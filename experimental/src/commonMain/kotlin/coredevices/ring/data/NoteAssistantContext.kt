package coredevices.ring.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.PlaylistAddCheck
import androidx.compose.material.icons.outlined.AlarmOn
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.assistant_not_run
import coreapp.ring.generated.resources.missing_info
import coreapp.ring.generated.resources.noted
import coreapp.ring.generated.resources.transcription_failed
import kotlin.time.Duration

sealed class NoteAssistantContext(val type: ContextType, val icon: ImageVector, val label: String? = null, val labelResource: StringResource? = null) {
    companion object {
        private fun formatTime(time: Instant): String {
            val tz = TimeZone.currentSystemDefault()
            val localTime = time.toLocalDateTime(tz)
            return buildString {
                if (localTime.date != Clock.System.now().toLocalDateTime(tz).date) {
                    append(localTime.date.format(LocalDate.Format {
                        monthName(MonthNames.ENGLISH_FULL) //TODO: Localize
                        char(' ')
                        dayOfMonth(Padding.NONE)
                    }))
                    append(", ")
                }
                append(localTime.time.format(LocalTime.Format {
                    amPmHour(Padding.NONE)
                    char(':')
                    minute()
                    amPmMarker("am", "pm") // TODO: Localize
                }))
            }

        }
    }
    enum class ContextType {
        Action,
        Warning
    }
    data class Reminder(val time: Instant) : NoteAssistantContext(ContextType.Action, Icons.Outlined.AlarmOn, formatTime(time))
    data class AddedToList(val listName: String) : NoteAssistantContext(ContextType.Action, Icons.AutoMirrored.Outlined.PlaylistAddCheck, listName)
    data object Noted : NoteAssistantContext(ContextType.Action, Icons.AutoMirrored.Outlined.PlaylistAddCheck, labelResource = Res.string.noted)
    data class Conversation(val messageCount: Int) : NoteAssistantContext(ContextType.Action, Icons.AutoMirrored.Outlined.Chat, messageCount.toString())
    data class Alarm(val time: Instant) : NoteAssistantContext(ContextType.Action, Icons.Outlined.AlarmOn, formatTime(time))
    data class Timer(val duration: Duration) : NoteAssistantContext(ContextType.Action, Icons.Outlined.AlarmOn, duration.toString())
    abstract class Warning(labelResource: StringResource?, val details: String?) : NoteAssistantContext(ContextType.Warning, Icons.Outlined.ErrorOutline, labelResource = labelResource)
    data object NeedMoreInfo : Warning(Res.string.missing_info, null)
    class TranscriptionError(reason: String) : Warning(Res.string.transcription_failed, reason)
    data object AssistantNotRun : Warning(Res.string.assistant_not_run, null)
}