package coredevices.ring.ui.screens.settings

import CommonRoutes
import CoreNav
import NextBugReportContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Troubleshoot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coredevices.ring.ui.viewmodel.DiagnosticsState
import coredevices.ring.ui.viewmodel.SettingsViewModel
import coredevices.ui.M3Dialog
import kotlinx.coroutines.Job
import org.koin.compose.koinInject

@Composable
fun DiagnosticsDialog(
    coreNav: CoreNav,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    val state = viewModel.diagnosticsState.collectAsState().value
    val nextBugReportContext = koinInject<NextBugReportContext>()
    var job by remember { mutableStateOf<Job?>(null) }

    fun dismiss() {
        job?.cancel()
        onDismiss()
    }

    M3Dialog(
        properties = DialogProperties(
            dismissOnClickOutside = false
        ),
        onDismissRequest = ::dismiss,
        icon = { Icon(Icons.Default.Troubleshoot, contentDescription = null) },
        title = { Text("Index 01 Diagnostics") },
        buttons = {
            when (state) {
                DiagnosticsState.Idle -> {
                    TextButton(onClick = ::dismiss) { Text("Cancel") }
                    TextButton(onClick = { job = viewModel.beginDiagnostics() }) { Text("Start") }
                }
                DiagnosticsState.Running -> {}
                is DiagnosticsState.Error -> {
                    TextButton(onClick = ::dismiss) { Text("Close") }
                    TextButton(onClick = { job = viewModel.beginDiagnostics() }) { Text("Try Again") }
                }
                is DiagnosticsState.Completed -> {
                    TextButton(onClick = ::dismiss) { Text("Done") }
                    TextButton(onClick = {
                        nextBugReportContext.nextContext = buildString {
                            appendLine()
                            append("Ring RSSI Diagnostics")
                            append("\n======================")
                            state.measurement.let {
                                append("\nPhone RSSI: ${it.phoneRSSI}")
                                append("\nRing RX RSSI: ${it.deviceRSSI}")
                            }
                            appendLine()
                        }
                        dismiss()
                        coreNav.navigateTo(CommonRoutes.BugReport(pebble = false))
                    }) { Text("File Bug Report") }
                }
            }
        }
    ) {
        when (state) {
            DiagnosticsState.Idle -> Text(
                buildAnnotatedString {
                    append("This checks the Bluetooth connection between your phone and Index 01. ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("Place the device right next to your phone, ")
                    }
                    append("then tap Start.")
                },
            )
            DiagnosticsState.Running -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Keep your Index 01 next to your phone and click the button...",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            }
            is DiagnosticsState.Error -> Text(
                "An error occurred: ${state.message}\nPlease try again ensuring you've clicked the button to wake up the device"
            )
            is DiagnosticsState.Completed -> Text(
                "Diagnostics complete. If support asked you to run this, file a bug report to send them the results."
            )
        }
    }
}
