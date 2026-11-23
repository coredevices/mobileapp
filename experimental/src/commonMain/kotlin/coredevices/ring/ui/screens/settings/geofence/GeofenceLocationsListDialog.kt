package coredevices.ring.ui.screens.settings.geofence

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.settings_geofence_none
import coreapp.ring.generated.resources.settings_geofence_saved_places
import coreapp.util.generated.resources.add
import coreapp.util.generated.resources.done
import coredevices.ring.ui.viewmodel.IndexActionsViewModel
import coredevices.ui.M3Dialog
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid
import coreapp.util.generated.resources.Res as UtilRes

@Composable
fun GeofenceLocationsListDialog(
    viewModel: IndexActionsViewModel = koinViewModel(),
    onAdd: () -> Unit,
    onEdit: (uuid: Uuid) -> Unit,
    onDismissRequest: () -> Unit
) {
    val locations = viewModel.geofenceLocations.collectAsState()
    val emptyLocations by derivedStateOf { locations.value.isEmpty() }
    M3Dialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Res.string.settings_geofence_saved_places)) },
        buttons = {
            TextButton(onClick = onAdd) {
                Text(stringResource(UtilRes.string.add))
            }
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(UtilRes.string.done))
            }
        },
    ) {
        if (emptyLocations) {
            Text(
                stringResource(Res.string.settings_geofence_none),
                modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth()
            )
        } else {
            GeofenceLocationsList(
                modifier = Modifier.fillMaxWidth(),
                viewModel = viewModel,
                onEdit = { uuid ->
                    onEdit(uuid)
                }
            )
        }
    }
}