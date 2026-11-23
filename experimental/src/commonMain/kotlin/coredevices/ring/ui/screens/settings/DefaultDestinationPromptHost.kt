package coredevices.ring.ui.screens.settings

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.settings_default_destination_message
import coreapp.ring.generated.resources.settings_default_destination_not_now
import coreapp.ring.generated.resources.settings_default_destination_set
import coreapp.ring.generated.resources.settings_default_destination_title
import coredevices.ring.agent.integrations.providers.DefaultDestinationPrompter
import coredevices.ui.M3Dialog
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Shows the default-destination question wherever the app is when a plugin's config page closes. */
@Composable
fun DefaultDestinationPromptHost(prompter: DefaultDestinationPrompter = koinInject()) {
    val prompt by prompter.prompt.collectAsState()
    val current = prompt ?: return
    M3Dialog(
        onDismissRequest = prompter::dismiss,
        title = { Text(stringResource(Res.string.settings_default_destination_title)) },
        buttons = {
            TextButton(onClick = prompter::dismiss) { Text(stringResource(Res.string.settings_default_destination_not_now)) }
            TextButton(onClick = prompter::accept) { Text(stringResource(Res.string.settings_default_destination_set)) }
        },
    ) {
        Text(stringResource(Res.string.settings_default_destination_message, current.provider.title, current.kindsLabel))
    }
}
