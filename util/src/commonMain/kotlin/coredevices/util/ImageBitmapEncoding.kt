package coredevices.util

import androidx.compose.ui.graphics.ImageBitmap

expect fun imageBitmapToPngBase64(bitmap: ImageBitmap): String
