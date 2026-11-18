package coredevices.ring.ui.screens.settings.geofence

import BugReportButton
import CoreNav
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowColumn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.swmansion.kmpmaps.core.CameraPosition
import com.swmansion.kmpmaps.core.Circle
import com.swmansion.kmpmaps.core.Coordinates
import com.swmansion.kmpmaps.core.Map
import com.swmansion.kmpmaps.core.MapProperties
import com.swmansion.kmpmaps.core.MapType
import com.swmansion.kmpmaps.core.MapUISettings
import com.swmansion.kmpmaps.core.Marker
import coreapp.util.generated.resources.Res
import coreapp.util.generated.resources.back
import coredevices.ring.data.entity.room.SavedGeofenceLocation
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ring.ui.viewmodel.IndexActionsViewModel
import coredevices.ui.M3Dialog
import dev.jordond.compass.geocoder.Geocoder
import io.rebble.libpebblecommon.util.GeolocationPositionResult
import io.rebble.libpebblecommon.util.SystemGeolocation
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

private val RADIUS_RANGE_METERS = 5f..100f
private const val MAP_ZOOM = 18f
private val WIDE_LAYOUT_MIN_WIDTH = 600.dp
private val MAP_HEIGHT = 300.dp
private val FAB_CLEARANCE = 144.dp

/** A place being edited; [coords] stay unset on a new place until an address is found. */
private data class PlaceDraft(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val coords: Coordinates? = null,
    val radiusMeters: Float = RADIUS_RANGE_METERS.endInclusive,
    val address: String? = null,
) {
    fun toEntity(): SavedGeofenceLocation? = coords?.takeIf { name.isNotBlank() }?.let {
        SavedGeofenceLocation(
            id = id,
            name = name.trim(),
            lat = it.latitude,
            long = it.longitude,
            radiusMeters = radiusMeters,
            cachedAddress = address,
        )
    }
}

private fun SavedGeofenceLocation.toDraft() = PlaceDraft(
    id = id,
    name = name,
    coords = Coordinates(lat, long),
    radiusMeters = radiusMeters,
    address = cachedAddress,
)

