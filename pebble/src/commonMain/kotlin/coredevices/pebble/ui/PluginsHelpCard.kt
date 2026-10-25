package coredevices.pebble.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import io.rebble.libpebblecommon.locker.AppType

private const val PLUGINS_HELP_DISMISSED_KEY = "pluginsHelpDismissed"

fun Settings.pluginsHelpDismissed() = getBoolean(PLUGINS_HELP_DISMISSED_KEY, false)

fun Settings.dismissPluginsHelp() = set(PLUGINS_HELP_DISMISSED_KEY, true)

@Composable
fun PluginsHelpCard(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            MaterialShapes.Cookie6Sided.toShape(),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        AppType.Plugin.icon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Text(
                    "What are plugins?",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
            }
            Text(
                buildAnnotatedString {
                    append(
                        "Plugins integrate with online services and your phone to provide shortcuts and live " +
                            "data to watchfaces, apps, and the Index Agent. "
                    )
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(
                            "All plugins run locally on your phone and connect directly " +
                                    "to each service, they can only use the permissions you allow."
                        )
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Don't show again")
            }
        }
    }
}
