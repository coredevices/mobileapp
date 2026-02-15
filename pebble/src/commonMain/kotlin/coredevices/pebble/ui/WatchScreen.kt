package coredevices.pebble.ui


import PlatformShareLauncher
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.Settings
import com.russhwolf.settings.serialization.decodeValue
import com.russhwolf.settings.serialization.encodeValue
import coredevices.pebble.account.GithubAccount
import coredevices.pebble.rememberLibPebble
import coredevices.pebble.services.Github
import coredevices.pebble.services.GithubUser
import coredevices.pebble.services.LanguagePack
import coredevices.pebble.services.LanguagePackRepository
import coredevices.pebble.services.displayName
import coredevices.ui.PebbleElevatedButton
import coredevices.util.CoreConfigFlow
import coredevices.util.Platform
import io.rebble.libpebblecommon.connection.BleDiscoveredPebbleDevice
import io.rebble.libpebblecommon.connection.ConnectedPebble
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.ConnectedPebbleDeviceInRecovery
import io.rebble.libpebblecommon.connection.KnownPebbleDevice
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.connection.PebbleDevice
import io.rebble.libpebblecommon.connection.endpointmanager.LanguagePackInstallState
import io.rebble.libpebblecommon.connection.forDevice
import io.rebble.libpebblecommon.database.entity.buildTimelineNotification
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import io.rebble.libpebblecommon.services.blobdb.TimelineActionResult
import io.rebble.libpebblecommon.timeline.TimelineColor
import io.rebble.libpebblecommon.timeline.toPebbleColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import theme.coreOrange
import kotlin.time.Clock
import kotlin.uuid.Uuid

private val logger = Logger.withTag("WatchScreen")

@Composable
fun WatchScreen(
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
    identifier: String,
) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val libPebble = rememberLibPebble()
        val watchesFlow = remember(identifier) {
            libPebble.watches.forDevice(identifier)
        }
        val watch by watchesFlow.collectAsState(null)
        LaunchedEffect(Unit) {
            topBarParams.searchAvailable(null)
            topBarParams.actions {}
            topBarParams.title("Device")
        }
        watch?.let { WatchScreenContent(navBarNav, topBarParams, it, libPebble) }
    }
}

fun String?.isNotEmptyNullable(): Boolean = this != null && this.isNotEmpty()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchScreenContent(
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
    watch: PebbleDevice,
    libPebble: LibPebble,
) {
    val watchColorText = remember(watch) {
        when (watch) {
            is KnownPebbleDevice -> watch.color?.uiDescription
            else -> null
        }
    }
    val settings: Settings = koinInject()
    val scope = rememberCoroutineScope()
    val showDebugOptions = settings.showDebugOptions()
    val bluetoothState by libPebble.bluetoothEnabled.collectAsState()
    val otherPebbleAppsInstalledFlow = remember {
        libPebble.otherPebbleCompanionAppsInstalled()
    }
    val otherPebbleAppsInstalled by otherPebbleAppsInstalledFlow.collectAsState()
    val coreConfigFlow = koinInject<CoreConfigFlow>()
    val coreConfig by coreConfigFlow.flow.collectAsState()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 15.dp)) {
        WatchHeader(watch)
        WatchDetails(
            watch = watch,
            bluetoothState = bluetoothState,
            allowedToConnect = otherPebbleAppsInstalled.isEmpty() || coreConfig.ignoreOtherPebbleApps,
            showForget = true,
            onForget = {
                topBarParams.showSnackbar(
                    "${watch.displayName()} forgotten",
                )
                navBarNav.goBack()
            },
        )

        PebbleElevatedButton(
            text = "Check For Updates",
            primaryColor = false,
            onClick = { libPebble.checkForFirmwareUpdates() },
            icon = Icons.Default.Refresh,
            contentDescription = "CheckFor Updates",
            modifier = Modifier.padding(vertical = 5.dp),
        )

        ElevatedCard(
            elevation = CardDefaults.cardElevation(
                defaultElevation = 6.dp,
            ),
            modifier = Modifier.padding(vertical = 36.dp, horizontal = 14.dp)
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(13.dp).fillMaxWidth()) {
                if (watchColorText != null) {
                    Text(
                        text = watchColorText,
                        modifier = Modifier.padding(5.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }

                val serial = when (watch) {
                    is KnownPebbleDevice -> watch.serial
                    is BleDiscoveredPebbleDevice -> watch.pebbleScanRecord.serialNumber
                    else -> null
                }

                if (serial != null) {
                    Text(
                        text = "Serial: $serial",
                        modifier = Modifier.padding(5.dp),
                    )
                }

                LanguagePacks(watch)

                Nickname(
                    watch = watch,
                    modifier = Modifier.padding(5.dp),
                )
            }
        }

        Screenshot(watch, scope)

        DevConnection(watch, libPebble, scope, modifier = Modifier.padding(vertical = 5.dp))

        if (showDebugOptions) {
            if (watch is ConnectedPebble.Firmware) {
                PebbleElevatedButton(
                    onClick = {
                        navBarNav.navigateTo(
                            PebbleRoutes.FirmwareSideloadRoute(
                                watch.identifier.asString,
                            )
                        )
                    },
                    text = "Firmware Update Debug",
                    primaryColor = false,
                    modifier = Modifier.padding(vertical = 5.dp),
                )
            }
            if (watch is ConnectedPebbleDevice) {
                PebbleElevatedButton(
                    onClick = { scope.launch { watch.sendPing(1234u) } },
                    text = "Ping Watch",
                    primaryColor = false,
                    modifier = Modifier.padding(vertical = 5.dp),
                )
                
                var showNotificationDialog by remember { mutableStateOf(false) }
                if (showNotificationDialog) {
                    NotificationDialog(
                        onDismiss = { showNotificationDialog = false },
                    )
                }
                
                PebbleElevatedButton(
                    onClick = { showNotificationDialog = true },
                    text = "Write Notification",
                    primaryColor = false,
                    modifier = Modifier.padding(vertical = 5.dp),
                )
                ConfirmDumpButton(
                    text = "Create a Core Dump",
                    description = "This will capture the current state of the watch - then the watch will reset. Send a bug report after reconnection to send us the core dump. Only use this if we asked you to!",
                    action = {
                        watch.createCoreDump()
                    },
                    icon = Icons.Default.Warning,
                )
                ConfirmDumpButton(
                    text = "Reset into PRF",
                    description = "This will reset the watch into recovery mode. Not for general public use.",
                    action = {
                        if (watch.watchInfo.recoveryFwVersion != null) {
                            watch.resetIntoPrf()
                        }
                    },
                    icon = Icons.Default.Warning,
                    enabled = watch.watchInfo.recoveryFwVersion != null,
                )
                ConfirmDumpButton(
                    text = "Factory reset",
                    description = "This will wipe the watch completely",
                    action = {
                        if (watch.watchInfo.recoveryFwVersion != null) {
                            watch.factoryReset()
                        }
                    },
                    icon = Icons.Default.Warning,
                    enabled = watch.watchInfo.recoveryFwVersion != null,
                )
            }
        }
    }
}

