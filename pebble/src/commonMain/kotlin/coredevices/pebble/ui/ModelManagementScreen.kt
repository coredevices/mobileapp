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
import androidx.compose.foundation.layout.offset
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
import com.russhwolf.settings.Settings
import coredevices.ui.M3Dialog
import coredevices.util.CoreConfigHolder
import coredevices.util.models.CactusSTTMode
import coredevices.util.models.ModelDownloadStatus
import coredevices.util.models.inProgressSlug
import coredevices.util.models.inProgress
import coredevices.util.models.ModelInfo
import coredevices.util.models.ModelManager
import coredevices.util.models.RecommendedModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import coreapp.pebble.generated.resources.Res
import coreapp.pebble.generated.resources.models_switched_to_cloud
import coreapp.pebble.generated.resources.models_unknown_error
import coreapp.pebble.generated.resources.models_download_required
import coreapp.pebble.generated.resources.models_download_lite
import coreapp.pebble.generated.resources.models_download_model
import coreapp.pebble.generated.resources.models_download_message
import coreapp.pebble.generated.resources.models_lite_message
import coreapp.pebble.generated.resources.models_title
import coreapp.pebble.generated.resources.models_recommended
import coreapp.pebble.generated.resources.models_size_mb
import coreapp.util.generated.resources.Res as UtilRes
import coreapp.util.generated.resources.cancel
import coreapp.util.generated.resources.delete
import coreapp.util.generated.resources.download
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.getString

class ModelManagementScreenViewModel(
    private val modelManager: ModelManager,
    private val coreConfigHolder: CoreConfigHolder,
    private val settings: Settings
) : ViewModel() {
    private val _downloadedModels = MutableStateFlow(modelManager.getDownloadedModelSlugs())
    val downloadedModels = _downloadedModels.asStateFlow()
    val modelDownloadState = modelManager.modelDownloadStatus

    init {
        viewModelScope.launch {
            modelDownloadState.drop(1).collect {
                if (!it.inProgress) {
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

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    fun deleteModel(slug: String) {
        modelManager.deleteModel(slug)
        refreshDownloadedModels()
        // With no STT models left, local speech recognition can't run, so fall
        // back to cloud-only and let the user know.
        if (modelManager.getDownloadedSTTModelSlugs().isEmpty() &&
            coreConfigHolder.config.value.sttConfig.mode.usesLocalCactus()
        ) {
            coreConfigHolder.update(
                coreConfigHolder.config.value.copy(
                    sttConfig = coreConfigHolder.config.value.sttConfig.copy(
                        mode = CactusSTTMode.RemoteOnly
                    )
                )
            )
            viewModelScope.launch { _snackbarMessages.tryEmit(getString(Res.string.models_switched_to_cloud)) }
        }
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
                val models = modelManager.getAvailableSTTModels()
                val recommendedSTTModelSlug = modelManager.getRecommendedSTTModel()
                AvailableModelsState.Success(
                    if (!settings.showDebugOptions()) {
                        models.filter {
                            it.slug == recommendedSTTModelSlug.modelSlug || it.slug in downloadedModels.value
                        }
                    } else {
                        models
                    }
                )
            } catch (e: Exception) {
                AvailableModelsState.Error(e.message ?: getString(Res.string.models_unknown_error))
            }
        }
        viewModelScope.launch {
            _availableLanguageModels.value = try {
                AvailableModelsState.Success(modelManager.getAvailableLanguageModels())
            } catch (e: Exception) {
                AvailableModelsState.Error(e.message ?: getString(Res.string.models_unknown_error))
            }
        }
    }

    fun getRecommendedSTTModel(): RecommendedModel {
        return modelManager.getRecommendedSTTModel()
    }

    fun getRecommendedLanguageModel(): String {
        return modelManager.getRecommendedLanguageModel()
    }
}

@Composable
fun ModelDownloadPromptDialog(
    isLite: Boolean,
    downloadSizeInMb: Int,
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
        title = { Text(stringResource(Res.string.models_download_required)) },
        verticalButtons = {
            TextButton(
                onClick = onGetRecommended,
            ) {
                if (isLite) {
                    Text(stringResource(Res.string.models_download_lite, downloadSizeInMb))
                } else {
                    Text(stringResource(Res.string.models_download_model, downloadSizeInMb))
                }
            }
            TextButton(
                onClick = onDismiss,
            ) {
                Text(stringResource(UtilRes.string.cancel))
            }
        }
    ) {
        Text(
            stringResource(Res.string.models_download_message),
        )
        if (isLite) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(Res.string.models_lite_message))
        }
    }
}

