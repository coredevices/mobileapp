package coredevices.ring.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.settings_speech_delete_model
import coreapp.ring.generated.resources.settings_speech_download_lite
import coreapp.ring.generated.resources.settings_speech_download_lite_message
import coreapp.ring.generated.resources.settings_speech_download_message
import coreapp.ring.generated.resources.settings_speech_download_offline
import coreapp.ring.generated.resources.settings_speech_download_required
import coreapp.ring.generated.resources.settings_speech_downloading
import coreapp.ring.generated.resources.settings_speech_engine
import coreapp.ring.generated.resources.settings_speech_engine_download_detail
import coreapp.ring.generated.resources.settings_speech_engine_local_first
import coreapp.ring.generated.resources.settings_speech_engine_local_only
import coreapp.ring.generated.resources.settings_speech_engine_local_only_detail
import coreapp.ring.generated.resources.settings_speech_engine_platform
import coreapp.ring.generated.resources.settings_speech_engine_platform_detail
import coreapp.ring.generated.resources.settings_speech_engine_remote_first
import coreapp.ring.generated.resources.settings_speech_engine_remote_only
import coreapp.ring.generated.resources.settings_speech_engine_remote_only_detail
import coreapp.ring.generated.resources.settings_speech_engine_sheet_subtitle
import coreapp.ring.generated.resources.settings_speech_engine_unsupported
import coreapp.ring.generated.resources.settings_speech_language
import coreapp.ring.generated.resources.settings_speech_language_not_selectable
import coreapp.ring.generated.resources.settings_speech_language_sheet_subtitle
import coreapp.ring.generated.resources.settings_speech_model
import coreapp.ring.generated.resources.settings_speech_model_download_size
import coreapp.ring.generated.resources.settings_speech_model_downloaded
import coreapp.ring.generated.resources.settings_speech_model_sheet_subtitle
import coreapp.ring.generated.resources.settings_speech_search_languages
import coreapp.ring.generated.resources.settings_speech_sign_in_required
import coreapp.ring.generated.resources.settings_speech_wispr
import coreapp.util.generated.resources.automatic
import coreapp.util.generated.resources.cancel
import coredevices.util.models.ModelDownloadStatus
import coredevices.util.models.inProgressSlug
import coredevices.util.models.ModelInfo
import coredevices.util.models.ModelManager
import coredevices.util.models.RecommendedModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TextButton
import coredevices.ui.M3Dialog
import coredevices.ring.ui.theme.IndexTheme
import coredevices.util.models.CactusSTTMode
import coredevices.util.transcription.PlatformSpeechRecognizer
import coredevices.util.transcription.SpeechModelAvailability
import coredevices.util.transcription.SpokenLanguageOptions
import coredevices.util.transcription.platformModelNeedsDownload
import coredevices.util.transcription.platformModelState
import coredevices.util.transcription.spokenLanguageLabel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import coreapp.util.generated.resources.Res as UtilRes

/** Cloud transcription is provided by Wispr Flow; the row is informational. */
private const val WISPR_URL = "https://wispr.ai/"

/** The engines the Index pipeline honours. Rebble modes are routed by STTRouter for the watch
 *  and never reach the ring's transcription service, so they are not offered here. */
internal val indexSpeechModes: List<CactusSTTMode> = listOf(
    CactusSTTMode.PlatformOnly,
    CactusSTTMode.RemoteOnly,
    CactusSTTMode.RemoteFirst,
    CactusSTTMode.LocalFirst,
    CactusSTTMode.LocalOnly,
)

@Composable
internal fun CactusSTTMode.speechEngineName(): String = when (this) {
    CactusSTTMode.PlatformOnly -> stringResource(Res.string.settings_speech_engine_platform)
    CactusSTTMode.RemoteOnly -> stringResource(Res.string.settings_speech_engine_remote_only)
    CactusSTTMode.RemoteFirst -> stringResource(Res.string.settings_speech_engine_remote_first)
    CactusSTTMode.LocalFirst -> stringResource(Res.string.settings_speech_engine_local_first)
    CactusSTTMode.LocalOnly -> stringResource(Res.string.settings_speech_engine_local_only)
    CactusSTTMode.RebbleOnly, CactusSTTMode.RebbleFirst, CactusSTTMode.RebbleFallback -> name
}

