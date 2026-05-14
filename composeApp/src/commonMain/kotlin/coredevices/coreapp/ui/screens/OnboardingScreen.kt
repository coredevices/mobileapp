package coredevices.coreapp.ui.screens

import CoreNav
import NoOpCoreNav
import PlatformUiContext
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
import coreapp.composeapp.generated.resources.Res
import coreapp.composeapp.generated.resources.pebble_logo
import coredevices.pebble.ui.PebbleRoutes
import coredevices.pebble.ui.PreviewWrapper
import coredevices.ui.PebbleElevatedButton
import coredevices.ui.SignInButtons
import coredevices.util.CoreConfig
import coredevices.util.CoreConfigHolder
import coredevices.util.DoneInitialOnboarding
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import coredevices.util.name
import coredevices.util.rememberUiContext
import coredevices.util.requestIsFullScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.dsl.module
import theme.onboardingScheme


enum class OnboardingStage {
    Welcome,
    DeviceSelection,
    Permissions,
    SignIn,
    Done,
}

enum class DeviceChoice {
    Watch,
    Index01,
    Both,
}

class OnboardingViewModel(private val config: CoreConfigHolder) : ViewModel() {
    val stage = mutableStateOf(OnboardingStage.Welcome)
    val deviceChoice = mutableStateOf<DeviceChoice?>(null)
    val requestedPermissions = mutableStateOf(emptySet<Permission>())
    fun setIndexEnabled(enabled: Boolean) {
        config.update(config.config.value.copy(enableIndex = enabled))
    }
}

private val logger = Logger.withTag("OnboardingScreen")

@Preview
@Composable
fun OnboardingScreenPreview() {
    PreviewWrapper(extraModule = module {
        single { OnboardingViewModel(CoreConfigHolder(
            CoreConfig(),
            settings = Settings(),
            Json.Default
        )) }
    }) {
        OnboardingScreen(NoOpCoreNav)
    }
}


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

    MaterialTheme(colorScheme = onboardingScheme) {
    Scaffold { windowInsets ->
        Box(modifier = Modifier.padding(windowInsets).fillMaxSize()) {
            when (viewModel.stage.value) {
                OnboardingStage.Welcome -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.pebble_logo),
                            contentDescription = "description",
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.height(50.dp),
                        )
                        Spacer(modifier = Modifier.height(15.dp))
                        PebbleElevatedButton(
                            text = "Get Started",
                            onClick = {
                                viewModel.stage.value = OnboardingStage.DeviceSelection
                            },
                            primaryColor = true,
                        )
                    }
                }

                OnboardingStage.DeviceSelection -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "I have a:",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(30.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        ) {
                            DeviceChoiceCard(
                                label = "Watch",
                                icon = Icons.Default.Watch,
                                onClick = {
                                    viewModel.deviceChoice.value = DeviceChoice.Watch
                                    viewModel.stage.value = OnboardingStage.Permissions
                                },
                            )
                            DeviceChoiceCard(
                                label = "Index 01",
                                icon = Icons.Default.RadioButtonUnchecked,
                                onClick = {
                                    viewModel.setIndexEnabled(true)
                                    viewModel.deviceChoice.value = DeviceChoice.Index01
                                    viewModel.stage.value = OnboardingStage.Permissions
                                },
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        DeviceChoiceCard(
                            label = "Both",
                            icon = Icons.Default.Devices,
                            onClick = {
                                viewModel.setIndexEnabled(true)
                                viewModel.deviceChoice.value = DeviceChoice.Both
                                viewModel.stage.value = OnboardingStage.Permissions
                            },
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
                        if (permissionToRequest == null) {
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
                                        primaryColor = true,
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
                            primaryColor = true,
                        )
                        PebbleElevatedButton(
                            text = "Skip",
                            onClick = { viewModel.stage.value = OnboardingStage.Done },
                            primaryColor = true,
    
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
                            primaryColor = true,
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun DeviceChoiceCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier.width(140.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(72.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = label,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

val HighlightStyle = SpanStyle(
    fontWeight = FontWeight.Bold,
    fontStyle = FontStyle.Italic
)

expect fun Permission.descriptionOnboarding(): AnnotatedString

const val SHOWN_ONBOARDING = "shown_onboarding"