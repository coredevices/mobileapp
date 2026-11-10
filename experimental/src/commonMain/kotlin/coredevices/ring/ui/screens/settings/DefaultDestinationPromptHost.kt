package coredevices.ring.ui.screens.settings

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import coredevices.ring.agent.integrations.providers.DefaultDestinationPrompter
import coredevices.ui.M3Dialog
import org.koin.compose.koinInject

/** Shows the default-destination question wherever the app is when a plugin's config page closes. */
@Composable
fun DefaultDestinationPromptHost(prompter: DefaultDestinationPrompter = koinInject()) {
    val prompt by prompter.prompt.collectAsState()
    val current = prompt ?: return
    M3Dialog(
        onDismissRequest = prompter::dismiss,
        title = { Text("Set default destination?") },
        buttons = {
            TextButton(onClick = prompter::dismiss) { Text("Not now") }
            TextButton(onClick = prompter::accept) { Text("Set default") }
        },
    ) {
        Text("Do you want to set ${current.provider.title} as your default destination for ${current.kindsLabel}?")
    }
}
