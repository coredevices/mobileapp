package coredevices.pebble.fake

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coredevices.ui.M3Dialog
import io.rebble.libpebblecommon.connection.fakeWatchDisplayName
import io.rebble.libpebblecommon.connection.isConsumerWatchPlatform
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform

@Composable
internal fun FakeWatchPickerDialog(
    currentWatches: Set<WatchHardwarePlatform>,
    onAddWatches: (Set<WatchHardwarePlatform>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val consumerWatchPlatforms = remember {
        WatchHardwarePlatform.entries.filter { isConsumerWatchPlatform(it) }
    }
    val availablePlatforms = remember(currentWatches) {
        consumerWatchPlatforms.filter { it !in currentWatches }
    }
    val selected = remember(currentWatches) { mutableStateOf<Set<WatchHardwarePlatform>>(emptySet()) }

    M3Dialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Add Fake Watches") },
        buttons = {
            TextButton(onClick = onDismissRequest) { Text("Cancel") }
            TextButton(
                onClick = { onAddWatches(selected.value) },
                enabled = selected.value.isNotEmpty(),
            ) { Text("Add") }
        },
    ) {
        if (availablePlatforms.isEmpty()) {
            Text("All available watches have been added.")
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(availablePlatforms, key = { it.revision }) { platform ->
                    val isSelected = platform in selected.value
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected.value = selected.value.toggle(platform)
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { selected.value = selected.value.toggle(platform) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(fakeWatchDisplayName(platform), modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
