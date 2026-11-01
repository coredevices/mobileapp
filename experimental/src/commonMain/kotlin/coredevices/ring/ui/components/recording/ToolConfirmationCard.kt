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
import coredevices.ring.ui.theme.IndexTheme
import coredevices.ring.ui.viewmodel.humanizeToolName
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Allow / deny prompt for a tool call the agent paused on, with its parameters in a dialog on demand. */
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
                    "Confirm action",
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
        Text("Action", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        Text(tool, color = colors.onSurfaceVariant, fontSize = 14.sp)
        request.description?.let {
            Spacer(Modifier.height(4.dp))
            Text("Description", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            Text(it, color = colors.onSurfaceVariant, fontSize = 14.sp)
        }
        if (request.arguments.isNotEmpty()) {
            TextButton(
                onClick = { showDetails = true },
                contentPadding = PaddingValues(vertical = 0.dp),
                modifier = Modifier.height(20.dp),
            ) { Text("More details", fontSize = 13.sp) }
        }
        if (request.destructive) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Icon(Icons.Outlined.Warning, contentDescription = null, tint = colors.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("This action is destructive", color = colors.error, fontSize = 14.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onRespond(false) }, modifier = Modifier.weight(1f)) {
                Text("Deny")
            }
            Button(
                onClick = { onRespond(true) },
                modifier = Modifier.weight(1f),
                colors = if (request.destructive) {
                    ButtonDefaults.buttonColors(containerColor = colors.error)
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) { Text("Allow") }
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
        title = { Text("Action parameters") },
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
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
