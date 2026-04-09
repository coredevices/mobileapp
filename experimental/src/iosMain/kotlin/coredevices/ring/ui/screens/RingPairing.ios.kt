package coredevices.ring.ui.screens

import CoreNav
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.window.Dialog
import com.juul.kable.Advertisement
import coreapp.util.generated.resources.Res
import coreapp.util.generated.resources.back
import coredevices.ring.service.RingEvent
import coredevices.ring.ui.viewmodel.IosRingPairingViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
actual fun RingPairing(coreNav: CoreNav) {
    val snackbarHostState = remember { SnackbarHostState() }
    suspend fun showSnackbar(message: String) {
        snackbarHostState.showSnackbar(message)
    }
    val viewModel: IosRingPairingViewModel = koinViewModel { parametersOf(::showSnackbar) }
    val pairingState by viewModel.pairingState.collectAsState()
    val updateState by viewModel.updateStatus.collectAsState()
    val dialoguePotentialAdv by viewModel.pairingDialogueAdv.collectAsState()

    dialoguePotentialAdv?.let {
        PairingDialogue(
            advertisement = it,
            onResult = { accepted ->
                viewModel.closePairingDialogue(accepted)
            }
        )
    }

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
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(innerPadding)
                .then(Modifier.padding(horizontal = 16.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (updateState !is RingEvent.FirmwareUpdate.Started) {
                when (pairingState) {
                    IosRingPairingViewModel.PairingState.WaitingForBluetooth -> {
                        Text(
                            text = "Waiting for Bluetooth",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Please enable Bluetooth to pair a ring")
                    }
                    IosRingPairingViewModel.PairingState.Scanning -> {
                        Text(
                            text = "Scanning for devices...",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Hold your ring close to this device and press its button",
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator()
                    }
                    is IosRingPairingViewModel.PairingState.WaitingForPairing -> {
                        val pairingState = pairingState as IosRingPairingViewModel.PairingState.WaitingForPairing
                        Text(
                            text = "Pairing in progress...",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            pairingState.extraDetail
                                ?: "Please press the button on your ring after confirming on this device to complete pairing."
                        )
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator()
                    }
                    IosRingPairingViewModel.PairingState.Paired -> {
                        Text(
                            text = "Paired",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Currently paired to a ring")
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.startScanning()
                            }
                        ) {
                            Text("Pair to a different device")
                        }
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

@Composable
private fun PairingDialogue(
    advertisement: Advertisement,
    onResult: (Boolean) -> Unit
) {
    Dialog(
        onDismissRequest = { onResult(false) }
    ) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
            ) {
                Text(
                    text = "Ring Detected",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(16.dp))
                Text("Found a ring '${advertisement.name}'\n" +
                        "Would you like to pair with this device?")
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { onResult(true) }) {
                        Text("Pair")
                    }
                    OutlinedButton(onClick = { onResult(false) }) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}