@Composable
internal fun CactusSTTMode.speechEngineDetail(): String = when (this) {
    CactusSTTMode.PlatformOnly -> stringResource(Res.string.settings_speech_engine_platform_detail)
    CactusSTTMode.RemoteOnly -> stringResource(Res.string.settings_speech_engine_remote_only_detail)
    CactusSTTMode.RemoteFirst -> stringResource(Res.string.settings_speech_engine_download_detail)
    CactusSTTMode.LocalFirst -> stringResource(Res.string.settings_speech_engine_download_detail)
    CactusSTTMode.LocalOnly -> stringResource(Res.string.settings_speech_engine_local_only_detail)
    CactusSTTMode.RebbleOnly, CactusSTTMode.RebbleFirst, CactusSTTMode.RebbleFallback -> ""
}

/** Cloud transcription runs against the Core account, matching the watch settings screen's guard. */
internal fun CactusSTTMode.needsSignIn(): Boolean =
    this != CactusSTTMode.LocalOnly && this != CactusSTTMode.PlatformOnly

internal fun CactusSTTMode.needsLocalModel(): Boolean =
    this == CactusSTTMode.LocalOnly ||
        this == CactusSTTMode.LocalFirst ||
        this == CactusSTTMode.RemoteFirst

/** Hardware can't run this engine, so picking it would never work. */
internal fun speechEngineBlockedReason(mode: CactusSTTMode, onDeviceSupported: Boolean): StringResource? =
    if (mode != CactusSTTMode.RemoteOnly && mode != CactusSTTMode.PlatformOnly && !onDeviceSupported) {
        Res.string.settings_speech_engine_unsupported
    } else {
        null
    }

/** Selectable, but the speech model has to come down first. */
internal fun speechEngineNeedsDownload(
    mode: CactusSTTMode,
    onDeviceSupported: Boolean,
    hasOfflineModels: Boolean,
    platformModelNeedsDownload: Boolean,
): Boolean = when {
    mode == CactusSTTMode.PlatformOnly -> platformModelNeedsDownload
    else -> mode.needsLocalModel() && onDeviceSupported && !hasOfflineModels
}

internal fun platformModelSubtitle(
    spokenLanguage: String?,
    availability: SpeechModelAvailability,
    download: ModelDownloadStatus,
): String = "${spokenLanguageLabel(spokenLanguage)} · ${platformModelState(availability, download)}"

@Composable
internal fun speechModelDetail(model: ModelInfo, downloaded: Boolean): String =
    listOfNotNull(
        model.intendedTask,
        if (downloaded) {
            stringResource(Res.string.settings_speech_model_downloaded)
        } else {
            stringResource(Res.string.settings_speech_model_download_size, model.sizeInMB)
        },
    ).joinToString(" · ")

/** The language can only be chosen when the model doing the transcribing understands more than one. */
internal fun spokenLanguageSelectable(mode: CactusSTTMode, model: ModelInfo?): Boolean =
    !mode.needsLocalModel() || model == null || model.supportsMultiLanguage

@Composable
internal fun spokenLanguageRowSubtitle(spokenLanguage: String?, selectable: Boolean): String =
    if (selectable) {
        spokenLanguageLabel(spokenLanguage)
    } else {
        stringResource(Res.string.settings_speech_language_not_selectable)
    }

