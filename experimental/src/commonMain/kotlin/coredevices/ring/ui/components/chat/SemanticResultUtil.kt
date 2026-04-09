package coredevices.ring.ui.components.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Note
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.IntegrationInstructions
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.alarm_set
import coreapp.ring.generated.resources.am
import coreapp.ring.generated.resources.noted
import coreapp.ring.generated.resources.noted_to_list
import coreapp.ring.generated.resources.pm
import coreapp.ring.generated.resources.reminder_added
import coreapp.ring.generated.resources.timer_started
import coredevices.mcp.data.SemanticResult
import coredevices.ring.ui.UITimeUtil
import coredevices.ring.ui.isLocale24HourFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun SemanticResultIcon(
    result: SemanticResult,
    modifier: Modifier = Modifier
) {
    val icon = when (result) {
        is SemanticResult.GenericSuccess -> Icons.Default.Code
        is SemanticResult.GenericFailure -> Icons.Outlined.Warning
        is SemanticResult.AlarmCreation -> Icons.Outlined.Alarm
        is SemanticResult.TimerCreation -> Icons.Outlined.Timer
        is SemanticResult.TaskCreation -> Icons.Outlined.Checklist
        is SemanticResult.ListItemCreation -> Icons.AutoMirrored.Outlined.Note
        is SemanticResult.SupportingData -> Icons.Outlined.IntegrationInstructions
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier
    )
}

@Composable
fun SemanticResultActionTaken(
    result: SemanticResult,
    modifier: Modifier = Modifier
) {
    val actionText = remember(result) {
        runBlocking(Dispatchers.Default) {
            result.actionText()
        }
    }
    Text(text = actionText, modifier = modifier)
}

fun SemanticResult.hasExpandedDetails(): Boolean {
    return when (this) {
        is SemanticResult.AlarmCreation,
        is SemanticResult.TimerCreation,
        is SemanticResult.TaskCreation,
        is SemanticResult.ListItemCreation -> true
        is SemanticResult.SupportingData if this.summary != null -> true
        is SemanticResult.GenericFailure if this.userErrorMessage != null -> true
        else -> false
    }
}

private val BIG_TIME_TEXT_STYLE = TextStyle(
    fontSize = 66.sp,
    fontWeight = FontWeight(400),
    fontFamily = FontFamily.SansSerif,
    fontStyle = FontStyle.Normal
)
private val TIME_FORMATTER = LocalTime.Format {
    if (isLocale24HourFormat()) {
        amPmHour()
    } else {
        hour()
    }
    char(':')
    minute()
}
@Composable
fun SemanticResultDetails(
    result: SemanticResult,
    modifier: Modifier = Modifier
) {
    when (result) {
        is SemanticResult.AlarmCreation -> {
            val hour24 = isLocale24HourFormat()
            Column(modifier) {
                SemanticResultOverline("Next instance of")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = result.fireTime.format(TIME_FORMATTER), style = BIG_TIME_TEXT_STYLE)
                    if (!hour24) {
                        val amPm = if (result.fireTime.hour < 12) {
                            stringResource(Res.string.am)
                        } else {
                            stringResource(Res.string.pm)
                        }
                        Text(text = amPm)
                    }
                }
            }
        }
        is SemanticResult.TimerCreation -> {
            if (result.requestedDuration != null) {
                Column(modifier) {
                    SemanticResultOverline(result.requestedDuration.toString())
                    Row {
                        //TODO: Show countdown timer
                        Text(text = "-:--:--", style = BIG_TIME_TEXT_STYLE)
                        Spacer(Modifier.weight(1f))
                        //TODO: Implement timer controls
                        IconButton(
                            onClick = {},
                            enabled = false
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = null
                            )
                        }
                        IconButton(
                            onClick = {},
                            enabled = false
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
        is SemanticResult.TaskCreation -> {
            Column(modifier) {
                val dateTime = remember { result.deadline?.toLocalDateTime(TimeZone.currentSystemDefault()) }
                val humanDate = remember { dateTime?.let {
                    runBlocking(Dispatchers.Default) {
                        UITimeUtil.humanDate(dateTime.date)
                    }
                } }
                val humanTime = remember { dateTime?.let {
                    runBlocking(Dispatchers.Default) {
                        it.time.format(UITimeUtil.timeFormat())
                    }
                } }
                dateTime?.let {
                    SemanticResultOverline("$humanDate, $humanTime")
                }
                Row(modifier = Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(false, onCheckedChange = {})
                    Text(text = result.title)
                }
            }
        }
        is SemanticResult.ListItemCreation -> {
            Text(text = result.content, modifier = modifier, fontSize = 15.sp)
        }
        is SemanticResult.SupportingData -> {
            result.summary?.let {
                Text(text = it, modifier = modifier)
            }
        }
        is SemanticResult.GenericFailure -> {
            result.userErrorMessage?.let {
                Text(text = it, modifier = modifier)
            }
        }
        else -> {
            // No additional details for other types
        }
    }
}

@Composable
fun SemanticResultOverline(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = modifier
    )
}

suspend fun SemanticResult.actionText(): String {
    return when (this) {
        is SemanticResult.GenericSuccess -> "Action completed"
        is SemanticResult.GenericFailure -> "Action failed"
        is SemanticResult.AlarmCreation -> getString(Res.string.alarm_set)
        is SemanticResult.TimerCreation -> getString(Res.string.timer_started)
        is SemanticResult.TaskCreation -> getString(Res.string.reminder_added)
        is SemanticResult.ListItemCreation -> if (this.listUsed != null) {
            getString(Res.string.noted_to_list, this.listUsed!!)
        } else {
            getString(Res.string.noted)
        }
        is SemanticResult.SupportingData -> "Gathered info"
    }
}