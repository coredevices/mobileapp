package coredevices.pebble.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement.Center
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import co.touchlab.kermit.Logger
import coredevices.pebble.rememberLibPebble
import coredevices.ui.PebbleElevatedButton
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.database.entity.buildTimelineNotification
import io.rebble.libpebblecommon.notification.VibePattern
import io.rebble.libpebblecommon.notification.VibePattern.Companion.uIntPattern
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import theme.coreOrange
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private val logger = Logger.withTag("Vibrations")

fun CoroutineScope.testPattern(vibePattern: VibePattern, libPebble: LibPebble) {
    launch {
        logger.v { "sending test vibe for $vibePattern" }
        val notif = buildTimelineNotification(
            timestamp = Clock.System.now(),
            parentId = Uuid.NIL,
        ) {
            layout = TimelineItem.Layout.GenericNotification
            attributes {
                title { "Test Vibe Pattern" }
                body { "Testing vibe pattern: ${vibePattern.name}" }
                tinyIcon { TimelineIcon.RadioShow }
                vibrationPattern { vibePattern.uIntPattern() }
            }
            actions {
                action(TimelineItem.Action.Type.Generic) {
                    attributes {
                        title { "Test" }
                    }
                }
            }
        }
        libPebble.sendNotification(notif)
    }
}

