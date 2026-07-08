package coredevices.coreapp.ui.screens.ringonboarding

import CoreNav
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MobileOff
import androidx.compose.material.icons.filled.Shower
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coreapp.composeapp.generated.resources.Res
import coreapp.composeapp.generated.resources.index01_mic
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.imageResource

private data class FaqEntry(
    val id: String,
    val icon: ImageVector,
    val question: String,
    val answer: AnnotatedString,
    val image: DrawableResource? = null,
)

private val FaqEntries: List<FaqEntry> = listOf(
    FaqEntry(
        "bug", Icons.Default.BugReport,
        "What if I find a bug?",
        ann(
            "This app is a work in progress. We're constantly experimenting and building new " +
                    "features. If you have any problems, we'd love if you report them to us with " +
                    "as much detail as possible. We'll fix it as soon as we can.\n\n" +
                    "Settings → Get Help → Report a bug (be sure to tap Index)"
        ),
    ),
    FaqEntry(
        "shower", Icons.Default.Shower,
        "Can I wear it in the shower?",
        ann(
            "We don't recommend it. Index 01 is splash resistant but not waterproof " +
                    "especially in hot or soapy water.\n\n" +
                    "Water or soap may block the microphone hole, which reduces the " +
                    "volume of recordings."
        ),
    ),
    FaqEntry(
        "listen", Icons.Default.Bluetooth,
        "Is it always listening?",
        ann(
            "Nope! Index 01 only listens while you're holding the button. When you release, " +
                    "it'll be processed by your phone if in range."
        ),
    ),
    FaqEntry(
        "ask", Icons.Outlined.Lightbulb,
        "What can I record?",
        ann(
            "Jot down notes, add a reminder or todo, set timers and alarms, or whatever else " +
                    "you need to remember! Double-click-and-hold to ask quick questions and get " +
                    "the answer in a notification."
        ),
    ),
    FaqEntry(
        "mic", Icons.Default.Mic,
        "Where's the microphone?",
        ann(
            "Look for the small hole, that's the mic.\n\n" +
                    "Hold it within 5-10 cm of your mouth when recording, and wear the ring " +
                    "so your finger doesn't cover the hole."
        ),
        image = Res.drawable.index01_mic,
    ),
    FaqEntry(
        "how", Icons.Default.Mic,
        "How do I use it?",
        ann(
            "Hold the button, speak your mind, then release. You'll see a green light blink " +
                    "twice on your ring, then a notification in 5-10 seconds on your phone with " +
                    "the transcription."
        ),
    ),
    FaqEntry(
        "offline", Icons.Default.MobileOff,
        "What if I want to ditch my phone?",
        ann(
            "Index 01 can store up to 5 minutes of recordings. When you get back to your " +
                    "phone, just click the button to wake it and your recordings will automatically sync!"
        ),
    ),
    FaqEntry(
        "charge", Icons.Default.BatteryChargingFull,
        "How do I charge it?",
        ann(
            "You don't! Index 01 will last up to two years (or more) with 20 six second " +
                    "recordings each day."
        ),
    ),
    FaqEntry(
        "offline2", Icons.Default.WifiOff,
        "Can it be used while offline / in bad cell service?",
        ann(
            "Yes! Your recordings are always saved to Index 01 and synced to your phone " +
                    "regardless of internet status.\n\nIf you select the optional local speech " +
                    "recognition and a local AI model, Index 01 can even process your recordings " +
                    "offline. We recommend setting it up as cloud with a local fallback for the " +
                    "best experience."
        ),
    ),
)

internal val faqEntriesInitialPage = FaqEntries.lastIndex

private fun ann(s: String) = buildAnnotatedString { append(s) }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun FaqTourStep(
    initialPage: Int,
    onLeaveBackwards: () -> Unit,
    onContinue: () -> Unit,
    onExit: () -> Unit,
    coreNav: CoreNav,
) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { FaqEntries.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == FaqEntries.size - 1

    val goBack: () -> Unit = {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        } else {
            onLeaveBackwards()
        }
    }
    BackHandler { goBack() }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBarRow(onLeading = goBack, leadingIsClose = false, onTrailingClose = onExit)

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            FaqTourPage(entry = FaqEntries[page], coreNav = coreNav)
        }

        // Dots
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(FaqEntries.size) { i ->
                val selected = i == pagerState.currentPage
                val dotWidth by animateDpAsState(if (selected) 22.dp else 8.dp, label = "dotW")
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(8.dp)
                        .width(dotWidth)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (selected) LocalPalette.current.primary
                            else LocalPalette.current.surfaceContainerHigh
                        ),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (pagerState.currentPage > 0) {
                OutlinedPillButton(
                    text = "Back",
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    },
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                PrimaryFilledButton(
                    text = if (isLast) "Continue to setup" else "Next",
                    onClick = {
                        if (isLast) {
                            onContinue()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun FaqTourPage(entry: FaqEntry, coreNav: CoreNav) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        // More breathing room between the top bar and the hero icon, per design.
        Spacer(Modifier.height(48.dp))
        val palette = LocalPalette.current
        if (entry.image != null) {
            // Pages with a labeled photo (e.g. mic location) get a larger square
            // tile. PNG has a white background, so we fill the pink container
            // first and draw the image with BlendMode.Multiply. Multiply needs a
            // light base or the whole photo goes dark, so always use the light
            // container color even in dark mode (MOB-8710).
            val bitmap = imageResource(entry.image)
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .aspectRatio(1f),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(color = LightPalette.primaryContainer)
                    val srcW = bitmap.width.toFloat()
                    val srcH = bitmap.height.toFloat()
                    val maxW = size.width * 0.92f
                    val maxH = size.height * 0.92f
                    val scale = minOf(maxW / srcW, maxH / srcH)
                    val drawW = srcW * scale
                    val drawH = srcH * scale
                    val offX = ((size.width - drawW) / 2f).toInt()
                    val offY = ((size.height - drawH) / 2f).toInt()
                    drawImage(
                        image = bitmap,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bitmap.width, bitmap.height),
                        dstOffset = IntOffset(offX, offY),
                        dstSize = IntSize(drawW.toInt(), drawH.toInt()),
                        blendMode = BlendMode.Multiply,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(palette.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = entry.icon,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = palette.primary,
                )
            }
        }
        Spacer(Modifier.height(40.dp))
        Text(
            text = entry.question,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.4).sp,
            color = LocalPalette.current.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = entry.answer,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = LocalPalette.current.onSurfaceVariant,
        )
        if (entry.id == "how") {
            Spacer(Modifier.height(20.dp))
            RingDemo(nav = coreNav)
        }
        Spacer(Modifier.height(24.dp))
    }
}