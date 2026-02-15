package coredevices.ui

import androidx.compose.animation.animateContentSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import co.touchlab.kermit.Logger
import com.cactus.CactusLM
import com.cactus.CactusSTT
import com.cactus.CactusInitParams
import coredevices.util.deleteRecursive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path

sealed interface ModelType {
    val modelName: String
    data class STT(override val modelName: String) : ModelType
    data class Agent(override val modelName: String) : ModelType
}

@Composable
fun ModelDownloadDialog(
    onDismissRequest: (success: Boolean) -> Unit,
    models: Set<ModelType>
) {
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }

    fun onCancel() {
        job?.cancel()
        onDismissRequest(false)
    }

    fun onDownload() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            try {
                models.forEach { model ->
                    val modelName = model.modelName
                    when (model) {
                        is ModelType.STT -> {
                            val stt = CactusSTT()
                            try {
                                // Download the model (throws exception on failure)
                                stt.downloadModel(modelName)

                                // Initialize the model (throws exception on failure)
                                stt.initializeModel(CactusInitParams(model = modelName))
                            } catch (e: Exception) {
                                // Retry after wiping model directories
                                Logger.e("ModelDownloadDialog", e) { "Error downloading STT model, retrying after wiping models" }
                                try {

                                    stt.downloadModel(modelName)
                                    stt.initializeModel(CactusInitParams(model = modelName))
                                } catch (e: Exception) {
                                    Logger.e("ModelDownloadDialog", e) { "Error downloading STT model after retry" }
                                    onDismissRequest(false)
                                    return@launch
                                }
                            }
                        }
                        is ModelType.Agent -> {
                            CactusLM().downloadModel(modelName)
                        }
                    }
                }
                onDismissRequest(true)
            } catch (e: Exception) {
                //TODO: Handle error
                Logger.e("ModelDownloadDialog", e) { "Error downloading models" }
                onDismissRequest(false)
                return@launch
            }
        }
    }
    M3Dialog(
        modifier = Modifier.animateContentSize(),
        properties = DialogProperties(
            dismissOnBackPress = job == null,
            dismissOnClickOutside = job == null
        ),
        onDismissRequest = ::onCancel,
        icon = {
            Icon(Icons.Outlined.CloudDownload, contentDescription = null)
        },
        title = {
            if (job == null) {
                Text("Download Required")
            } else {
                Text("Downloading Models")
            }
        },
        buttons = if (job == null) {
            {
                TextButton(
                    onClick = ::onCancel
                ) {
                    Text("Cancel")
                }
                TextButton(
                    onClick = ::onDownload
                ) {
                    Text("Download")
                }
            }
        } else {
            {
                TextButton(
                    onClick = ::onCancel
                ) {
                    Text("Cancel")
                }
            }
        }
    ) {
        if (job == null) {
            Text("This feature requires downloading additional machine learning models. " +
                    "Please avoid downloading over a metered connection.")
        } else {
            Text("This might take a few minutes on a slow connection...")
            Spacer(Modifier.height(24.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}