@Composable
fun VibePatternPickerDialog(
    onResult: (VibePattern?) -> Unit,
    onDismissWithoutResult: () -> Unit,
    selectedPattern: String?,
) {
    val scope = rememberCoroutineScope()
    val libPebble = rememberLibPebble()
    val vibePatterns by libPebble.vibePatterns().collectAsState(emptyList())
    var showCustomVibeDialog by remember { mutableStateOf(false) }
    var patternToDelete by remember { mutableStateOf<VibePattern?>(null) }
    Dialog(
        onDismissRequest = { onDismissWithoutResult() }
    ) {
        if (showCustomVibeDialog) {
            CustomVibeTapperDialog {
                showCustomVibeDialog = false
            }
        }
        patternToDelete?.let { pattern ->
            AlertDialog(
                onDismissRequest = { patternToDelete = null },
                title = { Text("Delete Pattern") },
                text = { Text("Are you sure you want to delete the vibration pattern \"${pattern.name}\"?") },
                confirmButton = {
                    TextButton(onClick = {
                        libPebble.deleteCustomPattern(pattern.name)
                        patternToDelete = null
                    }) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { patternToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
        Card(modifier = Modifier.padding(15.dp)) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Center,
                ) {
                    Text(
                        text = "Choose Vibration Pattern",
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Center,
                ) {
                    PebbleElevatedButton(
                        text = "Add Custom Pattern",
                        onClick = { showCustomVibeDialog = true },
                        primaryColor = true,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(vibePatterns, key = { it.name }) { pattern ->
                        ListItem(
                            leadingContent = {
                                IconButton(
                                    onClick = {
                                        scope.testPattern(pattern, libPebble)
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.PlayCircle,
                                        contentDescription = "Test",
                                    )
                                }
                            },
                            headlineContent = {
                                Text(pattern.name)
                            },
                            trailingContent = {
                                if (!pattern.bundled) {
                                    IconButton(
                                        onClick = {
                                            patternToDelete = pattern
                                        },
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.clickable(onClick = {
                                onResult(pattern)
                            }).border(
                                width = 2.dp,
                                color = if (selectedPattern == pattern.name) coreOrange else Color.Transparent,
                            ),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Center,
                ) {
                    TextButton(
                        onClick = {
                            onDismissWithoutResult()
                        },
                        content = {
                            Text("Cancel")
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            onResult(null)
                        },
                        content = {
                            Text("None")
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private const val MIN_SEGMENT_LENGTH = 30L
private const val MAX_TOTAL_LENGTH = 6000L

@Composable
fun CustomVibeTapperDialog(onDismiss: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val timings = remember { mutableStateListOf<Long>() }
    var lastEventTime by remember { mutableStateOf(0L) }
    var lastTappedPattern by remember { mutableStateOf<List<Long>?>(null) }
    var finishJob by remember { mutableStateOf<Job?>(null) }
    var patternName by remember { mutableStateOf("") }
    var pressedRightNow by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val libPebble = rememberLibPebble()
    Dialog(
        onDismissRequest = { onDismiss() }
    ) {
        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        pressedRightNow = true
                    }

                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        pressedRightNow = false
                        if (lastEventTime == 0L) {
                            return@collect
                        }
                    }
                }

                lastTappedPattern = null
                finishJob?.cancel()
                finishJob = scope.launch {
                    delay(1.seconds)
                    lastEventTime = 0
                    // TODO: When done, construct VibePattern and call onResult
                    val newPattern = mutableListOf<Long>()
                    var totalLength = 0L
                    var i = 0
                    while (i < timings.size) {
                        val timing = timings[i]
                        val increase = maxOf(0, MIN_SEGMENT_LENGTH - timing.toInt())
                        val newTiming = (timing) + increase
                        if (totalLength + newTiming > MAX_TOTAL_LENGTH) break
                        totalLength += newTiming.toLong()
                        newPattern.add(newTiming)
                        if (increase > 0 && i + 1 < timings.size) {
                            timings[i + 1] = maxOf(0, timings[i + 1] - increase.toLong())
                        }
                        i++
                    }
                    lastTappedPattern = newPattern
                    logger.v { "Tapped pattern: $timings  =  $lastTappedPattern" }
                    timings.clear()
                }
                val now = Clock.System.now().toEpochMilliseconds()
                if (lastEventTime != 0L) {
                    timings.add(now - lastEventTime)
                }
                lastEventTime = now
            }
        }

        val tapBackgroundColor = if (pressedRightNow) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.primary
        }
        val tapTextColor = if (pressedRightNow) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onPrimary
        }

        Card(modifier = Modifier.padding(15.dp)) {
            Column(
                modifier = Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {},
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tapBackgroundColor)
                ) {
                    Text(
                        text = "Tap custom pattern",
                        modifier = Modifier.padding(vertical = 100.dp).align(Alignment.Center),
                        color = tapTextColor,
                    )
                }

                Row {
                    IconButton(
                        onClick = {
                            lastTappedPattern?.let { pattern ->
                                val name = patternName.ifBlank { "Custom Tapped Pattern" }
                                val vibePattern = VibePattern(name, pattern, bundled = false)
                                scope.testPattern(vibePattern, libPebble)
                            }
                        },
                        modifier = Modifier.padding(5.dp),
                        enabled = lastTappedPattern != null,
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = "Test",
                        )
                    }

                    TextField(
                        value = patternName,
                        onValueChange = { patternName = it },
                        label = { Text("Pattern Name") },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    TextButton(
                        onClick = {
                            onDismiss()
                        },
                        content = {
                            Text("Cancel")
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            lastTappedPattern?.let { pattern ->
                                if (patternName.isNotBlank()) {
                                    libPebble.addCustomVibePattern(
                                        name = patternName,
                                        pattern = pattern,
                                    )
                                    onDismiss()
                                }
                            }
                        },
                        content = {
                            Text("Save")
                        },
                        enabled = lastTappedPattern != null && patternName.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun SelectVibePatternOrNone(
    currentPattern: String?,
    onChangePattern: (VibePattern?) -> Unit,
    subtext: String? = null,
) {
    var showVibePatternChooser by remember { mutableStateOf(false) }
    if (showVibePatternChooser) {
        VibePatternPickerDialog(
            onResult = { pattern ->
                onChangePattern(pattern)
                showVibePatternChooser = false
            },
            onDismissWithoutResult = {
                showVibePatternChooser = false
            },
            selectedPattern = currentPattern,
        )
    }
    ListItem(
        headlineContent = {
            Text("Vibration Pattern")
        },
        supportingContent = {
            Column {
                Text(currentPattern ?: "Default")
                if (subtext != null) {
                    Text(subtext, fontSize = 12.sp)
                }
            }
        },
        trailingContent = {
            PebbleElevatedButton(
                text = "Select",
                onClick = {
                    showVibePatternChooser = true
                },
                icon = Icons.Default.Vibration,
                contentDescription = "Select vibration pattern",
                primaryColor = true,
                modifier = Modifier.padding(8.dp),
            )
        },
    )
}

@Preview
@Composable
fun VibePickerPreview() {
    PreviewWrapper {
        VibePatternPickerDialog({ }, {}, null)
    }
}