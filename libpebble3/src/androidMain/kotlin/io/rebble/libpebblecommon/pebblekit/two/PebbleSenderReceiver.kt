package io.rebble.libpebblecommon.pebblekit.two

import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.LockerApi
import io.rebble.libpebblecommon.connection.Watches
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import io.rebble.libpebblecommon.di.LibPebbleKoinComponent
import io.rebble.libpebblecommon.disk.pbw.PbwApp
import io.rebble.libpebblecommon.js.RemoteTimelineEmulator
import io.rebble.libpebblecommon.js.TimelineLayoutJson
import io.rebble.libpebblecommon.js.TimelinePinJson
import io.rebble.libpebblecommon.locker.Locker
import io.rebble.libpebblecommon.locker.LockerPBWCache
import io.rebble.libpebblecommon.services.appmessage.AppMessageResult
import io.rebble.pebblekit2.common.model.PebbleDictionary
import io.rebble.pebblekit2.common.model.TimelinePin
import io.rebble.pebblekit2.common.model.TimelineResult
import io.rebble.pebblekit2.common.model.TransmissionResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import io.rebble.pebblekit2.server.BasePebbleSenderReceiver
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.collections.mapNotNull
import kotlin.collections.orEmpty
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration
import kotlin.time.toKotlinInstant
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

private val WATCH_SENDING_TIMEOUT = 10.seconds

class PebbleSenderReceiver : BasePebbleSenderReceiver(), LibPebbleKoinComponent {
    private val watchManager: Watches = getKoin().get<LibPebble>()
    private val locker: Locker = getKoin().get()
    private val lockerPBWCache: LockerPBWCache = getKoin().get()
    private val remoteTimelineEmulator: RemoteTimelineEmulator = getKoin().get()
    override val coroutineScope: LibPebbleCoroutineScope = getKoin().get()

    override suspend fun sendDataToPebble(
        callingPackage: String?,
        watchappUUID: UUID,
        data: PebbleDictionary,
        watches: List<WatchIdentifier>?
    ): Map<WatchIdentifier, TransmissionResult> {
        return runOnConnectedWatches(watches) { watch ->
            val companionApp = watch.currentCompanionAppSessions.value.filterIsInstance<PebbleKit2>().firstOrNull()

            if (companionApp == null || companionApp.uuid.toJavaUuid() != watchappUUID) {
                return@runOnConnectedWatches TransmissionResult.FailedDifferentAppOpen
            }

            if (callingPackage == null ||
                !companionApp.isAllowedToCommunicate(callingPackage)
            ) {
                return@runOnConnectedWatches TransmissionResult.FailedNoPermissions
            }

            companionApp.sendMessage(data)
        }
    }

    override suspend fun startAppOnTheWatch(
        watchappUUID: UUID,
        watches: List<WatchIdentifier>?
    ): Map<WatchIdentifier, TransmissionResult> {
        return runOnConnectedWatches(watches) {
            it.launchApp(watchappUUID.toKotlinUuid())
            TransmissionResult.Success
        }
    }

    override suspend fun stopAppOnTheWatch(
        watchappUUID: UUID,
        watches: List<WatchIdentifier>?
    ): Map<WatchIdentifier, TransmissionResult> {
        return runOnConnectedWatches(watches) {
            it.stopApp(watchappUUID.toKotlinUuid())
            TransmissionResult.Success
        }
    }

    override suspend fun insertTimelinePin(
        callingPackage: String?,
        watchappUUID: UUID,
        timelinePin: TimelinePin
    ): TimelineResult {
        if (!isAllowedToCommunicate(callingPackage, watchappUUID)) {
            return TimelineResult.FailedNoPermissions
        }

        remoteTimelineEmulator.insertPin(watchappUUID.toKotlinUuid(), timelinePin.toPinJson())
        return TimelineResult.Success
    }

    override suspend fun deleteTimelinePin(
        callingPackage: String?,
        watchappUUID: UUID,
        id: String
    ): TimelineResult {
        if (!isAllowedToCommunicate(callingPackage, watchappUUID)) {
            return TimelineResult.FailedNoPermissions
        }

        val success = remoteTimelineEmulator.deletePin(watchappUUID.toKotlinUuid(), id)
        return if (success) {
            TimelineResult.Success
        } else {
            TimelineResult.FailedUnknownPin
        }
    }

    private inline suspend fun runOnConnectedWatches(
        watches: List<WatchIdentifier>?,
        crossinline action: suspend (ConnectedPebbleDevice) -> TransmissionResult
    ): Map<WatchIdentifier, TransmissionResult> {
        val connectedWatches = watchManager.watches.value.filterIsInstance<ConnectedPebbleDevice>()

        val targetWatches = if (watches == null) {
            connectedWatches.map { WatchIdentifier(it.watchInfo.serial) }
        } else {
            watches
        }

        return coroutineScope {
            targetWatches.associateWith { targetWatchId ->
                async {
                    val watch = connectedWatches.firstOrNull { it.serial == targetWatchId.value }
                        ?: return@async TransmissionResult.FailedWatchNotConnected

                    try {
                        withTimeout(WATCH_SENDING_TIMEOUT) {
                            action(watch)
                        }
                    } catch (e: TimeoutCancellationException) {
                        TransmissionResult.FailedTimeout
                    }
                }
            }.mapValues { it.value.await() }
        }
    }

    private suspend fun isAllowedToCommunicate(pkg: String?, uuid: UUID): Boolean {
        if (pkg == null) {
            return false
        }

        val lockerEntry = locker.getLockerApp(uuid.toKotlinUuid()).firstOrNull() ?: return false
        val pbwInfo = PbwApp(
            lockerPBWCache.getPBWFileForApp(
                lockerEntry.properties.id,
                lockerEntry.properties.version.orEmpty(),
                locker
            )
        )

        return pbwInfo.info.companionApp?.android?.apps.orEmpty().any { it.pkg == pkg }
    }
}

private fun TimelinePin.toPinJson(): TimelinePinJson {
    return TimelinePinJson(
        id,
        startTime,
        duration?.inWholeMinutes?.toInt(),
        layout = TimelineLayoutJson(
            type  = layout.type.code,
            title = layout.title,
            subtitle = layout.subtitle,
            body = layout.body,
            tinyIcon = layout.tinyIcon,
            smallIcon = layout.smallIcon,
            largeIcon = layout.largeIcon,
            primaryColor = layout.primaryColor,
            secondaryColor = layout.secondaryColor,
            backgroundColor = layout.backgroundColor,
            headings = layout.headings,
            paragraphs = layout.paragraphs,
            lastUpdated = layout.lastUpdated,
        )
    )
}