/** Edits the saved place [locationId], or creates a new one when it is null. */
@Composable
fun GeofenceLocationEditScreen(
    coreNav: CoreNav,
    locationId: Uuid?,
) {
    val viewModel = koinViewModel<IndexActionsViewModel>()
    val geocoder: Geocoder = koinInject()
    val geolocation: SystemGeolocation = koinInject()
    val searchAvailable = remember { geocoder.isAvailable() }
    val scope = rememberCoroutineScope()
    val colors = IndexTheme.colors
    val density = LocalDensity.current
    val locations by viewModel.geofenceLocations.collectAsState()
    val saved = locations.firstOrNull { it.id == locationId }
    var draft by remember { mutableStateOf(if (locationId == null) PlaceDraft() else null) }
    // Tapping the map moves the pin without recentring; loading and searching do both.
    var cameraTarget by remember { mutableStateOf<Coordinates?>(null) }
    var addressQuery by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    // Non-null while the delete confirmation is showing: how many reminders use this place.
    var deleteReminderCount by remember { mutableStateOf<Int?>(null) }
    var searchFailed by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (draft == null && saved != null) {
            draft = saved.toDraft()
            cameraTarget = draft?.coords
            addressQuery = saved.cachedAddress.orEmpty()
        }
    }

    LaunchedEffect(Unit) {
        if (locationId != null) return@LaunchedEffect
        val position = geolocation.getCurrentPosition(maximumAge = 5.minutes)
        if (position is GeolocationPositionResult.Success && cameraTarget == null && draft?.coords == null) {
            cameraTarget = Coordinates(position.latitude, position.longitude)
        }
    }

    fun searchAddress() {
        val query = addressQuery.trim()
        if (query.isEmpty() || searching) return
        scope.launch {
            searching = true
            val found = geocoder.forward(query).getFirstOrNull()
            searching = false
            searchFailed = found == null
            if (found != null) {
                val coords = Coordinates(found.latitude, found.longitude)
                draft = draft?.copy(coords = coords, address = query)
                cameraTarget = coords
            }
        }
    }

    val reminderCount = deleteReminderCount
    if (reminderCount != null && saved != null) {
        fun delete(deleteReminders: Boolean) {
            deleteReminderCount = null
            scope.launch {
                viewModel.deleteGeofenceLocation(saved, deleteReminders).await()
                coreNav.goBack()
            }
        }
        if (reminderCount == 0) {
            M3Dialog(
                onDismissRequest = { deleteReminderCount = null },
                title = { Text("Delete Saved Place") },
                buttons = {
                    TextButton(onClick = { deleteReminderCount = null }) { Text("Cancel") }
                    TextButton(onClick = { delete(deleteReminders = false) }) { Text("Delete") }
                },
            ) {
                Text("Delete “${saved.name}”?")
            }
        } else {
            val reminders = if (reminderCount == 1) "1 reminder" else "$reminderCount reminders"
            M3Dialog(
                onDismissRequest = { deleteReminderCount = null },
                title = { Text("Delete Saved Place") },
                verticalButtons = {
                    TextButton(
                        onClick = { delete(deleteReminders = true) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Delete reminders") }
                    TextButton(onClick = { delete(deleteReminders = false) }) { Text("Remove association") }
                    TextButton(onClick = { deleteReminderCount = null }) { Text("Cancel") }
                },
            ) {
                Text(
                    "“${saved.name}” is used by $reminders. You can either delete these reminders or " +
                        "remove their place association."
                )
            }
        }
    }

    val current = draft
    val entity = current?.toEntity()
    Scaffold(
        containerColor = colors.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = colors.primary,
                    navigationIconContentColor = colors.onSurface,
                    actionIconContentColor = colors.onSurface,
                ),
                navigationIcon = {
                    IconButton(onClick = coreNav::goBack) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(Res.string.back)
                        )
                    }
                },
                title = { Text(if (locationId == null) "Add Saved Place" else "Edit Saved Place") },
                actions = {
                    BugReportButton(
                        coreNav,
                        pebble = false,
                        screenContext = mapOf(
                            "screen" to "GeofenceLocationEditScreen"
                        )
                    )
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (saved != null) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { deleteReminderCount = viewModel.geofenceReminderCount(saved) } },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
                if (entity != null) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            scope.launch {
                                viewModel.updateGeofenceLocation(entity).await()
                                coreNav.goBack()
                            }
                        },
                        icon = { Icon(Icons.Default.Check, contentDescription = null) },
                        text = { Text("Save") },
                    )
                }
            }
        }
    ) { innerPadding ->
        if (current == null) return@Scaffold
        val coords = current.coords
        BoxWithConstraints(Modifier.fillMaxSize().padding(innerPadding)) {
            // Settings wrap into a second column on wide screens. Panes are sized in whole pixels:
            // a rounding overshoot would wrap or clip the settings.
            val wide = maxWidth >= WIDE_LAYOUT_MIN_WIDTH
            val widthPx = constraints.maxWidth
            val heightPx = constraints.maxHeight
            val mapWidthPx = if (wide) widthPx / 2 else widthPx
            val settingsWidthPx = if (wide) widthPx - mapWidthPx else widthPx
            val mapHeightPx = if (wide) heightPx else with(density) { minOf(MAP_HEIGHT.roundToPx(), heightPx / 2) }
            val settingsHeightPx = if (wide) heightPx else heightPx - mapHeightPx
            FlowColumn(modifier = Modifier.fillMaxSize()) {
                Map(
                    modifier = with(density) { Modifier.size(mapWidthPx.toDp(), mapHeightPx.toDp()) },
                    cameraPosition = remember(cameraTarget) { cameraTarget?.let { CameraPosition(it, MAP_ZOOM) } },
                    properties = MapProperties(
                        isMyLocationEnabled = true,
                        mapType = MapType.NORMAL,
                    ),
                    uiSettings = MapUISettings(
                        myLocationButtonEnabled = true,
                        compassEnabled = true
                    ),
                    markers = listOfNotNull(coords?.let { Marker(coordinates = it, title = null) }),
                    circles = listOfNotNull(coords?.let { Circle(center = it, radius = current.radiusMeters) }),
                    onMapClick = {
                        draft = current.copy(coords = it, address = null)
                        addressQuery = ""
                        searchFailed = false
                    },
                )
                Column(
                    modifier = Modifier
                        .then(with(density) { Modifier.size(settingsWidthPx.toDp(), settingsHeightPx.toDp()) })
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = FAB_CLEARANCE),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = current.name,
                        onValueChange = { draft = current.copy(name = it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Name") },
                        isError = current.name.isBlank(),
                        singleLine = true,
                    )
                    if (searchAvailable) {
                        OutlinedTextField(
                            value = addressQuery,
                            onValueChange = {
                                addressQuery = it
                                searchFailed = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Address") },
                            placeholder = { Text("Search for an address") },
                            trailingIcon = {
                                if (searching) {
                                    CircularProgressIndicator(Modifier.padding(12.dp), strokeWidth = 2.dp)
                                } else {
                                    IconButton(onClick = ::searchAddress) {
                                        Icon(Icons.Default.Search, contentDescription = "Search")
                                    }
                                }
                            },
                            isError = searchFailed,
                            supportingText = if (searchFailed) {
                                { Text("Couldn't find that address.") }
                            } else null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { searchAddress() }),
                        )
                    }
                    Column {
                        Text(
                            "Radius: ${current.radiusMeters.roundToInt()} m",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface,
                        )
                        Slider(
                            value = current.radiusMeters.coerceIn(RADIUS_RANGE_METERS),
                            onValueChange = { draft = current.copy(radiusMeters = it.roundToInt().toFloat()) },
                            valueRange = RADIUS_RANGE_METERS,
                        )
                    }
                }
            }
        }
    }
}
