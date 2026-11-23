package coredevices.ring.ui.components.recording

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coredevices.mcp.data.SemanticResult
import coredevices.ring.agent.confirmation.argumentLines
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ring.ui.viewmodel.humanizeToolName
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.feed_action_destructive
import coreapp.ring.generated.resources.feed_action_parameters
import coreapp.ring.generated.resources.feed_allow
import coreapp.ring.generated.resources.feed_confirm_action
import coreapp.ring.generated.resources.feed_deny
import coreapp.ring.generated.resources.feed_more_details
import coreapp.ring.generated.resources.feed_tool_action
import coreapp.ring.generated.resources.feed_tool_description
import coreapp.ring.generated.resources.feed_tool_parameters
import coreapp.util.generated.resources.Res as UtilRes
import coreapp.util.generated.resources.close
import org.jetbrains.compose.resources.stringResource

/** Allow / deny prompt for a tool call the agent paused on, listing its parameters unless the tool supplied its own title. */
@Composable
fun ToolConfirmationCard(
    request: SemanticResult.RequiresConfirmation,
    onRespond: (approved: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IndexTheme.colors
    var showDetails by remember { mutableStateOf(false) }
    val tool = humanizeToolName(request.toolName) ?: request.toolName
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
            .background(colors.surfaceContainerLow)
            .border(1.dp, colors.chipOutline, RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Security,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(6.dp))
            Column {
                Text(
                    request.title ?: stringResource(Res.string.feed_confirm_action),
                    color = colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        request.integrationName,
                        color = colors.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                    )
                    Spacer(Modifier.width(3.dp))
                    with(LocalDensity.current) {
                        Icon(
                            Icons.Filled.Power,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(11.sp.toDp()),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.feed_tool_action), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text(tool, color = colors.onSurfaceVariant, fontSize = 14.sp)
        if (request.title == null && request.arguments.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.feed_tool_parameters), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            request.argumentLines.forEach { Text(it, color = colors.onSurface, fontSize = 14.sp) }
        }
        request.description?.let {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(Res.string.feed_tool_description), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            Text(it, color = colors.onSurfaceVariant, fontSize = 14.sp)
        }
        if (request.title != null && request.arguments.isNotEmpty()) {
            TextButton(
                onClick = { showDetails = true },
                contentPadding = PaddingValues(vertical = 0.dp),
                modifier = Modifier.height(20.dp),
            ) { Text(stringResource(Res.string.feed_more_details), fontSize = 13.sp) }
        }
        if (request.destructive) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Icon(Icons.Outlined.Warning, contentDescription = null, tint = colors.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.feed_action_destructive), color = colors.error, fontSize = 14.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onRespond(false) }, modifier = Modifier.weight(1f)) {
                Text(request.denyLabel ?: stringResource(Res.string.feed_deny))
            }
            Button(
                onClick = { onRespond(true) },
                modifier = Modifier.weight(1f),
                colors = if (request.destructive) {
                    ButtonDefaults.buttonColors(containerColor = colors.error)
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) { Text(request.allowLabel ?: stringResource(Res.string.feed_allow)) }
        }
    }
    if (showDetails) {
        ParameterDetailsDialog(
            arguments = request.arguments,
            onDismiss = { showDetails = false },
        )
    }
}

@Composable
private fun ParameterDetailsDialog(
    arguments: Map<String, JsonElement>,
    onDismiss: () -> Unit,
) {
    val colors = IndexTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.feed_action_parameters)) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(arguments.entries.size) { i ->
                    val (key, value) = arguments.entries.elementAt(i)
                    Column {
                        Text(
                            key,
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            (value as? JsonPrimitive)?.content ?: value.toString(),
                            color = colors.onSurface,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(UtilRes.string.close)) }
        },
    )
}
