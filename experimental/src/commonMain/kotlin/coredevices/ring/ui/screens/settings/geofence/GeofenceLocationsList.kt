package coredevices.ring.ui.screens.settings.geofence

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import coredevices.ring.data.entity.room.SavedGeofenceLocation
import coredevices.ring.ui.viewmodel.IndexActionsViewModel
import org.koin.compose.viewmodel.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun GeofenceLocationsList(
    modifier: Modifier = Modifier,
    viewModel: IndexActionsViewModel = koinViewModel(),
    onEdit: (uuid: Uuid) -> Unit,
) {
    val locations by viewModel.geofenceLocations.collectAsState()
    LazyColumn(modifier) {
        items(locations.size) { index ->
            val location = locations[index]
            GeofenceLocationRow(
                location = location,
                onEdit = {
                    onEdit(location.id)
                }
            )
        }
    }
}

@Composable
private fun GeofenceLocationRow(
    location: SavedGeofenceLocation,
    onEdit: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable {
            onEdit()
        },
        headlineContent = {
            Text(location.name)
        },
        supportingContent = {
            Text(location.cachedAddress ?: "${location.lat}, ${location.long} (${location.radiusMeters}m radius)")
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    )
}