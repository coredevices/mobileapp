package coredevices.ring.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
actual fun QrCodeImage(
    data: String,
    modifier: Modifier,
    size: Dp,
) {
    val matrix = remember(data) {
        val writer = QRCodeWriter()
        writer.encode(data, BarcodeFormat.QR_CODE, 0, 0)
    }

    Canvas(
        modifier = modifier
            .size(size)
            .background(Color.White)
            .padding(8.dp)
    ) {
        val rows = matrix.height
        val cols = matrix.width
        val cellSize = minOf(
            this.size.width / cols,
            this.size.height / rows
        )
        val xOffset = (this.size.width - cols * cellSize) / 2
        val yOffset = (this.size.height - rows * cellSize) / 2
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                if (matrix.get(col, row)) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(xOffset + col * cellSize, yOffset + row * cellSize),
                        size = Size(cellSize, cellSize)
                    )
                }
            }
        }
    }
}
