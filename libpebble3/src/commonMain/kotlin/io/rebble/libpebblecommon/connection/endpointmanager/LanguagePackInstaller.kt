package io.rebble.libpebblecommon.connection.endpointmanager

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.endpointmanager.putbytes.PutBytesSession
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.packets.ObjectType
import io.rebble.libpebblecommon.services.WatchInfo
import io.rebble.libpebblecommon.web.FirmwareDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
) : ConnectedPebble.LanguageInstall, LanguagePackInstaller {
    private val logger = Logger.withTag("LanguagePackInstaller")

    private val _state = MutableStateFlow<LanguagePackInstallState>(LanguagePackInstallState.Idle())
    override val state = _state.asStateFlow()

    override fun installLanguagePack(url: String, name: String) {
        installLanguagePackInternal(null, url, name)
    }

    override fun installLanguagePack(path: Path, name: String) {
        installLanguagePackInternal(path, null, name)
    }

    private fun installLanguagePackInternal(sideloadPath: Path?, url: String?, name: String) {
        if (_state.value !is LanguagePackInstallState.Idle) {
            return
        }
        val progressFlow = MutableStateFlow(0.0f)
        _state.value = LanguagePackInstallState.Downloading(name)
        connectionCoroutineScope.launch {
            val path = when {
                sideloadPath != null -> sideloadPath
                url != null -> {
                    val downloadedPath = firmwareDownloader.downloadFirmware(url, "pbl")
                    if (downloadedPath == null) {
                        _state.value = LanguagePackInstallState.Idle("Failed to download language pack")
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
                _state.value = LanguagePackInstallState.Idle("Failed to get metadata for $path")
                return@launch
            }
            _state.value = LanguagePackInstallState.Installing(progressFlow.asStateFlow(), name)
            val source = SystemFileSystem.source(path).buffered()
            try {
                val installFlow = putBytesSession.beginSession(
                    size = metadata.size.toUInt(),
                    type = ObjectType.FILE,
                    bank = 0u,
                    filename = "lang",
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
                logger.d { "installLanguagePack done" }
                _state.value = LanguagePackInstallState.Idle(successfullyInstalledLanguage = name)
            } catch (e: CancellationException) {
                _state.value = LanguagePackInstallState.Idle()
                throw e
            } catch (e: Exception) {
                logger.e(e) { "Error installing language pack" }
                _state.value = LanguagePackInstallState.Idle("Error installing language pack")
                return@launch
            } finally {
                source.close()
            }
        }
    }
}

sealed class LanguagePackInstallState {
    data class Idle(
        val successfullyInstalledLanguage: String? = null,
        val previousError: String? = null,
    ) : LanguagePackInstallState()
    data class Downloading(val language: String) : LanguagePackInstallState()
    data class Installing(val progress: StateFlow<Float>, val language: String) :
        LanguagePackInstallState()
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