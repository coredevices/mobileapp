package io.rebble.libpebblecommon.connection.endpointmanager

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.ErrorTracker
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.PebbleProtocolHandler
import io.rebble.libpebblecommon.connection.UserFacingError
import io.rebble.libpebblecommon.connection.endpointmanager.putbytes.PutBytesSession
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.packets.MAX_LANGUAGE_PACKS
import io.rebble.libpebblecommon.packets.ObjectType
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.packets.SystemMessage
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.services.WatchInfo
import io.rebble.libpebblecommon.web.FirmwareDownloader
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.coroutines.cancellation.CancellationException

interface LanguagePackInstaller {
    val state: StateFlow<LanguagePackInstallState>
}

class RealLanguagePackInstaller(
    private val putBytesSession: PutBytesSession,
    private val connectionCoroutineScope: ConnectionCoroutineScope,
    private val firmwareDownloader: FirmwareDownloader,
    private val protocolHandler: PebbleProtocolHandler,
    private val errorTracker: ErrorTracker,
) : ConnectedPebble.LanguageInstall, LanguagePackInstaller {
    private val logger = Logger.withTag("LanguagePackInstaller")

    private val operations = Mutex()
    private var requestToken: UByte = 0u
    private var multiplePacks = false

    fun init(watchInfo: WatchInfo) {
        multiplePacks = ProtocolCapsFlag.SupportsMultipleLanguagePacks in watchInfo.capabilities
    }

    override suspend fun setActiveLanguage(pack: WatchLanguagePack?): Boolean = operations.withLock {
        if (_state.value !is LanguagePackInstallState.Idle) return@withLock false
        select(pack)
    }

    private suspend fun select(pack: WatchLanguagePack?): Boolean {
        require(pack == null || pack.hasTranslation) { "The pack has no translation" }
        val token = requestToken++
        val slot = pack?.slot?.toUByte() ?: 0xffu.toUByte()
        val response = request<SystemMessage.LanguageSelectResponse>(
            SystemMessage.LanguageSelectRequest(token, slot, pack?.version?.toUShort() ?: 0u, pack?.locale ?: "")
        ) { it.token.get() == token && it.slot.get() == slot }
        val selected = response.success.get() == 1u.toUByte()
        if (selected) {
            _state.value = LanguagePackInstallState.Idle(activeLanguage = pack?.name ?: "English")
        }
        return selected
    }

    override suspend fun listLanguagePacks(): List<WatchLanguagePack> = operations.withLock { list() }

    private suspend fun list(): List<WatchLanguagePack> {
        val token = requestToken++
        val response = request<SystemMessage.LanguagePacksResponse>(SystemMessage.LanguagePacksRequest(token)) {
            it.token.get() == token
        }
        return response.toWatchLanguagePacks()
    }

    override suspend fun removeLanguagePack(pack: WatchLanguagePack): Boolean = operations.withLock {
        if (_state.value !is LanguagePackInstallState.Idle) return@withLock false
        val token = requestToken++
        val response = request<SystemMessage.LanguagePackRemoveResponse>(
            SystemMessage.LanguagePackRemoveRequest(token, pack.slot.toUByte(), pack.version.toUShort(), pack.locale)
        ) { it.token.get() == token && it.slot.get().toInt() == pack.slot }
        val removed = response.success.get() == 1u.toUByte()
        if (removed && pack.isActive) {
            _state.value = LanguagePackInstallState.Idle(activeLanguage = "English")
        }
        removed
    }

    private suspend fun activateAddedPack(occupiedSlots: Set<Int>): Boolean = try {
        list().firstOrNull { it.slot !in occupiedSlots && it.hasTranslation }?.let { select(it) } == true
    } catch (e: Exception) {
        if (e is CancellationException && e !is TimeoutCancellationException) throw e
        logger.w(e) { "Could not activate the added language pack" }
        false
    }

    private suspend inline fun <reified T : PebblePacket> request(
        message: PebblePacket,
        crossinline matches: (T) -> Boolean,
    ): T = coroutineScope {
        val response = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { protocolHandler.inboundMessages.filterIsInstance<T>().first { matches(it) } }
        }
        try {
            protocolHandler.send(message)
            response.await()
        } finally {
            response.cancel()
        }
    }

    private val _state = MutableStateFlow<LanguagePackInstallState>(LanguagePackInstallState.Idle())
    override val state = _state.asStateFlow()

    override fun installLanguagePack(url: String, name: String, activate: Boolean) {
        installLanguagePackInternal(null, url, name, activate)
    }

    override fun installLanguagePack(path: Path, name: String, activate: Boolean) {
        installLanguagePackInternal(path, null, name, activate)
    }

    private fun installLanguagePackInternal(
        sideloadPath: Path?,
        url: String?,
        name: String,
        activate: Boolean,
    ) {
        if (_state.value !is LanguagePackInstallState.Idle) {
            return
        }
        val addToExisting = multiplePacks
        val previousLanguage = (_state.value as? LanguagePackInstallState.Idle)?.activeLanguage
        fun idle(error: String? = null): LanguagePackInstallState.Idle {
            error?.let { errorTracker.reportError(UserFacingError.FailedToInstallLanguagePack(it)) }
            return LanguagePackInstallState.Idle(activeLanguage = previousLanguage)
        }
        val progressFlow = MutableStateFlow(0.0f)
        _state.value = LanguagePackInstallState.Downloading(name)
        connectionCoroutineScope.launch {
            val path = when {
                sideloadPath != null -> sideloadPath
                url != null -> {
                    val downloadedPath = firmwareDownloader.downloadFirmware(url, "pbl")
                    if (downloadedPath == null) {
                        _state.value = idle("Failed to download language pack")
                        return@launch
                    }
                    downloadedPath
                }
                else -> throw IllegalArgumentException("Either sideloadPath or url must be provided")
            }
            logger.d { "installLanguagePack() $path" }
            val metadata = SystemFileSystem.metadataOrNull(path)
            if (metadata == null || metadata.size < 1) {
                logger.e { "Failed to get metadata for $path" }
                _state.value = idle("Failed to read language pack file")
                return@launch
            }
            _state.value = LanguagePackInstallState.Installing(progressFlow.asStateFlow(), name)
            val source = SystemFileSystem.source(path).buffered()
            try {
                val activated = operations.withLock {
                    val occupiedSlots = if (addToExisting) list().map { it.slot }.toSet() else emptySet()
                    if (occupiedSlots.size >= MAX_LANGUAGE_PACKS) {
                        _state.value = idle("All $MAX_LANGUAGE_PACKS language pack slots are full - remove one to add another")
                        return@launch
                    }
                    val installFlow = putBytesSession.beginSession(
                        size = metadata.size.toUInt(),
                        type = ObjectType.FILE,
                        bank = 0u,
                        filename = if (addToExisting) "lang-add" else "lang",
                        source = source,
                        sendInstall = true,
                    )
                    installFlow.collect { state ->
                        when (state) {
                            is PutBytesSession.SessionState.Finished -> logger.d { "installLanguagePack finished" }
                            is PutBytesSession.SessionState.Open -> logger.d { "installLanguagePack opened" }
                            is PutBytesSession.SessionState.Sending -> {
                                logger.d { "installLanguagePack sending: ${state.totalSent}" }
                                progressFlow.value = state.totalSent.toFloat() / metadata.size.toFloat()
                            }
                        }
                    }
                    addToExisting && activate && activateAddedPack(occupiedSlots)
                }
                logger.d { "installLanguagePack done" }
                _state.value = LanguagePackInstallState.Idle(
                    activeLanguage = if (addToExisting && !activated) previousLanguage else name,
                )
            } catch (e: CancellationException) {
                _state.value = idle()
                throw e
            } catch (e: Exception) {
                logger.e(e) { "Error installing language pack" }
                _state.value = idle(if (addToExisting) "Error adding language pack" else "Error installing language pack")
                return@launch
            } finally {
                source.close()
            }
        }
    }
}

