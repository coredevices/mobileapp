package coredevices.ring.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coredevices.ring.ui.viewmodel.ListenDialogViewModel
import coredevices.ring.ui.viewmodel.ListenDialogViewModel.ManualTranscriptionState
import coredevices.util.Platform
import coredevices.util.isIOS
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun ListenDialog(onDismissRequest: () -> Unit) {
    val listenDialogViewModel = koinInject<ListenDialogViewModel> { parametersOf(onDismissRequest) }
    val state by listenDialogViewModel.manualTranscriptionState.collectAsState()
    val platform = koinInject<Platform>()
    val isIOS = remember { platform.isIOS }
    LaunchedEffect(Unit) {
        listenDialogViewModel.beginManualRecording()
    }
    Dialog(onDismissRequest = {
        listenDialogViewModel.cancelManualRecording()
    }) {
        Surface(Modifier.width(300.dp).height(200.dp), color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (val it = state) {
                    is ManualTranscriptionState.Recording -> {
                        Text("Recording", style = MaterialTheme.typography.titleLarge)
                        if (it.partial != null) {
                            Text(it.partial)
                        }
                        if (isIOS) {
                            Button(
                                onClick = {
                                    listenDialogViewModel.completeManualRecording()
                                }
                            ) {
                                Text("Finish")
                            }
                        }
                    }
                    ManualTranscriptionState.Processing -> {
                        Text("Processing", style = MaterialTheme.typography.titleLarge)
                    }
                    is ManualTranscriptionState.Error -> {
                        Text("Error", style = MaterialTheme.typography.titleLarge)
                        Text(it.message)
                    }
                    else -> {
                        LinearProgressIndicator()
                    }
                }
            }
        }
    }
}