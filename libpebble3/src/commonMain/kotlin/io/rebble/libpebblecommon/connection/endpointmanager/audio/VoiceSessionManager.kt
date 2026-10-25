package io.rebble.libpebblecommon.connection.endpointmanager.audio

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.SystemAppIDs
import io.rebble.libpebblecommon.WatchConfigFlow
import io.rebble.libpebblecommon.connection.endpointmanager.CompanionAppLifecycleManager
import io.rebble.libpebblecommon.di.ConnectionCoroutineScope
import io.rebble.libpebblecommon.packets.AudioStream
import io.rebble.libpebblecommon.packets.AudioStreamSetupResult
import io.rebble.libpebblecommon.packets.DictationResult
import io.rebble.libpebblecommon.packets.Result
import io.rebble.libpebblecommon.packets.Sentence
import io.rebble.libpebblecommon.packets.SessionSetupResult
import io.rebble.libpebblecommon.packets.SessionType
import io.rebble.libpebblecommon.packets.VoiceAttribute
import io.rebble.libpebblecommon.packets.VoiceAttributeType
import io.rebble.libpebblecommon.permissions.AppPermissions
import io.rebble.libpebblecommon.permissions.PermissionDecision
import io.rebble.libpebblecommon.plugin.PluginPermissions
import io.rebble.libpebblecommon.services.AudioStreamService
import io.rebble.libpebblecommon.services.VoiceService
import io.rebble.libpebblecommon.voice.TranscriptionProvider
import io.rebble.libpebblecommon.voice.TranscriptionResult
import io.rebble.libpebblecommon.voice.TranscriptionWord
import io.rebble.libpebblecommon.voice.toProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class VoiceSessionManager(
    private val voiceService: VoiceService,
    private val audioStreamService: AudioStreamService,
    private val watchScope: ConnectionCoroutineScope,
    private val transcriptionProvider: TranscriptionProvider,
    private val companionAppLifecycleManager: CompanionAppLifecycleManager,
    private val audioFrameDecoder: AudioFrameDecoder,
    private val permissions: AppPermissions,
    private val watchConfig: WatchConfigFlow,
) {
    companion object Companion {
        private val logger = Logger.withTag("VoiceSession")
    }
    private val _currentSession = MutableStateFlow<CurrentSession?>(null)
    val currentSession = _currentSession.asStateFlow()

    data class CurrentSession (
        val request: VoiceService.SessionSetupRequest,
        val result: CompletableDeferred<TranscriptionResult>
    )

    private fun makeSetupResult(
        sessionType: SessionType,
        result: Result,
        appInitiated: Boolean
    ): SessionSetupResult {
        val setupResult = SessionSetupResult(sessionType, result)
        if (appInitiated) {
            setupResult.flags.set(1u) // Indicates app-initiated session
        }
        return setupResult
    }

    private fun makeAudioStreamSetupResult(
        result: Result,
        sessionId: UShort,
        appInitiated: Boolean
    ): AudioStreamSetupResult {
        val setupResult = AudioStreamSetupResult(result, sessionId)
        if (appInitiated) {
            setupResult.flags.set(1u) // Indicates app-initiated session
        }
        return setupResult
    }

    private fun makeDictationResult(
        sessionId: UShort,
        result: Result,
        words: Iterable<TranscriptionWord>?,
        appUuid: Uuid
    ): DictationResult {
        return DictationResult(
            sessionId,
            result,
            buildList {
                words?.let {
                    add(VoiceAttribute(
                        id = VoiceAttributeType.Transcription.value,
                        content = VoiceAttribute.Transcription(
                            sentences = listOf(
                                Sentence(words.map { it.toProtocol() })
                            )
                        )
                    ))
                }
                if (appUuid != Uuid.NIL) {
                    add(VoiceAttribute(
                        id = VoiceAttributeType.AppUuid.value,
                        content = VoiceAttribute.AppUuid().apply {
                            uuid.set(appUuid)
                        }
                    ))
                }
            }
        ).apply {
            if (appUuid != Uuid.NIL) {
                flags.set(1u) // Indicates app-initiated session
            }
        }
    }

    /**
     * An app streaming its microphone to its companion app. Accept, deliver decoded PCM, and
     * hold the session until the watch stops the transfer.
     */
    private suspend fun handleAudioStream(
        setupRequest: VoiceService.SessionSetupRequest,
        appInitiated: Boolean,
    ) {
        val encoderInfo = setupRequest.encoderInfo
        val sessionId = setupRequest.sessionId.toUShort()
        val subject = permissions.subjectOf(setupRequest.appUuid)
        if (!watchConfig.value.enablePlugins) { // Only enable for apps if plugins are enabled, otherwise we would default-allow
            logger.w { "Audio stream from ${setupRequest.appUuid} refused: permissions not enabled" }
            voiceService.send(makeAudioStreamSetupResult(Result.FailDisabled, sessionId, appInitiated))
            return
        }
        if (subject == null || permissions.request(subject, PluginPermissions.WATCH_MICROPHONE) != PermissionDecision.Granted) {
            logger.w { "Audio stream from ${setupRequest.appUuid} refused: microphone permission not granted" }
            voiceService.send(makeAudioStreamSetupResult(Result.FailDisabled, sessionId, appInitiated))
            return
        }
        val app = companionAppLifecycleManager.currentCompanionAppSessions.value
            .filterIsInstance<AudioStreamTarget>().firstOrNull { it.uuid == setupRequest.appUuid }
        if (app == null) {
            logger.w { "Audio stream from ${setupRequest.appUuid} refused: no companion accepts audio" }
            voiceService.send(makeAudioStreamSetupResult(Result.FailServiceUnavailable, sessionId, appInitiated))
            return
        }
        val decoder = encoderInfo?.let { audioFrameDecoder.open(it) }
        if (encoderInfo == null || decoder == null) {
            logger.w { "Audio stream with unsupported encoder: $encoderInfo" }
            voiceService.send(makeAudioStreamSetupResult(Result.FailServiceUnavailable, sessionId, appInitiated))
            return
        }
        var accepted = false
        var completed = false
        try {
            val pcm = flow {
                audioStreamService.dataFlowForSession(sessionId).collect { transfer ->
                    for (frame in transfer.frames) {
                        decoder.decode(frame.data.get())?.let { emit(it) }
                    }
                }
            }
            completed = streamAudioWhileGranted(permissions.grantedKeys(subject, listOf(PluginPermissions.WATCH_MICROPHONE))) {
                app.streamAudio(encoderInfo.sampleRate.toInt(), pcm) {
                    voiceService.send(makeAudioStreamSetupResult(Result.Success, sessionId, appInitiated))
                    accepted = true
                    logger.i { "Audio stream $sessionId from ${setupRequest.appUuid} started" }
                }
            }
            if (!completed && !accepted) {
                logger.w { "Audio stream from ${setupRequest.appUuid} refused: companion not ready" }
                voiceService.send(makeAudioStreamSetupResult(Result.FailServiceUnavailable, sessionId, appInitiated))
            } else if (completed) {
                logger.i { "Audio stream $sessionId ended" }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Audio stream $sessionId failed" }
            if (!accepted) {
                voiceService.send(makeAudioStreamSetupResult(Result.FailServiceUnavailable, sessionId, appInitiated))
            }
        } finally {
            decoder.close()
            if (accepted && !completed) {
                withContext(NonCancellable) {
                    withTimeoutOrNull(1.seconds) {
                        audioStreamService.send(AudioStream.StopTransfer(sessionId))
                    }
                }
            }
        }
    }

    fun init() {
        watchScope.launch {
            voiceService.sessionSetupRequests.flowOn(Dispatchers.IO).collectLatest { setupRequest ->
                logger.i { "New voice session started: $setupRequest" }
                var audioFrameFlowCollected = false
                val audioFrameFlow = audioStreamService.dataFlowForSession(setupRequest.sessionId.toUShort())
                    .transform { transfer ->
                        transfer.frames
                            .map { frame -> frame.data.get() }
                            .forEach { emit(it) }
                    }
                    .onStart {
                        audioFrameFlowCollected = true
                    }
                val appInitiated = setupRequest.appUuid != Uuid.NIL
                if (setupRequest.sessionType == SessionType.AudioStream) {
                    handleAudioStream(setupRequest, appInitiated)
                    return@collectLatest
                }
                if (setupRequest.encoderInfo == null) {
                    logger.e { "Received voice session setup request without encoder info, cannot handle voice session." }
                    voiceService.send(makeSetupResult(
                        sessionType = setupRequest.sessionType,
                        result = Result.FailInvalidMessage,
                        appInitiated = appInitiated
                    ))
                    return@collectLatest
                }
                if (transcriptionProvider.canServeSession()) {
                    voiceService.send(makeSetupResult(
                        sessionType = setupRequest.sessionType,
                        result = Result.Success,
                        appInitiated = appInitiated
                    ))
                } else {
                    logger.w { "Voice session requested, but speech recognition is disabled or not available" }
                    voiceService.send(makeSetupResult(
                        sessionType = setupRequest.sessionType,
                        result = Result.FailDisabled,
                        appInitiated = appInitiated
                    ))
                    return@collectLatest
                }

                val resultCompletable = CompletableDeferred<TranscriptionResult>()
                _currentSession.value = CurrentSession(setupRequest, resultCompletable)
                logger.i { "Voice session initialized with ID: ${setupRequest.sessionId}" }
                val result = try {
                    transcriptionProvider.transcribe(
                        setupRequest.encoderInfo,
                        audioFrameFlow,
                        isNotificationReply = setupRequest.appUuid == Uuid.NIL || setupRequest.appUuid == SystemAppIDs.NOTIFICATIONS_APP_UUID
                    )
                } catch (e: CancellationException) {
                    logger.d { "Voice session cancelled" }
                    throw e
                } catch (e: Exception) {
                    logger.e(e) { "Error during transcription: ${e.message}" }
                    TranscriptionResult.Error("Transcription error: ${e.message}")
                }
                logger.i { "Voice session completed with result: ${
                    when (result) {
                        is TranscriptionResult.Success -> "Success, ${result.words.size} words"
                        is TranscriptionResult.Error -> "Error, ${result.message}"
                        is TranscriptionResult.Disabled -> "Disabled"
                        is TranscriptionResult.Failed -> "Failed"
                        is TranscriptionResult.ConnectionError -> "ConnectionError"
                    }
                }" }
                if (!audioFrameFlowCollected) {
                    logger.w { "Audio frames not collected, sending audio stop packet" }
                    audioStreamService.send(AudioStream.StopTransfer(setupRequest.sessionId.toUShort()))
                }
                voiceService.send(
                    makeDictationResult(
                        sessionId = setupRequest.sessionId.toUShort(),
                        result = result.toProtocol(),
                        words = (result as? TranscriptionResult.Success)?.words,
                        appUuid = setupRequest.appUuid
                    )
                )
                resultCompletable.complete(result)
                _currentSession.value = null
            }
        }
    }
}
