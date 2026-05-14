package coredevices.ring.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
actual fun QrCodeImage(
    data: String,
    modifier: Modifier,
    size: Dp,
) {
    // TODO: Implement QR code rendering on iOS using CoreImage CIQRCodeGenerator
    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "QR code available on Android.\nUse Copy Key instead.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
