package coredevices.pebble.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import coredevices.pebble.services.LanguagePack
import coredevices.pebble.services.LanguagePackGroup
import coredevices.pebble.services.LanguagePackRepository
import coredevices.pebble.services.canContribute
import coredevices.pebble.services.canonicalLocale
import coredevices.pebble.services.description
import coredevices.pebble.services.displayName
import coredevices.pebble.services.groupLanguagePacks
import coredevices.ui.CoreLinearProgressIndicator
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.endpointmanager.LanguagePackInstallState
import io.rebble.libpebblecommon.connection.endpointmanager.WatchLanguagePack
import io.rebble.libpebblecommon.connection.endpointmanager.installing
import io.rebble.libpebblecommon.packets.MAX_LANGUAGE_PACKS
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import theme.coreOrange
import kotlin.coroutines.cancellation.CancellationException
import coreapp.pebble.generated.resources.Res
import coreapp.pebble.generated.resources.langpack_error_read_installed
import coreapp.pebble.generated.resources.langpack_error_refresh_suffix
import coreapp.pebble.generated.resources.langpack_error_change_language
import coreapp.pebble.generated.resources.langpack_watch_language
import coreapp.pebble.generated.resources.langpack_english
import coreapp.pebble.generated.resources.langpack_built_in
import coreapp.pebble.generated.resources.langpack_status_installing
import coreapp.pebble.generated.resources.langpack_status_changing
import coreapp.pebble.generated.resources.langpack_status_loading
import coreapp.pebble.generated.resources.langpack_status_count
import coreapp.pebble.generated.resources.langpack_add_language_pack
import coreapp.pebble.generated.resources.langpack_remove_to_add
import coreapp.pebble.generated.resources.langpack_remove_title
import coreapp.pebble.generated.resources.langpack_remove_active_message
import coreapp.pebble.generated.resources.langpack_remove_message
import coreapp.pebble.generated.resources.langpack_error_remove
import coreapp.pebble.generated.resources.langpack_removing
import coreapp.pebble.generated.resources.langpack_unknown
import coreapp.pebble.generated.resources.langpack_unreadable
import coreapp.pebble.generated.resources.langpack_translation_and_fonts
import coreapp.pebble.generated.resources.langpack_adds_characters
import coreapp.pebble.generated.resources.langpack_remove_to_free_slot
import coreapp.pebble.generated.resources.langpack_remove_content_description
import coreapp.pebble.generated.resources.langpack_add_title
import coreapp.pebble.generated.resources.langpack_title
import coreapp.pebble.generated.resources.langpack_error_load
import coreapp.pebble.generated.resources.langpack_loading
import coreapp.pebble.generated.resources.langpack_use_as_watch_language
import coreapp.pebble.generated.resources.langpack_help_translate
import coreapp.pebble.generated.resources.langpack_error_timeout
import coreapp.pebble.generated.resources.langpack_error_download
import coreapp.pebble.generated.resources.langpack_downloading
import coreapp.util.generated.resources.Res as UtilRes
import coreapp.util.generated.resources.add
import coreapp.util.generated.resources.cancel
import coreapp.util.generated.resources.done
import coreapp.util.generated.resources.install
import coreapp.util.generated.resources.refresh
import coreapp.util.generated.resources.remove
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private val logger = Logger.withTag("LanguagePackDialogs")

@Composable
fun LanguageDialog(watch: ConnectedPebbleDevice, onDismissRequest: () -> Unit) {
    if (ProtocolCapsFlag.SupportsMultipleLanguagePacks in watch.watchInfo.capabilities) {
        LanguagePacksDialog(watch, onDismissRequest)
    } else {
        LanguagePackPickerDialog(
            watch = watch,
            addToExisting = false,
            installedLocales = emptySet(),
            onInstallStarted = onDismissRequest,
            onDismissRequest = onDismissRequest,
        )
    }
}

