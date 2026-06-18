package coredevices.pebble.fake

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coredevices.pebble.ui.Section
import coredevices.pebble.ui.SettingsItem
import coredevices.pebble.ui.TopLevelType
import coredevices.pebble.ui.basicSettingsActionItem
import io.rebble.libpebblecommon.connection.fakeWatchDisplayName
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform

internal fun fakeWatchItems(
    fakeWatchConfig: FakeWatchConfigStore,
    fakeWatches: Set<WatchHardwarePlatform>,
    activeFakeWatch: WatchHardwarePlatform?,
    showAddDialog: MutableState<Boolean>,
): List<SettingsItem> {
    val addItem = basicSettingsActionItem(
        title = "Add fake watch (requires restart)",
        description = "Add a simulated watch for testing. Does not require a real Bluetooth connection.",
        topLevelType = TopLevelType.Phone,
        section = Section.Debug,
        action = { showAddDialog.value = true },
        isDebugSetting = true,
        keywords = "fake watch debug",
    )
    return buildList {
        add(addItem)
        fakeWatches.forEach { watch ->
            val isActive = watch == activeFakeWatch
            val displayName = fakeWatchDisplayName(watch)
            add(
                SettingsItem(
                    title = displayName,
                    topLevelType = TopLevelType.Phone,
                    section = Section.Debug,
                    isDebugSetting = true,
                    item = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        fakeWatchConfig.setActiveFakeWatch(watch)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                            ) {
                                RadioButton(
                                    selected = isActive,
                                    onClick = {
                                        fakeWatchConfig.setActiveFakeWatch(watch)
                                    },
                                )
                                Column {
                                    Text(displayName)
                                    Text(
                                        text = if (isActive) "Active (connected)" else "Tap to set as active",
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                            TextButton(onClick = { fakeWatchConfig.removeFakeWatch(watch) }) {
                                Text("Remove")
                            }
                        }
                    },
                )
            )
        }
    }
}
