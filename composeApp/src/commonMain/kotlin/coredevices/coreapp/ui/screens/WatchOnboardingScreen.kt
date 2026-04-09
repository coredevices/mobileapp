package coredevices.coreapp.ui.screens

import CoreNav
import androidx.compose.foundation.layout.Arrangement
import theme.AppTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import coredevices.pebble.Platform
import coredevices.pebble.services.AppStoreHomeResult
import coredevices.pebble.services.PebbleWebServices
import coredevices.pebble.services.StoreOnboarding
import coredevices.pebble.ui.CommonAppType
import coredevices.pebble.ui.LanguageDialog
import coredevices.pebble.ui.NativeLockerAddUtil
import coredevices.pebble.ui.NativeWatchfaceMainContent
import coredevices.pebble.ui.SettingsIds.EnableHealthPlatformSync
import coredevices.pebble.ui.SettingsIds.EnableHealthTracking
import coredevices.pebble.ui.SettingsIds.OfflineSpeechRecognition
import coredevices.pebble.ui.SettingsItemsState
import coredevices.pebble.ui.SnackbarDisplay
import coredevices.pebble.ui.WatchOnboardingFinished
import coredevices.pebble.ui.allCollectionUuids
import coredevices.pebble.ui.asCommonApp
import coredevices.pebble.ui.connectedWatch
import coredevices.pebble.ui.languagePackInstalled
import coredevices.pebble.ui.rememberSettingsItemsState
import coredevices.ui.CoreLinearProgressIndicator
import coredevices.ui.PebbleElevatedButton
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.ConnectedPebbleDeviceInRecovery
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.connection.endpointmanager.FirmwareUpdater
import io.rebble.libpebblecommon.connection.endpointmanager.LanguagePackInstallState
import io.rebble.libpebblecommon.connection.endpointmanager.installing
import io.rebble.libpebblecommon.database.entity.BoolWatchPref
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import theme.onboardingScheme
import kotlin.time.Duration.Companion.seconds

private val logger = Logger.withTag("OnboardingScreen")

