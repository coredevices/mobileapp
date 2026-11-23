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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.settings_diagnostics_completed
import coreapp.ring.generated.resources.settings_diagnostics_dialog_title
import coreapp.ring.generated.resources.settings_diagnostics_error
import coreapp.ring.generated.resources.settings_diagnostics_file_bug_report
import coreapp.ring.generated.resources.settings_diagnostics_intro
import coreapp.ring.generated.resources.settings_diagnostics_intro_bold
import coreapp.ring.generated.resources.settings_diagnostics_running
import coreapp.ring.generated.resources.settings_diagnostics_start
import coreapp.ring.generated.resources.settings_diagnostics_try_again
import coreapp.util.generated.resources.cancel
import coreapp.util.generated.resources.close
import coreapp.util.generated.resources.done
import coredevices.ring.ui.viewmodel.DiagnosticsState
import coredevices.ring.ui.viewmodel.SettingsViewModel
import coredevices.ui.M3Dialog
import kotlinx.coroutines.Job
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import coreapp.util.generated.resources.Res as UtilRes

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
        title = { Text(stringResource(Res.string.settings_diagnostics_dialog_title)) },
        buttons = {
            when (state) {
                DiagnosticsState.Idle -> {
                    TextButton(onClick = ::dismiss) { Text(stringResource(UtilRes.string.cancel)) }
                    TextButton(onClick = { job = viewModel.beginDiagnostics() }) { Text(stringResource(Res.string.settings_diagnostics_start)) }
                }
                DiagnosticsState.Running -> {}
                is DiagnosticsState.Error -> {
                    TextButton(onClick = ::dismiss) { Text(stringResource(UtilRes.string.close)) }
                    TextButton(onClick = { job = viewModel.beginDiagnostics() }) { Text(stringResource(Res.string.settings_diagnostics_try_again)) }
                }
                is DiagnosticsState.Completed -> {
                    TextButton(onClick = ::dismiss) { Text(stringResource(UtilRes.string.done)) }
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
                    }) { Text(stringResource(Res.string.settings_diagnostics_file_bug_report)) }
                }
            }
        }
    ) {
        when (state) {
            DiagnosticsState.Idle -> Text(
                boldPlaceholder(
                    Res.string.settings_diagnostics_intro,
                    stringResource(Res.string.settings_diagnostics_intro_bold),
                ),
            )
            DiagnosticsState.Running -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(Res.string.settings_diagnostics_running),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            }
            is DiagnosticsState.Error -> Text(stringResource(Res.string.settings_diagnostics_error, state.message))
            is DiagnosticsState.Completed -> Text(stringResource(Res.string.settings_diagnostics_completed))
        }
    }
}
