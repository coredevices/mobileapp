package coredevices.pebble.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import coredevices.ui.M3Dialog
import coredevices.util.CoreConfigHolder
import coredevices.util.models.ModelDownloadStatus
import coredevices.util.models.ModelInfo
import coredevices.util.models.ModelManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class ModelManagementScreenViewModel(
    private val modelManager: ModelManager,
    private val coreConfigHolder: CoreConfigHolder
) : ViewModel() {
    private val _downloadedModels = MutableStateFlow(modelManager.getDownloadedModelSlugs())
    val downloadedModels = _downloadedModels.asStateFlow()
    val modelDownloadState = modelManager.modelDownloadStatus

    init {
        viewModelScope.launch {
            modelDownloadState.drop(1).collect {
                if (it !is ModelDownloadStatus.Downloading) {
                    refreshDownloadedModels()
                }
            }
        }
    }

    val currentSTTModel = coreConfigHolder.config.map { it.sttConfig.modelName }.onEach {
        Logger.d("Current STT model changed to $it")
    }.stateIn(
        viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.Lazily,
        initialValue = coreConfigHolder.config.value.sttConfig.modelName
    )

    sealed interface AvailableModelsState {
        object Loading : AvailableModelsState
        data class Success(val models: List<ModelInfo>) : AvailableModelsState
        data class Error(val message: String) : AvailableModelsState
    }

    private val _availableSTTModels =
        MutableStateFlow<AvailableModelsState>(AvailableModelsState.Loading)
    val availableSTTModels = _availableSTTModels.asStateFlow()
    private val _availableLanguageModels =
        MutableStateFlow<AvailableModelsState>(AvailableModelsState.Loading)
    val availableLanguageModels = _availableLanguageModels.asStateFlow()

    init {
        refreshAvailableModels()
    }

    fun refreshDownloadedModels() {
        _downloadedModels.value = modelManager.getDownloadedModelSlugs()
    }

    fun deleteModel(slug: String) {
        modelManager.deleteModel(slug)
        refreshDownloadedModels()
    }

    fun downloadSTTModel(info: ModelInfo) {
        viewModelScope.launch {
            modelManager.downloadSTTModel(info, allowMetered = true)
            modelDownloadState.drop(1)
                .filterIsInstance<ModelDownloadStatus.Idle>()
                .firstOrNull()
            setCurrentSTTModel(info.slug)
            refreshDownloadedModels()
        }
    }

    fun cancelDownload() {
        modelManager.cancelDownload()
    }

    fun downloadLanguageModel(info: ModelInfo) {
        viewModelScope.launch {
            modelManager.downloadLanguageModel(info, allowMetered = true)
            refreshDownloadedModels()
        }
    }

    fun setCurrentSTTModel(slug: String) {
        val newConfig = coreConfigHolder.config.value.copy(
            sttConfig = coreConfigHolder.config.value.sttConfig.copy(
                modelName = slug
            )
        )
        coreConfigHolder.update(newConfig)
    }

    fun refreshAvailableModels() {
        viewModelScope.launch {
            _availableSTTModels.value = try {
                AvailableModelsState.Success(modelManager.getAvailableSTTModels())
            } catch (e: Exception) {
                AvailableModelsState.Error(e.message ?: "Unknown error")
            }
        }
        viewModelScope.launch {
            _availableLanguageModels.value = try {
                AvailableModelsState.Success(modelManager.getAvailableLanguageModels())
            } catch (e: Exception) {
                AvailableModelsState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun getRecommendedSTTModel(): String {
        return modelManager.getRecommendedSTTModel()
    }

    fun getRecommendedLanguageModel(): String {
        return modelManager.getRecommendedLanguageModel()
    }
}

@Composable
private fun ModelDownloadPromptDialog(
    onGetRecommended: () -> Unit,
    onDismiss: () -> Unit,
) {
    M3Dialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,

            )
        },
        title = { Text("Download Required") },
        verticalButtons = {
            TextButton(
                onClick = onGetRecommended,
            ) {
                Text("Download recommended model")
            }
            TextButton(
                onClick = onDismiss,
            ) {
                Text("Pick a model myself")
            }
        }
    ) {
        Text(
            """
                To use offline speech recognition, you need to download a model first.
                Data charges may apply, Wi-Fi is recommended.
            """.trimIndent(),
        )
    }
}

