package coredevices.coreapp.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coredevices.ui.M3Dialog
import coredevices.util.CoreConfigHolder
import coredevices.util.models.CactusSTTMode
import coredevices.util.models.ModelDownloadStatus
import coredevices.util.models.ModelInfo
import coredevices.util.models.ModelManager
import coredevices.util.transcription.PlatformSpeechRecognizer
import coredevices.util.transcription.SpeechModelAvailability
import coredevices.util.transcription.platformModelDialogMessage
import coredevices.util.transcription.platformModelNeedsDownload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import coreapp.composeapp.generated.resources.Res
import coreapp.composeapp.generated.resources.speech_download_downloaded
import coreapp.composeapp.generated.resources.speech_download_downloading
import coreapp.composeapp.generated.resources.speech_download_model
import coreapp.composeapp.generated.resources.speech_download_needed
import coreapp.composeapp.generated.resources.speech_download_not_needed
import coreapp.composeapp.generated.resources.speech_download_required
import coreapp.composeapp.generated.resources.speech_download_scheduled
import coreapp.composeapp.generated.resources.speech_download_with_size
import coreapp.util.generated.resources.Res as UtilRes
import coreapp.util.generated.resources.cancel
import coreapp.util.generated.resources.close
import org.jetbrains.compose.resources.stringResource

/**
 * Offers to download the on-device speech model the configured engine is missing. Reached via
 * [CommonRoutes.SpeechModelDownloadDialog], so the download-required notification can open it
 * regardless of which screen is showing.
 */
@Composable
fun SpeechModelDownloadDialog(onDismiss: () -> Unit) {
    val configHolder: CoreConfigHolder = koinInject()
    val config by configHolder.config.collectAsState()
    when {
        config.sttConfig.mode == CactusSTTMode.PlatformOnly ->
            PlatformModelDownloadDialog(config.sttConfig.spokenLanguage, onDismiss)
        config.sttConfig.mode.usesLocalCactus() ->
            CactusModelDownloadDialog(config.sttConfig.modelName, onDismiss)
        else -> ModelDownloadDialog(
            message = stringResource(Res.string.speech_download_not_needed),
            downloadLabel = null,
            progress = null,
            onDownload = {},
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun PlatformModelDownloadDialog(spokenLanguage: String?, onDismiss: () -> Unit) {
    val recognizer: PlatformSpeechRecognizer = koinInject()
    val download by recognizer.downloadStatus.collectAsState()
    val availability by produceState(SpeechModelAvailability.Unsupported, spokenLanguage, download) {
        value = withContext(Dispatchers.Default) { recognizer.modelAvailability(spokenLanguage) }
    }
    ModelDownloadDialog(
        message = platformModelDialogMessage(spokenLanguage, availability, download),
        downloadLabel = stringResource(Res.string.speech_download_model).takeIf { platformModelNeedsDownload(availability, download) },
        progress = (download as? ModelDownloadStatus.Downloading)?.progress,
        onDownload = { recognizer.downloadModel(spokenLanguage) },
        onDismiss = onDismiss,
    )
}

@Composable
private fun CactusModelDownloadDialog(modelName: String?, onDismiss: () -> Unit) {
    val modelManager: ModelManager = koinInject()
    val configHolder: CoreConfigHolder = koinInject()
    val scope = rememberCoroutineScope()
    val download by modelManager.modelDownloadStatus.collectAsState()
    val slug = modelName ?: modelManager.getRecommendedSTTModel().modelSlug
    val model by produceState<ModelInfo?>(null, slug) {
        value = withContext(Dispatchers.Default) {
            modelManager.getAvailableSTTModels().firstOrNull { it.slug == slug }
        }
    }
    val downloaded by produceState(false, slug, download) {
        value = withContext(Dispatchers.Default) { slug in modelManager.getDownloadedSTTModelSlugs() }
    }
    val downloading = download is ModelDownloadStatus.Downloading
    ModelDownloadDialog(
        message = when {
            download is ModelDownloadStatus.Scheduled -> {
                stringResource(Res.string.speech_download_scheduled)
            }
            downloading -> stringResource(Res.string.speech_download_downloading)
            downloaded -> stringResource(Res.string.speech_download_downloaded)
            else -> stringResource(Res.string.speech_download_needed)
        },
        downloadLabel = model?.let { stringResource(Res.string.speech_download_with_size, it.sizeInMB.toString()) }
            ?.takeUnless { downloading || downloaded || download is ModelDownloadStatus.Scheduled },
        progress = when (val d = download) {
            is ModelDownloadStatus.Downloading -> {
                d.progress
            }
            is ModelDownloadStatus.Scheduled -> {
                -1f
            }
            else -> null
        },
        onDownload = {
            val info = model ?: return@ModelDownloadDialog
            scope.launch {
                if (modelManager.downloadSTTModel(info, allowMetered = true) && modelName == null) {
                    val current = configHolder.config.value
                    configHolder.update(current.copy(sttConfig = current.sttConfig.copy(modelName = info.slug)))
                }
            }
        },
        onDismiss = onDismiss,
    )
}

/** [downloadLabel] null hides the download button, e.g. while a download is already in flight. */
@Composable
private fun ModelDownloadDialog(
    message: String,
    downloadLabel: String?,
    progress: Float?,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    M3Dialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.CloudDownload, contentDescription = null) },
        title = { Text(stringResource(Res.string.speech_download_required)) },
        buttons = {
            TextButton(onClick = onDismiss) { Text(if (downloadLabel == null) stringResource(UtilRes.string.close) else stringResource(UtilRes.string.cancel)) }
            if (downloadLabel != null) {
                TextButton(onClick = onDownload) { Text(downloadLabel) }
            }
        },
    ) {
        Text(message)
        progress?.let {
            Spacer(Modifier.height(24.dp))
            if (it < 0f) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