@Composable
fun ModelManagementScreen(
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
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
        topBarParams.title(getString(Res.string.models_title))
        topBarParams.searchAvailable(null)
        topBarParams.actions {}
    }
    val downloadingModelSlug by remember {
        derivedStateOf { downloadStatus.value.inProgressSlug }
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
        overlineContent = { if (isRecommended) { Text(stringResource(Res.string.models_recommended)) } },
        headlineContent = { Text(model.slug) },
        supportingContent = { model.intendedTask?.let { Text(it) } },
        leadingContent = {
            RadioButton(
                selected = isSelected && downloadState == DownloadState.Downloaded,
                onClick = onSelect,
                enabled = downloadState == DownloadState.Downloaded
            )
        },
        trailingContent = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
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
                                Icon(Icons.Default.Cancel, contentDescription = stringResource(UtilRes.string.download))
                            }
                        }
                    }
                    DownloadState.NotDownloaded -> {
                        IconButton(
                            onClick = { onDownload?.invoke() },
                            enabled = onDownload != null
                        ) {
                            Icon(Icons.Default.Download, contentDescription = stringResource(UtilRes.string.download))
                        }
                    }
                    DownloadState.Downloaded -> {
                        IconButton(
                            onClick = onDelete
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(UtilRes.string.delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Text(stringResource(Res.string.models_size_mb, model.sizeInMB), modifier = Modifier.offset(y = (-8).dp))
            }
        }
    )
}

@Composable
fun ModelManagementScreen(
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
) {
    val viewModel = koinViewModel<ModelManagementScreenViewModel>()
    val downloadedModels = viewModel.downloadedModels.collectAsState()
    val availableSTTModels = viewModel.availableSTTModels.collectAsState()
    val currentSTTModel = viewModel.currentSTTModel.collectAsState()
    val downloadStatus = viewModel.modelDownloadState.collectAsState()
    val recommendedSTTModel = remember { viewModel.getRecommendedSTTModel() }

    LaunchedEffect(viewModel, topBarParams) {
        viewModel.snackbarMessages.collect { topBarParams.showSnackbar(it) }
    }

    ModelManagementScreen(
        navBarNav = navBarNav,
        topBarParams = topBarParams,
        downloadStatus = downloadStatus,
        downloadedModels = downloadedModels,
        availableSTTModels = availableSTTModels,
        currentSTTModel = currentSTTModel,
        recommendedSTTModelSlug = recommendedSTTModel.modelSlug,
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
            downloadedModels = mutableStateOf(listOf("whisper-small")),
            availableSTTModels = mutableStateOf(
                ModelManagementScreenViewModel.AvailableModelsState.Success(
                    listOf(
                        ModelInfo(createdAt = Clock.System.now(), slug = "parakeet-tdt-0.6b-v3"),
                        ModelInfo(createdAt = Clock.System.now(), slug = "parakeet-tdt-0.6b-v2", intendedTask = "Higher accuracy for English")
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
            isLite = false,
            onGetRecommended = {},
            onDismiss = {},
            downloadSizeInMb = 100,
        )
    }
}

@Preview
@Composable
fun ModelManagementScreenPromptDialogLitePreview() {
    PreviewWrapper {
        ModelDownloadPromptDialog(
            isLite = true,
            onGetRecommended = {},
            onDismiss = {},
            downloadSizeInMb = 100,
        )
    }
}