@Composable
fun SpeechSection(
    mode: CactusSTTMode,
    spokenLanguage: String?,
    selectedModel: String?,
    onDeviceSupported: Boolean,
    platformSttAvailable: Boolean,
    hasOfflineModels: Boolean,
    signedIn: Boolean,
    onSelectMode: (CactusSTTMode) -> Unit,
    onSelectModeWithModel: (CactusSTTMode, String) -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectLanguage: (String?) -> Unit,
    onRequireSignIn: () -> Unit,
    /** Opens the routed speech model download dialog for the configured engine. */
    onShowModelDownload: () -> Unit,
) {
    var showEngineSheet by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var pendingDownloadMode by remember { mutableStateOf<CactusSTTMode?>(null) }
    var pendingDownloadModel by remember { mutableStateOf<ModelInfo?>(null) }
    val modelManager = koinInject<ModelManager>()
    val platformSpeechRecognizer = koinInject<PlatformSpeechRecognizer>()
    val scope = rememberCoroutineScope()
    val recommendedModel = remember { modelManager.getRecommendedSTTModel() }
    val currentModel = selectedModel ?: recommendedModel.modelSlug
    val downloadStatus by modelManager.modelDownloadStatus.collectAsState()
    val selectableModels by produceState(emptyList<ModelInfo>()) {
        value = modelManager.getSelectableSTTModels()
    }
    val currentModelInfo = selectableModels.firstOrNull { it.slug == currentModel }
    val languageSelectable = spokenLanguageSelectable(mode, currentModelInfo)
    var deletions by remember { mutableStateOf(0) }
    val downloadedSlugs by produceState(emptyList<String>(), downloadStatus, deletions) {
        value = withContext(Dispatchers.Default) { modelManager.getDownloadedSTTModelSlugs() }
    }
    // The parent's [hasOfflineModels] can't see a delete made from the model sheet.
    val localModelReady = hasOfflineModels && (deletions == 0 || currentModel in downloadedSlugs)
    val platformDownloadStatus by platformSpeechRecognizer.downloadStatus.collectAsState()
    val platformModelAvailability by produceState(
        SpeechModelAvailability.Unsupported,
        spokenLanguage,
        platformDownloadStatus,
        platformSttAvailable,
    ) {
        value = if (platformSttAvailable) {
            withContext(Dispatchers.Default) { platformSpeechRecognizer.modelAvailability(spokenLanguage) }
        } else {
            SpeechModelAvailability.Unsupported
        }
    }
    val platformNeedsDownload = platformModelNeedsDownload(platformModelAvailability, platformDownloadStatus)

    SettingsRow(
        title = stringResource(Res.string.settings_speech_engine),
        subtitle = mode.speechEngineName(),
        onClick = { showEngineSheet = true },
    )
    SettingsRow(
        title = stringResource(Res.string.settings_speech_language),
        subtitle = spokenLanguageRowSubtitle(spokenLanguage, languageSelectable),
        enabled = languageSelectable,
        onClick = { showLanguageSheet = true },
    )
    if (mode.needsLocalModel() && onDeviceSupported) {
        SettingsRow(
            title = stringResource(Res.string.settings_speech_model),
            subtitle = currentModel,
            onClick = { showModelSheet = true },
        )
    }
    if (mode == CactusSTTMode.PlatformOnly) {
        SettingsRow(
            title = stringResource(Res.string.settings_speech_model),
            subtitle = platformModelSubtitle(spokenLanguage, platformModelAvailability, platformDownloadStatus),
            enabled = platformNeedsDownload,
            onClick = onShowModelDownload,
        )
    }
    val uriHandler = LocalUriHandler.current
    Text(
        stringResource(Res.string.settings_speech_wispr),
        fontSize = 12.sp,
        color = IndexTheme.colors.onSurfaceVariant,
        modifier = Modifier
            .clickable { uriHandler.openUrlSafely(WISPR_URL) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )

    if (showEngineSheet) {
        SpeechEngineSheet(
            current = mode,
            onDeviceSupported = onDeviceSupported,
            platformSttAvailable = platformSttAvailable,
            hasOfflineModels = localModelReady,
            platformModelNeedsDownload = platformNeedsDownload,
            signedIn = signedIn,
            onSelect = { selected, needsDownload ->
                showEngineSheet = false
                when {
                    selected.needsSignIn() && !signedIn -> onRequireSignIn()
                    selected == CactusSTTMode.PlatformOnly -> {
                        onSelectMode(selected)
                        if (needsDownload) onShowModelDownload()
                    }
                    needsDownload -> pendingDownloadMode = selected
                    else -> onSelectMode(selected)
                }
            },
            onDismiss = { showEngineSheet = false },
        )
    }
    if (showModelSheet) {
        SpeechModelSheet(
            current = currentModel,
            models = selectableModels,
            downloadedSlugs = downloadedSlugs,
            downloadStatus = downloadStatus,
            onSelect = { info ->
                showModelSheet = false
                if (info.slug in downloadedSlugs) onSelectModel(info.slug)
                else pendingDownloadModel = info
            },
            onDelete = { info ->
                scope.launch {
                    withContext(Dispatchers.Default) { modelManager.deleteModel(info.slug) }
                    deletions++
                }
            },
            onDismiss = { showModelSheet = false },
        )
    }
    pendingDownloadModel?.let { info ->
        SpeechModelDownloadDialog(
            isLite = false,
            downloadSizeInMb = info.sizeInMB,
            onDownload = {
                if (modelManager.downloadSTTModel(info, allowMetered = true)) {
                    onSelectModel(info.slug)
                }
                pendingDownloadModel = null
            },
            onDismiss = { pendingDownloadModel = null },
        )
    }
    pendingDownloadMode?.let { pending ->
        val model by produceState<ModelInfo?>(null, pending, currentModel) {
            value = withContext(Dispatchers.Default) {
                modelManager.getAvailableSTTModels().firstOrNull { it.slug == currentModel }
            }
            if (value == null) pendingDownloadMode = null
        }
        model?.let { info ->
            SpeechModelDownloadDialog(
                isLite = recommendedModel is RecommendedModel.Lite,
                downloadSizeInMb = info.sizeInMB,
                onDownload = {
                    scope.launch {
                        if (modelManager.downloadSTTModel(info, allowMetered = true)) {
                            onSelectModeWithModel(pending, info.slug)
                        }
                        pendingDownloadMode = null
                    }
                },
                onDismiss = { pendingDownloadMode = null },
            )
        }
    }
    if (showLanguageSheet) {
        SpokenLanguageSheet(
            current = spokenLanguage,
            onSelect = { language ->
                onSelectLanguage(language)
                showLanguageSheet = false
                if (mode == CactusSTTMode.PlatformOnly) {
                    scope.launch {
                        val availability = platformSpeechRecognizer.modelAvailability(language)
                        if (platformModelNeedsDownload(availability, platformDownloadStatus)) {
                            onShowModelDownload()
                        }
                    }
                }
            },
            onDismiss = { showLanguageSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeechEngineSheet(
    current: CactusSTTMode,
    onDeviceSupported: Boolean,
    platformSttAvailable: Boolean,
    hasOfflineModels: Boolean,
    platformModelNeedsDownload: Boolean,
    signedIn: Boolean,
    onSelect: (CactusSTTMode, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = IndexTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.sheetSurface) {
        Column(modifier = Modifier.padding(bottom = 28.dp)) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                Text(
                    stringResource(Res.string.settings_speech_engine),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
                Text(
                    stringResource(Res.string.settings_speech_engine_sheet_subtitle),
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            indexSpeechModes.filter {
                it != CactusSTTMode.PlatformOnly || platformSttAvailable
            }.forEach { mode ->
                val selected = mode == current
                val blocked = speechEngineBlockedReason(mode, onDeviceSupported)
                val needsDownload = speechEngineNeedsDownload(
                    mode = mode,
                    onDeviceSupported = onDeviceSupported,
                    hasOfflineModels = hasOfflineModels,
                    platformModelNeedsDownload = platformModelNeedsDownload,
                )
                val reason = blocked?.let { stringResource(it) }
                    ?: stringResource(Res.string.settings_speech_sign_in_required)
                        .takeIf { !signedIn && mode.needsSignIn() }
                val selectable = blocked == null || selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .selectedSheetRowBackground(selected)
                        .clickable(enabled = selectable) { onSelect(mode, needsDownload) }
                        .alpha(if (selectable) 1f else 0.38f)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        when {
                            mode == CactusSTTMode.RemoteOnly -> Icons.Default.Cloud
                            mode == CactusSTTMode.LocalOnly ||
                                mode == CactusSTTMode.PlatformOnly -> Icons.Default.Smartphone
                            else -> Icons.Default.CloudSync
                        },
                        contentDescription = null,
                        tint = if (selected) colors.primary else colors.outline,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(mode.speechEngineName(), fontSize = 15.sp, color = colors.onSurface)
                        Text(
                            reason ?: mode.speechEngineDetail(),
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    if (selected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeechModelSheet(
    current: String,
    models: List<ModelInfo>,
    downloadedSlugs: List<String>,
    downloadStatus: ModelDownloadStatus,
    onSelect: (ModelInfo) -> Unit,
    onDelete: (ModelInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = IndexTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.sheetSurface) {
        Column(modifier = Modifier.padding(bottom = 28.dp)) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                Text(
                    stringResource(Res.string.settings_speech_model),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
                Text(
                    stringResource(Res.string.settings_speech_model_sheet_subtitle),
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            models.forEach { info ->
                val selected = info.slug == current
                val downloaded = info.slug in downloadedSlugs
                val downloading =
                    downloadStatus.inProgressSlug == info.slug
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .selectedSheetRowBackground(selected)
                        .clickable(enabled = !downloading) { onSelect(info) }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(info.slug, fontSize = 15.sp, color = colors.onSurface)
                        Text(
                            if (downloading) stringResource(Res.string.settings_speech_downloading) else speechModelDetail(info, downloaded),
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    when {
                        downloading -> CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.primary,
                        )
                        !downloaded -> Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = colors.outline,
                            modifier = Modifier.size(18.dp),
                        )
                        else -> {
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(onClick = { onDelete(info) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(Res.string.settings_speech_delete_model, info.slug),
                                    tint = colors.error,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpokenLanguageSheet(
    current: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = IndexTheme.colors
    var query by remember { mutableStateOf("") }
    val matches = remember(query) {
        SpokenLanguageOptions.filter { it.second.contains(query, ignoreCase = true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.sheetSurface) {
        Column(modifier = Modifier.padding(bottom = 28.dp)) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                Text(
                    stringResource(Res.string.settings_speech_language),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
                Text(
                    stringResource(Res.string.settings_speech_language_sheet_subtitle),
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.settings_speech_search_languages)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                item {
                    SpokenLanguageRow(
                        label = stringResource(UtilRes.string.automatic),
                        selected = current == null,
                        onClick = { onSelect(null) },
                    )
                }
                items(matches) { (code, label) ->
                    SpokenLanguageRow(
                        label = label,
                        selected = current == code,
                        onClick = { onSelect(code) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SpokenLanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = IndexTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .selectedSheetRowBackground(selected)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, color = colors.onSurface, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SpeechModelDownloadDialog(
    isLite: Boolean,
    downloadSizeInMb: Int,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    M3Dialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_speech_download_required)) },
        buttons = {
            TextButton(onClick = onDismiss) { Text(stringResource(UtilRes.string.cancel)) }
            TextButton(onClick = onDownload) {
                Text(
                    stringResource(
                        if (isLite) Res.string.settings_speech_download_lite else Res.string.settings_speech_download_offline,
                        downloadSizeInMb,
                    )
                )
            }
        },
    ) {
        Text(
            listOfNotNull(
                stringResource(Res.string.settings_speech_download_message),
                stringResource(Res.string.settings_speech_download_lite_message).takeIf { isLite },
            ).joinToString(" "),
            fontSize = 14.sp,
            color = IndexTheme.colors.onSurfaceVariant,
        )
    }
}
