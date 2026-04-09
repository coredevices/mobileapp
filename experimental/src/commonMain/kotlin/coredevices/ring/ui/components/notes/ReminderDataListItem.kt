package coredevices.ring.ui.components.notes

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

@Composable
fun ReminderDataListItem(data: @Composable () -> Unit, label: @Composable () -> Unit, onClick: (() -> Unit)? = null) {
    ListItem(
        headlineContent = {
            Row {
                ProvideTextStyle(MaterialTheme.typography.titleMedium) {
                    data()
                }
                Spacer(Modifier.weight(1f))
                ProvideTextStyle(MaterialTheme.typography.titleMedium) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) {
                        label()
                    }
                }
            }
        }
    )
}