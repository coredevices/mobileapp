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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.russhwolf.settings.Settings
import coredevices.coreapp.STT_MODE_BEFORE_UPDATE_KEY
import coredevices.ui.M3Dialog
import coredevices.util.CommonBuildKonfig
import coredevices.util.CoreConfigHolder
import coredevices.util.models.CactusSTTMode
import coredevices.util.models.ModelDownloadStatus
import coredevices.util.models.ModelManager
import coredevices.util.transcription.CactusModelPathProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun SttModelUpdatePrompt() {
    val modelProvider: CactusModelPathProvider = koinInject()
    val modelManager: ModelManager = koinInject()
    val configHolder: CoreConfigHolder = koinInject()
    val settings: Settings = koinInject()
    val scope = rememberCoroutineScope()
    val sttModel = CommonBuildKonfig.CACTUS_STT_MODEL

    var needsUpdate by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val downloadStatus by modelManager.modelDownloadStatus.collectAsState()

    LaunchedEffect(Unit) {
        needsUpdate = withContext(Dispatchers.IO) { sttModel in modelProvider.getIncompatibleModels() }
    }

    LaunchedEffect(downloadStatus) {
        if (!downloading) return@LaunchedEffect
        when (downloadStatus) {
            is ModelDownloadStatus.Idle -> {
                val stillStale = withContext(Dispatchers.IO) { sttModel in modelProvider.getIncompatibleModels() }
                if (stillStale) {
                    failed = true
                } else {
                    val restored = CactusSTTMode.fromId(
                        settings.getInt(STT_MODE_BEFORE_UPDATE_KEY, CactusSTTMode.RemoteOnly.id)
                    )
                    configHolder.update(
                        configHolder.config.value.copy(
                            sttConfig = configHolder.config.value.sttConfig.copy(
                                mode = restored,
                                modelName = sttModel,
                            )
                        )
                    )
                    settings.remove(STT_MODE_BEFORE_UPDATE_KEY)
                    needsUpdate = false
                }
                downloading = false
            }
            is ModelDownloadStatus.Failed -> {
                failed = true
                downloading = false
            }
            else -> {}
        }
    }

    if (!needsUpdate) return

    fun startDownload() {
        failed = false
        scope.launch {
            val info = modelManager.getAvailableSTTModels().firstOrNull { it.slug == sttModel }
            if (info != null && modelManager.downloadSTTModel(info, allowMetered = true)) {
                downloading = true
            } else {
                failed = true
            }
        }
    }

    M3Dialog(
        onDismissRequest = { if (!downloading) needsUpdate = false },
        properties = DialogProperties(
            dismissOnBackPress = !downloading,
            dismissOnClickOutside = !downloading,
        ),
        icon = { Icon(Icons.Outlined.CloudDownload, contentDescription = null) },
        title = {
            Text(
                when {
                    downloading -> "Updating voice model"
                    failed -> "Update failed"
                    else -> "Voice model update"
                }
            )
        },
        buttons = {
            if (downloading) {
                TextButton(onClick = {
                    modelManager.cancelDownload()
                    downloading = false
                }) { Text("Cancel") }
            } else {
                TextButton(onClick = { needsUpdate = false }) { Text("Later") }
                TextButton(onClick = { startDownload() }) { Text(if (failed) "Retry" else "Update") }
            }
        },
    ) {
        when {
            downloading -> {
                Text("Downloading the updated model. This may take a few minutes.")
                Spacer(Modifier.height(24.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            failed -> Text("The download didn't finish. Check your connection and try again.")
            else -> Text("We've improved on-device voice recognition. Update the model to keep transcribing offline.")
        }
    }
}