@Composable
private fun LanguagePacksDialog(watch: ConnectedPebbleDevice, onDismissRequest: () -> Unit) {
    val scope = rememberCoroutineScope()
    val currentWatch by rememberUpdatedState(watch)
    var installedPacks: List<WatchLanguagePack>? by remember(watch.identifier) { mutableStateOf(null) }
    var loading by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var packToRemove: WatchLanguagePack? by remember { mutableStateOf(null) }
    var showPicker by remember { mutableStateOf(false) }
    val installingName = watch.languagePackInstallState.installing()
    LaunchedEffect(watch.identifier, refresh, installingName != null) {
        if (installingName != null) return@LaunchedEffect
        loading = true
        error = null
        try {
            installedPacks = currentWatch.listLanguagePacks()
        } catch (e: Exception) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            installedPacks = null
            error = getString(Res.string.langpack_error_read_installed)
        } finally {
            loading = false
        }
    }
    val talkingToWatch = removing || selecting
    val busy = talkingToWatch || installingName != null
    val enabled = !busy && !loading && installedPacks != null

    fun runOp(setInProgress: (Boolean) -> Unit, failureMessage: StringResource, op: suspend (ConnectedPebbleDevice) -> Boolean) {
        setInProgress(true)
        error = null
        scope.launch {
            try {
                val succeeded = op(currentWatch)
                installedPacks = currentWatch.listLanguagePacks()
                if (!succeeded) error = getString(failureMessage)
            } catch (e: Exception) {
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                installedPacks = null
                error = getString(Res.string.langpack_error_refresh_suffix, getString(failureMessage))
            } finally {
                setInProgress(false)
            }
        }
    }

    fun select(pack: WatchLanguagePack?) = runOp({ selecting = it }, Res.string.langpack_error_change_language) {
        it.setActiveLanguage(pack)
    }

    val packs = installedPacks.orEmpty()
    AlertDialog(
        onDismissRequest = { if (!talkingToWatch) onDismissRequest() },
        title = { Text(stringResource(Res.string.langpack_watch_language)) },
        text = {
            LazyColumn {
                if (installedPacks != null) {
                    item {
                        LanguageOption(
                            title = stringResource(Res.string.langpack_english),
                            subtitle = stringResource(Res.string.langpack_built_in),
                            selected = packs.none { it.isActive },
                            enabled = enabled,
                            onSelect = { select(null) },
                        )
                    }
                    items(packs, key = { it.slot }) { pack ->
                        InstalledPackRow(
                            pack = pack,
                            enabled = enabled,
                            onSelect = { select(pack) },
                            onRemove = { packToRemove = pack },
                        )
                    }
                }
                item {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        val status = when {
                            installingName != null -> stringResource(Res.string.langpack_status_installing, installingName)
                            selecting -> stringResource(Res.string.langpack_status_changing)
                            loading -> stringResource(Res.string.langpack_status_loading)
                            installedPacks != null -> stringResource(Res.string.langpack_status_count, packs.size, MAX_LANGUAGE_PACKS)
                            else -> null
                        }
                        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        when (val installState = watch.languagePackInstallState) {
                            is LanguagePackInstallState.Downloading -> CoreLinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            )
                            is LanguagePackInstallState.Installing -> {
                                val progress by installState.progress.collectAsState()
                                CoreLinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                )
                            }
                            is LanguagePackInstallState.Idle -> Unit
                        }
                        error?.let { message ->
                            Text(message, color = MaterialTheme.colorScheme.error)
                            TextButton(enabled = !busy && !loading, onClick = { refresh++ }) { Text(stringResource(UtilRes.string.refresh)) }
                        }
                        OutlinedButton(
                            enabled = enabled && packs.size < MAX_LANGUAGE_PACKS,
                            onClick = { showPicker = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text(stringResource(Res.string.langpack_add_language_pack)) }
                        if (installedPacks != null && packs.size >= MAX_LANGUAGE_PACKS) {
                            Text(stringResource(Res.string.langpack_remove_to_add), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest, enabled = !talkingToWatch) { Text(stringResource(UtilRes.string.done)) }
        },
    )
    if (showPicker) {
        LanguagePackPickerDialog(
            watch = watch,
            addToExisting = true,
            installedLocales = packs.filter { it.hasTranslation }.map { canonicalLocale(it.locale) }.toSet(),
            onInstallStarted = { showPicker = false },
            onDismissRequest = { showPicker = false },
        )
    }
    packToRemove?.let { pack ->
        AlertDialog(
            onDismissRequest = { if (!removing) packToRemove = null },
            title = { Text(stringResource(Res.string.langpack_remove_title, pack.label())) },
            text = {
                Text(
                    if (pack.isActive) stringResource(Res.string.langpack_remove_active_message)
                    else stringResource(Res.string.langpack_remove_message)
                )
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    runOp(
                        setInProgress = { inProgress ->
                            removing = inProgress
                            if (!inProgress) packToRemove = null
                        },
                        failureMessage = Res.string.langpack_error_remove,
                    ) { it.removeLanguagePack(pack) }
                }) { Text(if (removing) stringResource(Res.string.langpack_removing) else stringResource(UtilRes.string.remove)) }
            },
            dismissButton = { TextButton(enabled = !removing, onClick = { packToRemove = null }) { Text(stringResource(UtilRes.string.cancel)) } },
        )
    }
}

@Composable
private fun WatchLanguagePack.label(): String = when {
    hasTranslation -> name
    readable -> stringResource(Res.string.langpack_unknown)
    else -> stringResource(Res.string.langpack_unreadable)
}