sealed class LanguagePackInstallState {
    data class Idle(
        val activeLanguage: String? = null,
    ) : LanguagePackInstallState()
    data class Downloading(val language: String) : LanguagePackInstallState()
    data class Installing(val progress: StateFlow<Float>, val language: String) :
        LanguagePackInstallState()
}

fun LanguagePackInstallState.installing(): String? = when (this) {
    is LanguagePackInstallState.Downloading -> language
    is LanguagePackInstallState.Installing -> language
    else -> null
}

fun WatchInfo.installedLanguagePack(): InstalledLanguagePack? =
    if (language.isNotEmpty() && languageVersion > 0) {
        InstalledLanguagePack(
            isoLocal = language,
            version = languageVersion,
        )
    } else {
        null
    }

data class InstalledLanguagePack(
    val isoLocal: String,
    val version: Int,
)

data class WatchLanguagePack(
    val slot: Int,
    val locale: String,
    val name: String,
    val version: Int,
    val readable: Boolean,
    val hasTranslation: Boolean,
    val isActive: Boolean,
)

internal fun SystemMessage.LanguagePacksResponse.toWatchLanguagePacks(): List<WatchLanguagePack> =
    packs.filter { it.present }.map {
        val slot = it.slot.get().toInt()
        WatchLanguagePack(
            slot = slot,
            locale = it.locale.get(),
            name = it.name.get(),
            version = it.version.get().toInt(),
            readable = it.readable,
            hasTranslation = it.hasTranslation,
            isActive = activeSlot.get().toInt() == slot,
        )
    }
