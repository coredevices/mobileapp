package coredevices.ring.ui.components.feed

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coredevices.ring.ui.components.SectionHeader
import coredevices.ring.ui.monthNames
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.friday
import coreapp.ring.generated.resources.monday
import coreapp.ring.generated.resources.saturday
import coreapp.ring.generated.resources.sunday
import coreapp.ring.generated.resources.thursday
import coreapp.ring.generated.resources.today
import coreapp.ring.generated.resources.tuesday
import coreapp.ring.generated.resources.wednesday
import coreapp.ring.generated.resources.yesterday
import kotlinx.datetime.format.Padding

@Composable
fun FeedListSectionHeader(date: LocalDate) {
    val monthNames = monthNames()
    val dayOfWeek = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> Res.string.monday
        DayOfWeek.TUESDAY -> Res.string.tuesday
        DayOfWeek.WEDNESDAY -> Res.string.wednesday
        DayOfWeek.THURSDAY -> Res.string.thursday
        DayOfWeek.FRIDAY -> Res.string.friday
        DayOfWeek.SATURDAY -> Res.string.saturday
        DayOfWeek.SUNDAY -> Res.string.sunday
        else -> error("Unknown day of week")
    }
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val style = MaterialTheme.typography.labelMedium.copy(
        color = color
    )
    val verboseDate = remember {
        date.format(LocalDate.Format {
            monthName(monthNames)
            char(' ')
            day(padding = Padding.NONE)
        })
    }
    val text = when {
        date == now.date -> stringResource(Res.string.today)
        date == now.date - DatePeriod(days = 1) -> stringResource(Res.string.yesterday)
        date < now.date - DatePeriod(days = 7) -> verboseDate
        else -> stringResource(dayOfWeek)
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .border(1.dp, color, CircleShape)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text, textAlign = TextAlign.Center, style = style, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
}