@Serializable
data class TestNotificationContent(
    val title: String = "Test Notification",
    val body: String = "This is a test notification",
    val icon: TimelineIcon? = TimelineIcon.GenericSms,
    val color: TimelineColor? = TimelineColor.Orange,
)

private const val CUSTOM_NOTIFICATION_CONTENT_KEY = "custom_notification_content"

@OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
@Composable
private fun NotificationDialog(
    onDismiss: () -> Unit,
) {
    val settings: Settings = koinInject()
    var content by remember { mutableStateOf(settings.decodeValue(CUSTOM_NOTIFICATION_CONTENT_KEY, TestNotificationContent())) }
    val scope = rememberCoroutineScope()
    val libPebble = rememberLibPebble()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Notification") },
        text = {
            Column {
                TextField(
                    value = content.title,
                    onValueChange = { content = content.copy(title = it) },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                TextField(
                    value = content.body,
                    onValueChange = { content = content.copy(body = it) },
                    label = { Text("Body") },
                    modifier = Modifier.fillMaxWidth()
                )
                SelectColorOrNone(
                    currentColorName = content.color?.name,
                    onChangeColor = { color ->
                        content = content.copy(color = color)
                    },
                )
                SelectIconOrNone(
                    currentIcon = TimelineIcon.fromCode(content.icon?.code),
                    onChangeIcon = { icon ->
                        content = content.copy(icon = icon)
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.encodeValue(CUSTOM_NOTIFICATION_CONTENT_KEY, content)
                onDismiss()
                scope.launch {
                    val testActionId: UByte = 0u
                    val notif = buildTimelineNotification(
                        timestamp = Clock.System.now(),
                        parentId = Uuid.NIL,
                    ) {
                        layout = TimelineItem.Layout.GenericNotification
                        attributes {
                            title { content.title }
                            body { content.body }
                            content.icon?.let { tinyIcon { it } }
                            content.color?.let { backgroundColor { it.toPebbleColor() } }
                        }
                        actions {
                            action(TimelineItem.Action.Type.Generic) {
                                attributes {
                                    title { "Test" }
                                }
                            }
                        }
                    }
                    libPebble.sendNotification(
                        notif, mapOf(
                            testActionId to { _ ->
                                TimelineActionResult(
                                    success = true,
                                    icon = TimelineIcon.GenericConfirmation,
                                    title = "Test Success"
                                )
                            }
                        ))
                }
            }) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ConfirmDumpButton(
    text: String,
    description: String,
    action: () -> Unit,
    icon: ImageVector,
    enabled: Boolean = true,
) {
    var showConfirmationDialog by remember { mutableStateOf(false) }

    PebbleElevatedButton(
        onClick = { showConfirmationDialog = true },
        text = text,
        primaryColor = false,
        icon = icon,
        modifier = Modifier.padding(vertical = 5.dp),
        enabled = enabled,
    )

    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = { Text(text) },
            text = { Text(description) },
            confirmButton = {
                TextButton(onClick = {
                    action()
                    showConfirmationDialog = false
                }) { Text(text) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showConfirmationDialog = false
                }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun LanguagePacks(watch: PebbleDevice) {
    val languagePackInstalledAtConnectionTime = (watch as? ConnectedPebble.LanguageState)?.installedLanguagePack
    val languagePackRecentlyInstalledSinceConnection = ((watch as? ConnectedPebble.LanguageState)?.languagePackInstallState as? LanguagePackInstallState.Idle)?.successfullyInstalledLanguage
    val languagePackInstalled = languagePackRecentlyInstalledSinceConnection ?: languagePackInstalledAtConnectionTime?.let { "${it.isoLocal} (v${it.version})" }
    if (languagePackInstalled != null) {
        Text(
            text = "Language Pack: $languagePackInstalled",
            modifier = Modifier.padding(5.dp),
        )
    }

    if (watch is ConnectedPebbleDevice && watch.languagePackInstallState is LanguagePackInstallState.Idle) {
        var showLanguagePackDialog by remember { mutableStateOf(false) }

        PebbleElevatedButton(
            text = "Install Language Pack",
            primaryColor = true,
            onClick = { showLanguagePackDialog = true },
            icon = Icons.Default.Language,
            contentDescription = "Install Language Pack",
            modifier = Modifier.padding(5.dp),
        )

        if (showLanguagePackDialog) {
            val languagePackRepository: LanguagePackRepository = koinInject()
            val languagePacks by produceState<List<LanguagePack>>(emptyList()) {
                value = languagePackRepository.languagePacksForWatch(watch)
            }
            var selectedLanguagePack: LanguagePack? by remember { mutableStateOf(null) }
            AlertDialog(
                onDismissRequest = { showLanguagePackDialog = false },
                title = { Text("Language Packs") },
                text = {
                    LazyColumn {
                        items(languagePacks, key = { it.id }) { lp ->
                            val isSelected = selectedLanguagePack == lp
                            Text(
                                text = lp.displayName(),
                                modifier = Modifier.clickable {
                                    selectedLanguagePack = lp
                                }.border(
                                    width = 2.dp,
                                    color = if (isSelected) coreOrange else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                ).padding(9.dp),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val lp = selectedLanguagePack
                            if (lp == null) {
                                logger.e { "why is selectedLanguagePack null?" }
                            } else {
                                watch.installLanguagePack(lp.file, lp.displayName())
                            }
                            showLanguagePackDialog = false
                        },
                        enabled = selectedLanguagePack != null,
                    ) { Text("Install") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showLanguagePackDialog = false
                    }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun Nickname(watch: PebbleDevice, modifier: Modifier = Modifier) {
    if (watch !is KnownPebbleDevice) {
        return
    }
    Column(modifier = modifier) {
        val hasNickname = watch.nickname.isNotEmptyNullable()
        if (hasNickname) {
            Text("System name: ${watch.name}")
        }
        FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
            var editingNickname by remember { mutableStateOf(false) }
            if (editingNickname) {
                var nickname by remember { mutableStateOf(watch.nickname ?: "") }
                TextField(
                    value = nickname,
                    onValueChange = {
                        if (it.length <= 25) {
                            nickname = it
                        }
                    },
                    label = { Text("Nickname") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(
                    onClick = {
                        editingNickname = false
                        watch.setNickname(nickname)
                    },
                ) {
                    Icon(Icons.Default.Done, contentDescription = "Save")
                }
            } else {
                if (hasNickname) {
                    Text("Nickname: ${watch.nickname!!}")
                    IconButton(
                        onClick = { editingNickname = true },
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Nickname")
                    }
                    IconButton(
                        onClick = { watch.setNickname(null) },
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = "Remove Nickname")
                    }
                } else {
                    PebbleElevatedButton(
                        text = "Set Nickname",
                        primaryColor = true,
                        onClick = { editingNickname = true },
                        icon = Icons.Default.Edit,
                        contentDescription = "Set Nickname"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Screenshot(watch: PebbleDevice, scope: CoroutineScope) {
    if (watch !is ConnectedPebble.Screenshot) {
        return
    }
    val platform = koinInject<Platform>()
    if (watch !is ConnectedPebbleDevice) {
        return
    }

    var screenshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var takingScreenshot by remember { mutableStateOf(false) }
    fun takeScreenshot() {
        scope.launch {
            if (takingScreenshot) {
                return@launch
            }
            takingScreenshot = true
            screenshot = watch.takeScreenshot()
            takingScreenshot = false
            logger.v { "screenshot = $screenshot" }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        screenshot?.let {
            ElevatedCard(
                shape = CutCornerShape(0.dp),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 8.dp,
                ),
                modifier = Modifier.background(Color.Transparent).padding(bottom = 8.dp),
            ) {
                val height = 140.dp
                val width = height / it.height * it.width
                Image(
                    bitmap = it,
                    contentDescription = "Screenshot",
                    modifier = Modifier.height(height).width(width),
                )
            }
        }

        }

        FlowRow(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            PebbleElevatedButton(
                text = if (takingScreenshot) "Taking..." else "Screenshot",
                onClick = { takeScreenshot() },
                enabled = !takingScreenshot,
                icon = if (screenshot == null) Icons.Default.Image else Icons.Default.Refresh,
                primaryColor = true,
                modifier = Modifier.padding(5.dp),
            )
            
            if (screenshot != null) {
                val platformShareLauncher = koinInject<PlatformShareLauncher>()
                PebbleElevatedButton(
                    text = "Share",
                    onClick = { 
                        platformShareLauncher.shareImage(screenshot!!, "pebble_screenshot.png")
                    },
                    icon = Icons.Default.Share,
                    primaryColor = false,
                    modifier = Modifier.padding(5.dp),
                )
            }
        }
    }

@Composable
private fun DevConnection(
    watch: PebbleDevice,
    libPebble: LibPebble,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val config by libPebble.config.collectAsState()
    if (watch is ConnectedPebble.DevConnection && (watch !is ConnectedPebbleDeviceInRecovery || config.watchConfig.ignoreMissingPrf)) {
        val config by libPebble.config.collectAsState()
        val github = koinInject<Github>()
        val githubAccount by koinInject<GithubAccount>().loggedIn.collectAsState()
        val scope = rememberCoroutineScope()
        val uriHandler = LocalUriHandler.current
        val settings = koinInject<Settings>()

        fun login() {
            scope.launch {
                val uuid = Uuid.random()
                settings.putString(Github.STATE_SETTING_KEY, uuid.toString())
                val authUrl = github.getAuthorizationUrl(uuid.toString(), setOf("profile"))
                uriHandler.openUri(authUrl.toString())
            }
        }

        Box(modifier = modifier) {
            Column {
                val active by watch.devConnectionActive.collectAsState()
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dev Connection")
                    Spacer(Modifier.weight(1f))
                    if (githubAccount != null || config.watchConfig.lanDevConnection) {
                        Switch(
                            checked = active,
                            onCheckedChange = {
                                scope.launch {
                                    if (it) {
                                        watch.startDevConnection()
                                    } else {
                                        watch.stopDevConnection()
                                    }
                                }
                            }
                        )
                    } else {
                        Button(
                            onClick = ::login
                        ) {
                            Text("Login to GitHub")
                        }
                    }
                }
                if (active) {
                    if (config.watchConfig.lanDevConnection) {
                        LanDevConnectionDetail()
                    } else {
                        ProxyDevConnectionDetail(::login)
                    }
                }
            }
        }
    }
}

@Composable
private fun LanDevConnectionDetail() {
    val (v4, v6) = remember { getIPAddress() }
    ElevatedCard(
        modifier = Modifier.padding(5.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Warning",
                    modifier = Modifier.padding(4.dp)
                )
                Text(
                    "Watch exposed to local network",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (v4 != null || v6 != null) {
                Spacer(Modifier.height(8.dp))
                Text(buildAnnotatedString {
                    v4?.let {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            appendLine("IPv4:")
                        }
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                            append(v4)
                        } //TODO: fix text selection error after first line
                    }
                    v6?.let {
                        if (v4 != null) append("\n")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            appendLine("IPv6:")
                        }
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                            append(v6)
                        }
                    }
                })
            }
        }
    }
}

@Composable
private fun ProxyDevConnectionDetail(login: () -> Unit) {
    val github = koinInject<Github>()
    val githubAccount by koinInject<GithubAccount>().loggedIn.collectAsState()
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf<GithubUser?>(null) }
    LaunchedEffect(githubAccount) {
        user = if (githubAccount != null) {
            github.user()
        } else {
            null
        }
    }

    ElevatedCard(
        modifier = Modifier.padding(5.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Warning",
                    modifier = Modifier.padding(4.dp)
                )
                Text(
                    "Watch exposed to Cloudpebble Proxy",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (user != null) {
                Spacer(Modifier.height(8.dp))
                Text("Logged in via GitHub as ${user!!.login}")
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = login,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Change User")
                }
            }
        }
    }
}