@Composable
fun WatchOnboardingScreen(
    coreNav: CoreNav,
) {
    val scope = rememberCoroutineScope()
    val connectedWatch = connectedWatch()
    val pebbleWebServices: PebbleWebServices = koinInject()
    var loadingHomes by remember { mutableStateOf(true) }
    val pebbleStoreHomes = remember { mutableStateMapOf<AppType, AppStoreHomeResult?>() }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var startedUpdate by remember { mutableStateOf(false) }
    val watchOnboardingFinished: WatchOnboardingFinished = koinInject()
    val snackbarDisplay =
        remember { SnackbarDisplay { scope.launch { snackbarHostState.showSnackbar(message = it) } } }
    val settings = rememberSettingsItemsState(
        navBarNav = null,
        snackbarDisplay = snackbarDisplay,
    )
    if (connectedWatch != null) {
        LaunchedEffect(Unit) {
            launch {
                delay(6.seconds)
                // Don't block entire screen on loading apps - but try to make it feel
                // "together" by showing everything at once
                loadingHomes = false
            }
            val results = pebbleWebServices.fetchPebbleAppStoreHomes(
                connectedWatch.watchType.watchType,
                useCache = true
            )
            pebbleStoreHomes.clear()
            pebbleStoreHomes.putAll(results)
            loadingHomes = false
        }
    }

    MaterialTheme(colorScheme = onboardingScheme) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { windowInsets ->
            Box(modifier = Modifier.padding(windowInsets).fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Get Started!",
                        fontSize = 35.sp,
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (connectedWatch == null) {
                        if (startedUpdate) {
                            SectionText("Waiting for your Pebble to restart..")
                        } else {
                            SectionText("Waiting for your Pebble to connect..")
                        }

                        Spacer(modifier = Modifier.height(15.dp))
                    }

                    if (connectedWatch is ConnectedPebbleDeviceInRecovery) {
                        val firmwareUpdateAvailable = connectedWatch.firmwareUpdateAvailable.result
                        if (firmwareUpdateAvailable !is FirmwareUpdateCheckResult.FoundUpdate) {
                            SectionText("Checking for PebbleOS updates..")

                            Spacer(modifier = Modifier.height(15.dp))

                            PebbleElevatedButton(
                                text = "Skip Setup",
                                onClick = { coreNav.goBack() },
                                primaryColor = true,
                            )
                            return@Scaffold
                        }

                        LaunchedEffect(Unit) {
                            logger.d { "Starting firmware update from onboarding screen" }
                            startedUpdate = true
                            connectedWatch.updateFirmware(firmwareUpdateAvailable)
                        }

                        SectionText("Updating your watch to the latest version of PebbleOS...")
                        Spacer(modifier = Modifier.height(15.dp))
                        val progress = (connectedWatch.firmwareUpdateState as? FirmwareUpdater.FirmwareUpdateStatus.InProgress)?.progress?.collectAsState()
                        if (progress != null) {
                            CoreLinearProgressIndicator(
                                progress = { progress.value },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(15.dp))
                        SectionText("Updating your watch to the latest version of PebbleOS...")
                        SectionDivider()
                    }

                    if (connectedWatch !is ConnectedPebbleDevice) {
                        SectionText("Once your Pebble is connected, we'll get it set up")

                        Spacer(modifier = Modifier.height(15.dp))

                        PebbleElevatedButton(
                            text = "Skip Setup",
                            onClick = { coreNav.goBack() },
                            primaryColor = true,
                        )
                    } else {
                        if (loadingHomes) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(5.dp),
                            )
                            return@Scaffold
                        }

                        OnboardingAppCarousel(
                            header = "Add some Watchfaces",
                            storeHome = pebbleStoreHomes[AppType.Watchface],
                            connectedWatch,
                            footerText = null,
                        )

                        OnboardingAppCarousel(
                            header = "Add some Apps",
                            storeHome = pebbleStoreHomes[AppType.Watchapp],
                            connectedWatch,
                            footerText = "Get more apps from the Pebble App Store!",
                        )

                        val languagePackInstalled = connectedWatch.languagePackInstalled()
                        val installingLanguagePack =
                            connectedWatch.languagePackInstallState.installing()
                        SectionText("Install a language pack")
                        Spacer(modifier = Modifier.height(15.dp))
                        if (installingLanguagePack != null) {
                            Text("Installing $installingLanguagePack")
                            Spacer(modifier = Modifier.height(15.dp))
                        } else if (languagePackInstalled != null) {
                            Text("Currently installed: $languagePackInstalled")
                            Spacer(modifier = Modifier.height(15.dp))
                        }
                        val languagePackInstallState = connectedWatch.languagePackInstallState as? LanguagePackInstallState.Installing
                        if (languagePackInstallState != null) {
                            val progress by languagePackInstallState.progress.collectAsState()
                            CoreLinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            )
                        }
                        PebbleElevatedButton(
                            text = "Choose Language",
                            onClick = { showLanguageDialog = true },
                            primaryColor = true,
                            icon = Icons.Outlined.Language,
                            enabled = installingLanguagePack == null,
                        )
                        if (showLanguageDialog) {
                            AppTheme {
                                LanguageDialog(
                                    connectedWatch,
                                    onDismissRequest = { showLanguageDialog = false })
                            }
                        }

                        SectionDivider()

                        // Support settings sync
                        if (connectedWatch.capabilities.contains(ProtocolCapsFlag.SupportsBlobDbVersion)) {
                            SectionText("Configure your watch")
                            Spacer(modifier = Modifier.height(15.dp))

                            settings.Show(BoolWatchPref.Clock24h.id)
                            settings.Show(EnableHealthTracking)
                            settings.Show(EnableHealthPlatformSync)
                            Spacer(modifier = Modifier.height(15.dp))
                        }

                        SectionText("Speech Recognition")
                        Spacer(modifier = Modifier.height(15.dp))
                        settings.Show(OfflineSpeechRecognition)
                        SectionDivider()

                        Text("Configure more in Settings", textAlign = TextAlign.Center)
                        SectionDivider()

                        PebbleElevatedButton(
                            text = "Finished",
                            onClick = {
                                scope.launch {
                                    watchOnboardingFinished.finished.trySend(Unit)
                                    coreNav.goBack()
                                }
                            },
                            primaryColor = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionText(text: String) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 25.dp),
        color = Color.White,
    )
}

