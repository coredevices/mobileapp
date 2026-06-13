package coredevices.pebble.services

import co.touchlab.kermit.Logger
import coredevices.pebble.account.BootConfigProvider
import coredevices.pebble.account.PebbleAccount
import coredevices.util.CoreConfigFlow
import io.rebble.libpebblecommon.voice.TranscriptionProvider
import io.rebble.libpebblecommon.voice.TranscriptionResult
import io.rebble.libpebblecommon.voice.VoiceEncoderInfo
import kotlinx.coroutines.flow.Flow

class RebbleAsrTranscription(
    private val rebbleAsrService: RebbleAsrService,
    private val bootConfigProvider: BootConfigProvider,
    private val pebbleAccount: PebbleAccount,
    private val coreConfigFlow: CoreConfigFlow,
) : TranscriptionProvider {
    companion object {
        private val logger = Logger.withTag("RebbleAsrTranscription")
    }

    override suspend fun canServeSession(): Boolean = isAvailable()

    suspend fun isAvailable(): Boolean {
        if (pebbleAccount.loggedIn.value == null) return false
        val voice = bootConfigProvider.getBootConfig()?.config?.voice ?: return false
        return voice.languages.isNotEmpty()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override suspend fun transcribe(
        encoderInfo: VoiceEncoderInfo,
        audioFrames: Flow<UByteArray>,
        isNotificationReply: Boolean
    ): TranscriptionResult {
        require(encoderInfo is VoiceEncoderInfo.Speex) {
            "Rebble ASR only supports Speex encoding, got ${encoderInfo::class.simpleName}"
        }

        if (pebbleAccount.loggedIn.value == null) {
            return TranscriptionResult.Error("Not signed in to Rebble")
        }
        val voice = bootConfigProvider.getBootConfig()?.config?.voice
        if (voice == null || voice.languages.isEmpty()) {
            return TranscriptionResult.Error("Rebble voice config unavailable")
        }
        val language = RebbleAsrService.pickLanguage(
            voice = voice,
            iso639_1Code = coreConfigFlow.value.sttConfig.spokenLanguage,
        ) ?: return TranscriptionResult.Error("No Rebble language endpoint available")

        val host = extractHost(language.endpoint)
        logger.d { "Rebble ASR -> host=$host locale=${language.fourCharLocale}" }
        return rebbleAsrService.transcribe(
            endpointHost = host,
            encoderInfo = encoderInfo,
            fourCharLocale = language.fourCharLocale,
            audioFrames = audioFrames,
        )
    }
}

private fun extractHost(endpoint: String): String =
    endpoint.removePrefix("https://").removePrefix("http://").substringBefore('/')