@Composable
private fun InstalledPackRow(
    pack: WatchLanguagePack,
    enabled: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            if (pack.hasTranslation) {
                LanguageOption(
                    title = pack.name,
                    subtitle = stringResource(Res.string.langpack_translation_and_fonts),
                    selected = pack.isActive,
                    enabled = enabled,
                    onSelect = onSelect,
                )
            } else {
                LanguageOption(
                    title = pack.label(),
                    subtitle = if (pack.readable) stringResource(Res.string.langpack_adds_characters) else stringResource(Res.string.langpack_remove_to_free_slot),
                    selected = null,
                    enabled = enabled,
                    onSelect = {},
                )
            }
        }
        IconButton(enabled = enabled, onClick = onRemove) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.langpack_remove_content_description, pack.label()))
        }
    }
}

/** A null [selected] reserves the radio's space for rows that can't be chosen. */
@Composable
private fun LanguageOption(
    title: String,
    subtitle: String,
    selected: Boolean?,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled && selected != null, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected != null) {
            RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Column {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LanguagePackPickerDialog(
    watch: ConnectedPebbleDevice,
    addToExisting: Boolean,
    installedLocales: Set<String>,
    onInstallStarted: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val languagePackRepository: LanguagePackRepository = koinInject()
    val manifest by languagePackRepository.manifest.collectAsState()
    val languagePacks = remember(manifest, watch.identifier) {
        manifest?.let { languagePackRepository.languagePacksForWatch(it, watch) }
    }
    var manifestFetchFailed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (languagePacks == null) manifestFetchFailed = !languagePackRepository.syncManifest()
    }
    val groups = remember(languagePacks, installedLocales) {
        groupLanguagePacks(languagePacks.orEmpty().filter { canonicalLocale(it.isoLocal) !in installedLocales })
    }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var selected: LanguagePack? by remember { mutableStateOf(null) }
    var useAsWatchLanguage by remember { mutableStateOf(true) }
    var preparing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(groups) {
        selected = groups.flatMap { it.second }.firstOrNull { it.id == selected?.id }
    }
    val busy = preparing || watch.languagePackInstallState.installing() != null
    val selectedGroup = selected?.let { pack -> groups.firstOrNull { (_, packs) -> pack in packs }?.first }
    AlertDialog(
        onDismissRequest = { if (!preparing) onDismissRequest() },
        title = { Text(if (addToExisting) stringResource(Res.string.langpack_add_title) else stringResource(Res.string.langpack_title)) },
        text = {
            Column {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (languagePacks == null) {
                    if (manifestFetchFailed) {
                        Text(stringResource(Res.string.langpack_error_load), color = MaterialTheme.colorScheme.error)
                    } else {
                        Text(stringResource(Res.string.langpack_loading))
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    groups.forEach { (group, packs) ->
                        item(key = group.name) {
                            Text(
                                stringResource(group.title),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(packs, key = { it.id }) { lp ->
                            Column(
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                    selected = lp
                                    error = null
                                }.border(
                                    width = 2.dp,
                                    color = if (selected == lp) coreOrange else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                ).padding(9.dp),
                            ) {
                                Text(lp.displayName())
                                Text(lp.description(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (addToExisting && selectedGroup != null && selectedGroup != LanguagePackGroup.FontPacks) {
                    Row(
                        modifier = Modifier.clickable(enabled = !busy) { useAsWatchLanguage = !useAsWatchLanguage },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = useAsWatchLanguage, onCheckedChange = { useAsWatchLanguage = it }, enabled = !busy)
                        Text(stringResource(Res.string.langpack_use_as_watch_language))
                    }
                }
                selected?.takeIf { it.canContribute() }?.published?.let { published ->
                    TextButton(onClick = { uriHandler.openUri(published.translationUrl) }) {
                        Text(stringResource(Res.string.langpack_help_translate))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val lp = selected ?: return@TextButton
                    val activate = useAsWatchLanguage && selectedGroup != LanguagePackGroup.FontPacks
                    preparing = true
                    scope.launch {
                        try {
                            val path = languagePackRepository.prepareDownload(lp)
                            watch.installLanguagePack(path, lp.displayName(), activate)
                            onInstallStarted()
                        } catch (e: TimeoutCancellationException) {
                            error = getString(Res.string.langpack_error_timeout)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logger.w(e) { "Language pack download failed" }
                            error = getString(Res.string.langpack_error_download)
                        } finally {
                            preparing = false
                        }
                    }
                },
                enabled = selected != null && !busy,
            ) { Text(if (preparing) stringResource(Res.string.langpack_downloading) else if (addToExisting) stringResource(UtilRes.string.add) else stringResource(UtilRes.string.install)) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest, enabled = !preparing) { Text(stringResource(UtilRes.string.cancel)) }
        },
    )
}
