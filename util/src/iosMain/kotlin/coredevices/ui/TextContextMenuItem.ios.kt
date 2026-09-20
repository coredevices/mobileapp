package coredevices.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.contextmenu.builder.TextContextMenuBuilderScope
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession

@OptIn(ExperimentalFoundationApi::class)
actual fun TextContextMenuBuilderScope.textContextMenuItem(
    key: Any,
    label: String,
    onClick: TextContextMenuSession.() -> Unit,
) {
    item(key = key, label = label, onClick = onClick)
}
