package coredevices.coreapp.ui.screens.ringonboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coreapp.util.generated.resources.Res
import coreapp.util.generated.resources.ring_wireframe
import org.jetbrains.compose.resources.imageResource

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun WelcomeStep(
    onClose: () -> Unit,
    onGetStarted: () -> Unit,
) {
    val palette = LocalPalette.current
    BackHandler { onClose() }
    Column(modifier = Modifier.fillMaxSize()) {
        TopBarRow(onLeading = onClose, leadingIsClose = true)

        // Hero — ring on soft tonal backdrop. The PNG has a white background;
        // we fill the canvas with the pink container color first, then draw the
        // ring with BlendMode.Multiply so white pixels render as pink and black
        // line-art lines stay black.
        val ringBitmap = imageResource(Res.drawable.ring_wireframe)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp)
                .clip(RoundedCornerShape(28.dp))
                .aspectRatio(4f / 3f),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = palette.primaryContainer)
                val srcW = ringBitmap.width.toFloat()
                val srcH = ringBitmap.height.toFloat()
                val maxW = size.width * 0.92f
                val maxH = size.height * 0.94f
                val scale = minOf(maxW / srcW, maxH / srcH)
                val drawW = srcW * scale
                val drawH = srcH * scale
                val offX = ((size.width - drawW) / 2f).toInt()
                val offY = ((size.height - drawH) / 2f).toInt()
                drawImage(
                    image = ringBitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(ringBitmap.width, ringBitmap.height),
                    dstOffset = IntOffset(offX, offY),
                    dstSize = IntSize(drawW.toInt(), drawH.toInt()),
                    blendMode = BlendMode.Multiply,
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                text = "Meet Index 01",
                fontSize = 36.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
                color = palette.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "You're holding an entirely new type of device. Index 01 isn't like " +
                        "anything you've used before. Please spend a few minutes learning how " +
                        "it works, and how to get the most out of it.",
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = palette.onSurfaceVariant,
            )
        }

        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
        ) {
            PrimaryFilledButton(text = "Get started", onClick = onGetStarted)
        }
    }
}