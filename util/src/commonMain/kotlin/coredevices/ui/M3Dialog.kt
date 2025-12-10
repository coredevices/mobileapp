package coredevices.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun M3Dialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
    contents: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .then(modifier),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = if (icon != null) {
                        androidx.compose.ui.Alignment.CenterHorizontally
                    } else {
                        androidx.compose.ui.Alignment.Start
                    },
                ) {
                    if (icon != null) {
                        icon()
                        Spacer(Modifier.height(16.dp))
                    }
                    if (title != null) {
                        CompositionLocalProvider(
                            LocalTextStyle provides MaterialTheme.typography.titleLarge,
                        ) {
                            title()
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    CompositionLocalProvider(
                        LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                    ) {
                        contents()
                    }
                }
                if (buttons != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                    ) {
                        buttons()
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun M3DialogPreviewFull() {
    M3Dialog(
        onDismissRequest = {},
        icon = {
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(24.dp)
            )
        },
        title = {
            androidx.compose.material3.Text("Dialog Title")
        },
        buttons = {
            androidx.compose.material3.TextButton(onClick = {}) {
                androidx.compose.material3.Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.TextButton(onClick = {}) {
                androidx.compose.material3.Text("Okay")
            }
        },
    ) {
        androidx.compose.material3.Text(
            "This is the content of the dialog. It can be multiple lines long and contain various information.",
        )
    }
}

@Preview
@Composable
fun M3DialogPreviewNoIcon() {
    M3Dialog(
        onDismissRequest = {},
        title = {
            androidx.compose.material3.Text("Dialog Title")
        },
        buttons = {
            androidx.compose.material3.TextButton(onClick = {}) {
                androidx.compose.material3.Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.TextButton(onClick = {}) {
                androidx.compose.material3.Text("Okay")
            }
        },
    ) {
        androidx.compose.material3.Text(
            "This is the content of the dialog. It can be multiple lines long and contain various information.",
        )
    }
}

@Preview
@Composable
fun M3DialogPreviewNoButtons() {
    M3Dialog(
        onDismissRequest = {},
        title = {
            androidx.compose.material3.Text("Dialog Title")
        },
    ) {
        androidx.compose.material3.Text(
            "This is the content of the dialog. It can be multiple lines long and contain various information.",
        )
        CoreLinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), progress = {0.6f})
    }
}