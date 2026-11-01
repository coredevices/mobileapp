package coredevices.ring.ui.components.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Note
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.IntegrationInstructions
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.alarm_set
import coreapp.ring.generated.resources.noted
import coreapp.ring.generated.resources.noted_to_list
import coreapp.ring.generated.resources.reminder_added
import coreapp.ring.generated.resources.timer_started
import coredevices.mcp.data.SemanticResult
import coredevices.ring.ui.UITimeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString

@Composable
fun SemanticResultIcon(
    result: SemanticResult,
    modifier: Modifier = Modifier
) {
    val icon = when (result) {
        is SemanticResult.MessageSent -> Icons.Default.Send
        is SemanticResult.GenericSuccess -> Icons.Default.Code
        is SemanticResult.GenericFailure -> Icons.Outlined.Warning
        is SemanticResult.AlarmCreation -> Icons.Outlined.Alarm
        is SemanticResult.TimerCreation -> Icons.Outlined.Timer
        is SemanticResult.TaskCreation -> Icons.Outlined.Checklist
        is SemanticResult.ListItemCreation -> Icons.AutoMirrored.Outlined.Note
        is SemanticResult.SupportingData -> Icons.Outlined.IntegrationInstructions
        is SemanticResult.ActionLogged -> Icons.Default.Code
        is SemanticResult.CalendarEventCreation -> Icons.Default.DateRange
        is SemanticResult.RequiresConfirmation -> Icons.Outlined.Lock
        is SemanticResult.UserDenied -> Icons.Outlined.Block
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

suspend fun SemanticResult.actionText(): String {
    return when (this) {
        is SemanticResult.MessageSent -> "Messaged ${this.recipientName}"
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
        is SemanticResult.ActionLogged -> this.title
        is SemanticResult.CalendarEventCreation -> {
            val dateTime = startTime.toLocalDateTime(TimeZone.currentSystemDefault())
            val humanDate = UITimeUtil.humanDate(dateTime.date)
            val humanTime = dateTime.time.format(UITimeUtil.timeFormat())
            "Added $title at $humanDate, $humanTime to calendar"
        }
        is SemanticResult.RequiresConfirmation -> "Needs confirmation"
        is SemanticResult.UserDenied -> "Action denied"
    }
}