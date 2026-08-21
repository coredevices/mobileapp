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
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
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
import coredevices.util.transcription.SpokenLanguageOptions

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

internal fun CactusSTTMode.speechEngineName(): String = when (this) {
    CactusSTTMode.PlatformOnly -> "iOS Speech Recognition"
    CactusSTTMode.RemoteOnly -> "Cloud only"
    CactusSTTMode.RemoteFirst -> "Cloud, with local fallback (Recommended)"
    CactusSTTMode.LocalFirst -> "Local, cloud fallback"
    CactusSTTMode.LocalOnly -> "Local only"
    CactusSTTMode.RebbleOnly, CactusSTTMode.RebbleFirst, CactusSTTMode.RebbleFallback -> name
}

internal fun CactusSTTMode.speechEngineDetail(): String = when (this) {
    CactusSTTMode.PlatformOnly -> "Private, stays on this phone"
    CactusSTTMode.RemoteOnly -> "Best performance, requires connection"
    CactusSTTMode.RemoteFirst -> "Requires 400MB download"
    CactusSTTMode.LocalFirst -> "Requires 400MB download"
    CactusSTTMode.LocalOnly -> "Complete privacy, requires 400MB download"
    CactusSTTMode.RebbleOnly, CactusSTTMode.RebbleFirst, CactusSTTMode.RebbleFallback -> ""
}

/** Cloud transcription runs against the Core account, matching the watch settings screen's guard. */
internal fun CactusSTTMode.needsSignIn(): Boolean =
    this != CactusSTTMode.LocalOnly && this != CactusSTTMode.PlatformOnly

private fun CactusSTTMode.needsLocalModel(): Boolean =
    this == CactusSTTMode.LocalOnly ||
        this == CactusSTTMode.LocalFirst ||
        this == CactusSTTMode.RemoteFirst

/** Hardware can't run this engine, so picking it would never work. */
internal fun speechEngineBlockedReason(mode: CactusSTTMode, onDeviceSupported: Boolean): String? =
    if (mode != CactusSTTMode.RemoteOnly && mode != CactusSTTMode.PlatformOnly && !onDeviceSupported) {
        "This device doesn't support local speech recognition"
    } else {
        null
    }

/** Selectable, but the speech model has to come down first. */
internal fun speechEngineNeedsDownload(
    mode: CactusSTTMode,
    onDeviceSupported: Boolean,
    hasOfflineModels: Boolean,
): Boolean = mode.needsLocalModel() && onDeviceSupported && !hasOfflineModels

/** [spokenLanguage] is an ISO code, or null for automatic detection. */
internal fun spokenLanguageLabel(spokenLanguage: String?): String =
    spokenLanguage?.let { code ->
        SpokenLanguageOptions.firstOrNull { it.first == code }?.second ?: code
    } ?: "Automatic"

@Composable
fun SpeechSection(
    mode: CactusSTTMode,
    spokenLanguage: String?,
    onDeviceSupported: Boolean,
    platformSttAvailable: Boolean,
    hasOfflineModels: Boolean,
    signedIn: Boolean,
    onSelectMode: (CactusSTTMode) -> Unit,
    onSelectModeWithModel: (CactusSTTMode, String) -> Unit,
    onSelectLanguage: (String?) -> Unit,
    onRequireSignIn: () -> Unit,
) {
    var showEngineSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var pendingDownloadMode by remember { mutableStateOf<CactusSTTMode?>(null) }
    val modelManager = koinInject<ModelManager>()
    val scope = rememberCoroutineScope()

    SettingsRow(
        title = "Speech Engine",
        subtitle = mode.speechEngineName(),
        onClick = { showEngineSheet = true },
    )
    SettingsRow(
        title = "Spoken Language",
        subtitle = spokenLanguageLabel(spokenLanguage),
        onClick = { showLanguageSheet = true },
    )
    val uriHandler = LocalUriHandler.current
    Text(
        "Cloud speech recognition by Wispr Flow",
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
            hasOfflineModels = hasOfflineModels,
            signedIn = signedIn,
            onSelect = { selected, needsDownload ->
                showEngineSheet = false
                when {
                    selected.needsSignIn() && !signedIn -> onRequireSignIn()
                    needsDownload -> pendingDownloadMode = selected
                    else -> onSelectMode(selected)
                }
            },
            onDismiss = { showEngineSheet = false },
        )
    }
    pendingDownloadMode?.let { pending ->
        val recommended = remember { modelManager.getRecommendedSTTModel() }
        val model by produceState<ModelInfo?>(null, pending) {
            value = withContext(Dispatchers.Default) {
                modelManager.getAvailableSTTModels().firstOrNull { it.slug == recommended.modelSlug }
            }
            if (value == null) pendingDownloadMode = null
        }
        model?.let { info ->
            SpeechModelDownloadDialog(
                isLite = recommended is RecommendedModel.Lite,
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
            onSelect = {
                onSelectLanguage(it)
                showLanguageSheet = false
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
    signedIn: Boolean,
    onSelect: (CactusSTTMode, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = IndexTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.sheetSurface) {
        Column(modifier = Modifier.padding(bottom = 28.dp)) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                Text(
                    "Speech Engine",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
                Text(
                    "Select what turns your recordings into text",
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
                )
                val reason = blocked
                    ?: "Sign in to use cloud speech recognition"
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
                    "Spoken Language",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
                Text(
                    "The language you speak when recording",
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search languages") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
                item {
                    SpokenLanguageRow(
                        label = "Automatic",
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
        title = { Text("Download Required") },
        buttons = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(onClick = onDownload) {
                Text(if (isLite) "Download lite model: ${downloadSizeInMb}MB"
                     else "Download offline model: ${downloadSizeInMb}MB")
            }
        },
    ) {
        Text(
            "To use offline speech recognition, you need to download a model first. " +
                "Data charges may apply, Wi-Fi is recommended." +
                if (isLite) " Your device may struggle with larger models, a reduced accuracy model will be used." else "",
            fontSize = 14.sp,
            color = IndexTheme.colors.onSurfaceVariant,
        )
    }
}
