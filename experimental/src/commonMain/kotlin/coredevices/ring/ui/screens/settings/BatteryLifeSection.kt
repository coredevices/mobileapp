package coredevices.ring.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.settings_battery_life_about_left
import coreapp.ring.generated.resources.settings_battery_life_almost_empty
import coreapp.ring.generated.resources.settings_battery_life_days
import coreapp.ring.generated.resources.settings_battery_life_header
import coreapp.ring.generated.resources.settings_battery_life_learning
import coreapp.ring.generated.resources.settings_battery_life_learning_progress
import coreapp.ring.generated.resources.settings_battery_life_minutes
import coreapp.ring.generated.resources.settings_battery_life_months
import coreapp.ring.generated.resources.settings_battery_life_no_data_subtitle
import coreapp.ring.generated.resources.settings_battery_life_no_data_title
import coreapp.ring.generated.resources.settings_battery_life_remaining_summary
import coreapp.ring.generated.resources.settings_battery_life_seconds
import coreapp.ring.generated.resources.settings_battery_life_stop_soon
import coreapp.ring.generated.resources.settings_battery_life_weeks
import coreapp.ring.generated.resources.settings_battery_life_years
import coredevices.ring.battery.RingBatteryEstimator
import coredevices.ring.battery.RingBatteryLifeState
import coredevices.ring.ui.components.SectionHeader
import coredevices.ring.ui.theme.IndexTheme
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** "Index 01 Battery Life Estimate"; absent while loading or when no Index 01 is known (Index also runs with just a Pebble). */
@Composable
internal fun BatteryLifeSection(state: RingBatteryLifeState?) {
    if (state == null || state == RingBatteryLifeState.NoRing) return
    Column {
        SectionHeader(
            title = stringResource(Res.string.settings_battery_life_header),
            modifier = Modifier.padding(top = 22.dp, bottom = 2.dp, start = 16.dp, end = 16.dp),
        )
        BatteryLifeRows(state)
    }
}

@Composable
private fun BatteryLifeRows(state: RingBatteryLifeState) {
    when (val s = state) {
        RingBatteryLifeState.NoRing -> Unit
        RingBatteryLifeState.NoData -> SettingsRow(
            title = stringResource(Res.string.settings_battery_life_no_data_title),
            subtitle = stringResource(Res.string.settings_battery_life_no_data_subtitle),
        )
        is RingBatteryLifeState.Learning -> LearningRow(s)
        is RingBatteryLifeState.Estimated -> SettingsRow(
            title = if (s.replaceSoon) {
                stringResource(Res.string.settings_battery_life_almost_empty)
            } else {
                stringResource(Res.string.settings_battery_life_about_left, formatRemaining(s.daysLeft))
            },
            subtitle = if (s.replaceSoon) {
                stringResource(Res.string.settings_battery_life_stop_soon)
            } else {
                stringResource(
                    Res.string.settings_battery_life_remaining_summary,
                    "${s.remainingPct}%",
                    formatRecordingTime(s.collectionsPerDay),
                )
            },
        )
    }
}

@Composable
private fun LearningRow(s: RingBatteryLifeState.Learning) {
    val colors = IndexTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(stringResource(Res.string.settings_battery_life_learning), fontSize = 16.sp, color = colors.onSurface)
        Text(
            stringResource(Res.string.settings_battery_life_learning_progress, s.daysCollected, s.daysNeeded),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = colors.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { s.daysCollected.toFloat() / s.daysNeeded },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            color = colors.primary,
            trackColor = colors.outlineVariant,
        )
    }
}

/** Recording time, not press count, is what drains the cell. */
internal fun recordingTime(collectionsPerDay: Double): Pair<StringResource, Int> {
    val seconds = (collectionsPerDay * RingBatteryEstimator.SECONDS_PER_COLLECTION).roundToInt()
    return if (seconds < 60) {
        Res.string.settings_battery_life_seconds to seconds
    } else {
        Res.string.settings_battery_life_minutes to (seconds / 60.0).roundToInt()
    }
}

@Composable
private fun formatRecordingTime(collectionsPerDay: Double): String {
    val (resource, amount) = recordingTime(collectionsPerDay)
    return stringResource(resource, amount)
}

/** Coarse on purpose: the model isn't precise enough for exact dates. */
internal fun remainingAmount(daysLeft: Int): Pair<PluralStringResource, Int> = when {
    // Months up to two years: whole years would show 1.9 years as "1 year".
    daysLeft >= 730 -> Res.plurals.settings_battery_life_years to daysLeft / 365
    daysLeft >= 60 -> Res.plurals.settings_battery_life_months to daysLeft / 30
    daysLeft >= 14 -> Res.plurals.settings_battery_life_weeks to daysLeft / 7
    else -> Res.plurals.settings_battery_life_days to daysLeft
}

@Composable
private fun formatRemaining(daysLeft: Int): String {
    val (resource, amount) = remainingAmount(daysLeft)
    return pluralStringResource(resource, amount, amount)
}
