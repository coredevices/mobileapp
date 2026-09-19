package coredevices.ui

import androidx.compose.foundation.text.contextmenu.builder.TextContextMenuBuilderScope
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession

/** Foundation's `item` builder differs per platform (Android takes a drawable
 *  res, skiko a composable), so common code goes through this instead. */
expect fun TextContextMenuBuilderScope.textContextMenuItem(
    key: Any,
    label: String,
    onClick: TextContextMenuSession.() -> Unit,
)
