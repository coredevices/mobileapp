package coredevices.ring.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
expect fun QrCodeImage(
    data: String,
    modifier: Modifier = Modifier,
    size: Dp = 250.dp,
)
