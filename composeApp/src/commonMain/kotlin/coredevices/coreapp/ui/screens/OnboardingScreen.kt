package coredevices.coreapp.ui.screens

import CoreNav
import PlatformUiContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import coredevices.pebble.Platform
import coredevices.pebble.health.HealthSyncTracker
import coredevices.pebble.health.PlatformHealthSync
import coredevices.pebble.ui.PebbleRoutes
import coredevices.ui.PebbleElevatedButton
import coredevices.ui.SignInButtons
import coredevices.ui.SignInDialog
import coredevices.util.DoneInitialOnboarding
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import coredevices.util.name
import coredevices.util.rememberUiContext
import coredevices.util.requestIsFullScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

enum class OnboardingStage {
    Welcome,
    Permissions,
    SignIn,
    Done,
}

class OnboardingViewModel : ViewModel() {
    val stage = mutableStateOf(OnboardingStage.Welcome)
    val requestedPermissions = mutableStateOf(emptySet<Permission>())
    val healthPermissionHandled = mutableStateOf(false)
}

private val logger = Logger.withTag("OnboardingScreen")

@Composable
fun OnboardingScreen(
    coreNav: CoreNav,
) {
    val viewModel = koinViewModel<OnboardingViewModel>()
    val permissionRequester: PermissionRequester = koinInject()
    val scope = rememberCoroutineScope()
    val settings: Settings = koinInject()
    val doneInitialOnboarding: DoneInitialOnboarding = koinInject()

    fun exitOnboarding() {
        logger.v { "exitOnboarding" }
        settings[SHOWN_ONBOARDING] = true
        doneInitialOnboarding.onDoneInitialOnboarding()
        coreNav.navigateTo(PebbleRoutes.WatchHomeRoute)
    }

    suspend fun requestPermission(permission: Permission, uiContext: PlatformUiContext) {
        permissionRequester.requestPermission(permission, uiContext)
        viewModel.requestedPermissions.value += permission
    }

    Scaffold(
        // TODO setStatusBarTheme(dark) - needs white text (but doing that without changes probably breaks stuff)
        containerColor = MaterialTheme.colorScheme.primary,
    ) { windowInsets ->
        Box(modifier = Modifier.padding(windowInsets).fillMaxSize()) {
            when (viewModel.stage.value) {
                OnboardingStage.Welcome -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "Pebble",
                            fontSize = 35.sp,
                            modifier = Modifier.padding(bottom = 25.dp),
                        )
                        PebbleElevatedButton(
                            text = "Next",
                            onClick = {
                                viewModel.stage.value = OnboardingStage.Permissions
                            },
                            primaryColor = false,
                        )
                    }
                }

                OnboardingStage.Permissions -> {
                    val uiContext = rememberUiContext()
                    if (uiContext != null) {
                        val missingPermissions by permissionRequester.missingPermissions.collectAsState()
                        val permissionToRequest = missingPermissions.firstOrNull {
                            it !in viewModel.requestedPermissions.value
                        }
                        logger.v { "permissionToRequest = $permissionToRequest  /  missingPermissions = $missingPermissions " }
                        if (permissionToRequest == null && !viewModel.healthPermissionHandled.value) {
                            // All regular permissions done — show health sync opt-in
                            val platformHealthSync: PlatformHealthSync = koinInject()
                            val healthSyncTracker: HealthSyncTracker = koinInject()
                            val platform: Platform = koinInject()
                            val platformName = if (platform == Platform.IOS) "Apple Health" else "Health Connect"
                            Column(
                                modifier = Modifier.fillMaxSize().padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = "Health Sync",
                                    fontSize = 25.sp,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "Sync steps, heart rate, sleep, and workouts from your Pebble to $platformName",
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                PebbleElevatedButton(
                                    text = "Enable",
                                    onClick = {
                                        scope.launch {
                                            val available = platformHealthSync.isAvailable()
                                            if (available) {
                                                val granted = platformHealthSync.requestPermissions()
                                                if (granted) {
                                                    healthSyncTracker.enabled = true
                                                    platformHealthSync.sync()
                                                }
                                            }
                                            viewModel.healthPermissionHandled.value = true
                                        }
                                    },
                                    primaryColor = false,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                PebbleElevatedButton(
                                    text = "Skip",
                                    onClick = {
                                        viewModel.healthPermissionHandled.value = true
                                    },
                                    primaryColor = false,
                                )
                            }
                        } else if (permissionToRequest == null) {
                            viewModel.stage.value = OnboardingStage.SignIn
                        } else {
                            val warnBeforeFullScreenRequest = permissionToRequest.requestIsFullScreen()
                            LaunchedEffect(permissionToRequest) {
                                if (!warnBeforeFullScreenRequest) {
                                    requestPermission(permissionToRequest, uiContext)
                                }
                            }
                            Column(
                                modifier = Modifier.fillMaxSize().padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = if (warnBeforeFullScreenRequest) {
                                    Arrangement.Center
                                } else {
                                    Arrangement.Top
                                },
                            ) {
                                if (!warnBeforeFullScreenRequest) {
                                    // Space from top of screen
                                    Spacer(modifier = Modifier.height(20.dp))
                                }
                                Text(
                                    text = permissionToRequest.name(),
                                    fontSize = 25.sp,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(permissionToRequest.descriptionOnboarding(), textAlign = TextAlign.Center)
                                if (warnBeforeFullScreenRequest) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    PebbleElevatedButton(
                                        text = "OK",
                                        onClick = {
                                            scope.launch {
                                                requestPermission(
                                                    permissionToRequest,
                                                    uiContext
                                                )
                                            }
                                        },
                                        primaryColor = false,
                                    )
                                }
                            }
                        }
                    }
                }

                OnboardingStage.SignIn -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "Sign In",
                            fontSize = 35.sp,
                            modifier = Modifier.padding(bottom = 25.dp),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Sign in to backup your Pebble account to backup apps, settings, etc", textAlign = TextAlign.Center)
                        SignInButtons(
                            onDismiss = { viewModel.stage.value = OnboardingStage.Done },
                            primaryColor = false,
                        )
                        PebbleElevatedButton(
                            text = "Skip",
                            onClick = { viewModel.stage.value = OnboardingStage.Done },
                            primaryColor = false,
                        )
                    }
                }

                OnboardingStage.Done -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        PebbleElevatedButton(
                            text = "Connect a Pebble!",
                            onClick = ::exitOnboarding,
                            primaryColor = false,
                        )
                    }
                }
            }
        }
    }
}

val HighlightStyle = SpanStyle(
    fontWeight = FontWeight.Bold,
    fontStyle = FontStyle.Italic
)

expect fun Permission.descriptionOnboarding(): AnnotatedString

const val SHOWN_ONBOARDING = "shown_onboarding"