@Composable
fun ModelManagementScreen(
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
    openSttDialog: Boolean = false,
    downloadStatus: State<ModelDownloadStatus>,
    downloadedModels: State<List<String>>,
    availableSTTModels: State<ModelManagementScreenViewModel.AvailableModelsState>,
    currentSTTModel: State<String?>,
    recommendedSTTModelSlug: String,
    onDownloadSTTModel: (ModelInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteModel: (String) -> Unit,
    onSetCurrentSTTModel: (String) -> Unit,
) {
    LaunchedEffect(Unit) {
        topBarParams.title("Manage Models")
        topBarParams.searchAvailable(null)
        topBarParams.actions {}
    }
    val downloadingModelSlug by remember {
        derivedStateOf {
            when (val status = downloadStatus.value) {
                is ModelDownloadStatus.Downloading -> status.modelSlug
                else -> null
            }
        }
    }
    var showSttDialog by remember { mutableStateOf(openSttDialog) }
    if (showSttDialog) {
        ModelDownloadPromptDialog(
            onGetRecommended = {
                showSttDialog = false
                onSetCurrentSTTModel(recommendedSTTModelSlug)
                onDownloadSTTModel(
                    availableSTTModels.value.let {
                        when (it) {
                            is ModelManagementScreenViewModel.AvailableModelsState.Success -> {
                                it.models.find { model -> model.slug == recommendedSTTModelSlug }
                            }
                            else -> null
                        }
                    } ?: return@ModelDownloadPromptDialog
                )
            },
            onDismiss = {
                showSttDialog = false
            }
        )
    }
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        Scaffold { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (val state = availableSTTModels.value) {
                    ModelManagementScreenViewModel.AvailableModelsState.Loading -> {
                        item(contentType = "loading") {
                            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        }
                    }

                    is ModelManagementScreenViewModel.AvailableModelsState.Error -> {
                        item(contentType = "error") {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    is ModelManagementScreenViewModel.AvailableModelsState.Success -> {
                        val reorderedModels = state.models.sortedByDescending {
                            when {
                                it.slug == recommendedSTTModelSlug -> 3
                                it.slug == currentSTTModel.value -> 2
                                downloadedModels.value.contains(it.slug) -> 1
                                else -> 0
                            }
                        }
                        items(reorderedModels, key = { it.slug }) { model ->
                            val isRecommended = model.slug == recommendedSTTModelSlug
                            ModelListItem(
                                modifier = Modifier.animateItem(),
                                model = model,
                                isRecommended = isRecommended,
                                downloadState = when {
                                    downloadedModels.value.contains(model.slug) -> DownloadState.Downloaded
                                    downloadingModelSlug == model.slug -> {
                                        when (val status = downloadStatus.value) {
                                            is ModelDownloadStatus.Downloading -> {
                                                DownloadState.Downloading(status.progress)
                                            }
                                            else -> DownloadState.Downloading(null)
                                        }
                                    }
                                    else -> DownloadState.NotDownloaded
                                },
                                isSelected = currentSTTModel.value == model.slug,
                                onDownload = if (downloadingModelSlug == null) {
                                    { onDownloadSTTModel(model) }
                                } else {
                                    null
                                },
                                onDownloadCancel = {
                                    onCancelDownload()
                                },
                                onDelete = { onDeleteModel(model.slug) },
                                onSelect = { onSetCurrentSTTModel(model.slug) }
                            )
                        }
                    }
                }
            }
        }
    }
}

sealed interface DownloadState {
    data object NotDownloaded : DownloadState
    data class Downloading(val progress: Float? = null) : DownloadState
    data object Downloaded : DownloadState
}

@Composable
private fun ModelListItem(
    modifier: Modifier = Modifier,
    model: ModelInfo,
    isRecommended: Boolean,
    downloadState: DownloadState,
    isSelected: Boolean,
    onDownload: (() -> Unit)?,
    onDownloadCancel: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
) {
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = downloadState == DownloadState.Downloaded) {
                onSelect()
            },
        overlineContent = { if (isRecommended) { Text("Recommended for your device") } },
        headlineContent = { Text(model.slug) },
        supportingContent = { Text("${model.sizeInMB} MB") },
        leadingContent = {
            RadioButton(
                selected = isSelected && downloadState == DownloadState.Downloaded,
                onClick = onSelect,
                enabled = downloadState == DownloadState.Downloaded
            )
        },
        trailingContent = {
            when(downloadState) {
                is DownloadState.Downloading -> {
                    Box(contentAlignment = Alignment.Center) {
                        if (downloadState.progress != null) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(26.dp),
                                strokeWidth = 2.dp,
                                progress = { downloadState.progress },
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(26.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        IconButton(
                            onClick = onDownloadCancel,
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = "Download")
                        }
                    }
                }
                DownloadState.NotDownloaded -> {
                    IconButton(
                        onClick = { onDownload?.invoke() },
                        enabled = onDownload != null
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download")
                    }
                }
                DownloadState.Downloaded -> {
                    IconButton(
                        onClick = onDelete
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun ModelManagementScreen(
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
    openSttDialog: Boolean = false
) {
    val viewModel = koinViewModel<ModelManagementScreenViewModel>()
    val downloadedModels = viewModel.downloadedModels.collectAsState()
    val availableSTTModels = viewModel.availableSTTModels.collectAsState()
    val currentSTTModel = viewModel.currentSTTModel.collectAsState()
    val downloadStatus = viewModel.modelDownloadState.collectAsState()
    val recommendedSTTModel = remember { viewModel.getRecommendedSTTModel() }

    ModelManagementScreen(
        navBarNav = navBarNav,
        topBarParams = topBarParams,
        downloadStatus = downloadStatus,
        openSttDialog = openSttDialog,
        downloadedModels = downloadedModels,
        availableSTTModels = availableSTTModels,
        currentSTTModel = currentSTTModel,
        recommendedSTTModelSlug = recommendedSTTModel,
        onDownloadSTTModel = viewModel::downloadSTTModel,
        onCancelDownload = viewModel::cancelDownload,
        onDeleteModel = viewModel::deleteModel,
        onSetCurrentSTTModel = viewModel::setCurrentSTTModel,
    )
}

@Preview
@Composable
fun ModelManagementScreenPreview() {
    PreviewWrapper {
        ModelManagementScreen(
            navBarNav = NoOpNavBarNav,
            topBarParams = WrapperTopBarParams,
            downloadStatus = mutableStateOf(ModelDownloadStatus.Downloading(modelSlug = "whisper-base")),
            openSttDialog = false,
            downloadedModels = mutableStateOf(listOf("whisper-small")),
            availableSTTModels = mutableStateOf(
                ModelManagementScreenViewModel.AvailableModelsState.Success(
                    listOf(
                        ModelInfo(createdAt = Clock.System.now(), slug = "whisper-base", sizeInMB = 74, url = ""),
                        ModelInfo(createdAt = Clock.System.now(), slug = "whisper-small", sizeInMB = 244, url = ""),
                        ModelInfo(createdAt = Clock.System.now(), slug = "whisper-medium-pro", sizeInMB = 769, url = ""),
                    )
                )
            ),
            currentSTTModel = mutableStateOf("whisper-small"),
            recommendedSTTModelSlug = "whisper-medium-pro",
            onDownloadSTTModel = {},
            onCancelDownload = {},
            onDeleteModel = {},
            onSetCurrentSTTModel = {},
        )
    }
}

@Preview
@Composable
fun ModelManagementScreenPromptDialogPreview() {
    PreviewWrapper {
        ModelDownloadPromptDialog(
            onGetRecommended = {},
            onDismiss = {}
        )
    }
}