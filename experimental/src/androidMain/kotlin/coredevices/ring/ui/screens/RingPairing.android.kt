package coredevices.ring.ui.screens

import CoreNav
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coreapp.util.generated.resources.Res
import coreapp.util.generated.resources.back
import coredevices.ring.service.RingEvent
import coredevices.ring.ui.viewmodel.RingPairingViewModel
import coredevices.util.rememberPlatformBondingListener
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
actual fun RingPairing(coreNav: CoreNav) {
    val snackbarHostState = remember { SnackbarHostState() }
    suspend fun showSnackbar(message: String) {
        snackbarHostState.showSnackbar(message)
    }
    val bondingEvents = rememberPlatformBondingListener()

    val viewModel =
        koinViewModel<RingPairingViewModel>(parameters = { parametersOf(::showSnackbar, bondingEvents) })
    val pairingState by viewModel.pairingState.collectAsState()
    val updateState by viewModel.updateStatus.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pair Ring") },
                navigationIcon = {
                    IconButton(onClick = coreNav::goBack) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(
                                Res.string.back
                            )
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            if (updateState !is RingEvent.FirmwareUpdate.Started) {
                when (val state = pairingState) {
                    is RingPairingViewModel.PairingState.Idle -> {
                        Button(
                            modifier = Modifier.size(150.dp, 50.dp),
                            onClick = {
                                viewModel.openPicker()
                            }
                        ) {
                            Text("Scan for Rings")
                        }
                    }

                    is RingPairingViewModel.PairingState.Pairing -> {
                        Text(
                            text = "Pairing with ring",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        if (state.extraDetail != null) {
                            Text(state.extraDetail, textAlign = TextAlign.Center)
                        } else {
                            Text(
                                "Press the button on your ring to confirm...",
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator()
                    }
                }
            } else {
                Text(
                    text = "Firmware Update in Progress",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your ring is being updated to the latest firmware. This may take a minute, please keep the ring close to this device and do not exit the app.",
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator()
            }
        }
    }
}