@Composable
private fun SettingsItemsState?.Show(id: String) {
    if (this == null) {
        return
    }
    val setting = remember(this) {
        rawSettingsItems.find { it.id == id }
    }
    if (setting == null || !setting.show()) {
        return
    }
    setting.Item()
}

@Composable
fun OnboardingAppCarousel(
    header: String,
    storeHome: AppStoreHomeResult?,
    watch: ConnectedPebbleDevice,
    footerText: String?,
) {
    if (storeHome == null) {
        return
    }
    val platform: Platform = koinInject()
    val allCollectionUuids = allCollectionUuids()
    // Only use inital list of in-collection IDs (i.e. don't remove from list when they add from this screen)
    val initialAllCollectionUuids = remember(allCollectionUuids != null) { allCollectionUuids.orEmpty() }
    val nativeLockerAddUtil: NativeLockerAddUtil = koinInject()
    val watchType = watch.watchType.watchType
    val apps = remember(storeHome, watchType, initialAllCollectionUuids) {
        storeHome.result.onboarding?.forType(watchType)?.mapNotNull { appId ->
            storeHome.result.applications.find { app ->
                app.id == appId
            }?.asCommonApp(
                watchType,
                platform,
                storeHome.source,
                storeHome.result.categories
            )
        }?.filter {
            it.isNativelyCompatible && it.uuid !in initialAllCollectionUuids
        }?.take(5)
    }
    if (apps.isNullOrEmpty()) {
        return
    }
    SectionText(header)

    Spacer(modifier = Modifier.height(15.dp))

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        contentPadding = PaddingValues(horizontal = 5.dp),
    ) {
        items(apps, key = { it.uuid }) { entry ->
            var added by remember { mutableStateOf(false) }
            val commonAppStore = entry.commonAppType as? CommonAppType.Store ?: return@items
            Card(
                modifier = Modifier.padding(3.dp)
                    .width(125.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.91f),
                    contentColor = Color.Black,
                ),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NativeWatchfaceMainContent(
                        entry = entry,
                        highlightInLocker = false,
                        topBarParams = null
                    )
                    if (entry.uuid in allCollectionUuids.orEmpty()) {
                        PebbleElevatedButton(
                            text = "Remove",
                            onClick = {
                                added = false
                                GlobalScope.launch {
                                    nativeLockerAddUtil.removeFromLocker(
                                        commonAppStore.storeSource,
                                        entry.uuid,
                                    )
                                    logger.v { "Remove from locker from watch onboarding ${commonAppStore.storeApp?.title}" }
                                }
                            },
                            primaryColor = true,
                            icon = Icons.Default.RemoveCircle,
                        )
                    } else {
                        PebbleElevatedButton(
                            text = "Add",
                            onClick = {
                                added = true
                                GlobalScope.launch {
                                    val addResult = nativeLockerAddUtil.addAppToLocker(
                                        commonAppStore,
                                        commonAppStore.storeSource
                                    )
                                    logger.v { "Add to locker from watch onboarding ${commonAppStore.storeApp?.title} result=$addResult" }
                                }
                            },
                            primaryColor = true,
                            enabled = !added,
                            icon = Icons.Default.Add,
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                }
            }
        }
    }
    footerText?.let {
        Spacer(modifier = Modifier.height(15.dp))
        Text(footerText, textAlign = TextAlign.Center)
    }
    SectionDivider()
}

fun StoreOnboarding.forType(watchType: WatchType): List<String>? = when (watchType) {
    WatchType.APLITE -> aplite
    WatchType.BASALT -> basalt
    WatchType.CHALK -> chalk
    WatchType.DIORITE -> diorite
    WatchType.EMERY -> emery
    WatchType.FLINT -> flint
    WatchType.GABBRO -> gabbro
}