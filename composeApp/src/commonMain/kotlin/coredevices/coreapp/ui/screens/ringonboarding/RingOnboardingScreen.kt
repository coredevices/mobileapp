@file:OptIn(ExperimentalComposeUiApi::class)

package coredevices.coreapp.ui.screens.ringonboarding

import CoreNav
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coredevices.pebble.ui.SnackbarDisplay
import coredevices.ring.database.Preferences
import coredevices.ring.ui.viewmodel.SettingsViewModel
import coredevices.util.Platform
import coredevices.util.isAndroid
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RingOnboardingScreen(
    coreNav: CoreNav,
) {
    val preferences: Preferences = koinInject()
    val platform: Platform = koinInject()
    val viewModel = koinViewModel<SettingsViewModel>()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val snackbarDisplay =
        remember { SnackbarDisplay { scope.launch { snackbarHostState.showSnackbar(message = it) } } }
    var step by remember { mutableStateOf(0) }
    // Seed for the FAQ pager — bumped to lastIndex when user lands on Setup
    // so a "back" from Setup returns them to the final guide page.
    var faqInitialPage by remember { mutableStateOf(0) }
    val isAndroid = remember { platform.isAndroid }
    val exit: () -> Unit = { coreNav.goBack() }

    val palette = if (isSystemInDarkTheme()) DarkPalette else LightPalette

    CompositionLocalProvider(LocalPalette provides palette) {
        Scaffold(
            containerColor = palette.surface,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { windowInsets ->
            Box(modifier = Modifier.padding(windowInsets).fillMaxSize()) {
                Crossfade(targetState = step, label = "onboarding_step") { currentStep ->
                    when (currentStep) {
                        0 -> WelcomeStep(
                            onClose = exit,
                            onGetStarted = {
                                faqInitialPage = 0
                                step = 1
                            },
                        )
                        1 -> FaqTourStep(
                            initialPage = faqInitialPage,
                            onLeaveBackwards = { step = 0 },
                            onContinue = { step = 2 },
                            onExit = exit,
                            coreNav = coreNav,
                        )
                        else -> SetupStep(
                            coreNav = coreNav,
                            viewModel = viewModel,
                            preferences = preferences,
                            snackbarDisplay = snackbarDisplay,
                            isAndroid = isAndroid,
                            onBack = {
                                faqInitialPage = faqEntriesInitialPage
                                step = 1
                            },
                            onExit = exit,
                            onFinish = exit,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun produceAuthState(
    refreshKey: Any?,
    load: suspend () -> Boolean,
): State<Boolean> {
    val state = remember { mutableStateOf(false) }
    LaunchedEffect(refreshKey) { state.value = load() }
    return state
}

internal enum class PressPattern { None, Single, Double, ShortHold }

@Composable
internal fun PressTile(
    label: String,
    pattern: PressPattern,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val contentColor =
        if (selected) LocalPalette.current.primary
        else LocalPalette.current.onSurface
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.alpha(if (enabled) 1f else 0.4f),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) LocalPalette.current.primaryContainer
        else LocalPalette.current.surfaceContainerLowest,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) LocalPalette.current.primary
            else LocalPalette.current.outlineVariant,
        ),
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier.height(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (pattern) {
                    PressPattern.None -> Text("—", fontSize = 11.sp, color = contentColor.copy(alpha = 0.5f))
                    PressPattern.Single -> PressDots(listOf(8.dp))
                    PressPattern.Double -> PressDots(listOf(8.dp, 8.dp))
                    PressPattern.ShortHold -> PressDots(listOf(8.dp, 20.dp))
                }
            }
        }
    }
}

@Composable
private fun PressDots(widths: List<Dp>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        widths.forEach { w ->
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(w)
                    .clip(RoundedCornerShape(4.dp))
                    .background(LocalContentColor.current),
            )
        }
    }
}

// ─── Shared bits ───────────────────────────────────────────────────────────

@Composable
internal fun TopBarRow(
    onLeading: () -> Unit,
    leadingIsClose: Boolean,
    title: String? = null,
    onTrailingClose: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onLeading) {
            Icon(
                imageVector = if (leadingIsClose) Icons.Default.Close
                else Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = if (leadingIsClose) "Close" else "Back",
                tint = LocalPalette.current.onSurface,
            )
        }
        if (title != null) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = LocalPalette.current.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (onTrailingClose != null) {
            IconButton(onClick = onTrailingClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit onboarding",
                    tint = LocalPalette.current.onSurface,
                )
            }
        } else {
            // Keep the title visually centered when there's no trailing icon.
            Spacer(Modifier.width(44.dp))
        }
    }
}

@Composable
private fun StatusPill(label: String, icon: ImageVector) {
    Surface(
        shape = RoundedCornerShape(50),
        color = LocalPalette.current.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = LocalPalette.current.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = LocalPalette.current.primary,
            )
        }
    }
}

@Composable
internal fun PrimaryFilledButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = if (enabled) LocalPalette.current.primary
        else LocalPalette.current.surfaceContainerHigh,
        contentColor = if (enabled) LocalPalette.current.onPrimary
        else LocalPalette.current.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun OutlinedPillButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        contentColor = LocalPalette.current.onSurface,
        border = BorderStroke(1.5.dp, LocalPalette.current.outlineVariant),
        modifier = Modifier.height(52.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
