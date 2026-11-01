package coredevices.ring.ui.viewmodel

import PlatformUiContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import coredevices.libindex.di.LibIndexCoroutineScope
import coredevices.ring.backup.BackupDeleteProgress
import coredevices.ring.backup.BackupImporter
import coredevices.ring.backup.BackupManager
import coredevices.ring.database.Preferences
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/** Cloud backup settings plus the full export/import flows. Work is done by [BackupManager] and [BackupImporter]. */
class BackupViewModel(
    private val backupManager: BackupManager,
    private val backupImporter: BackupImporter,
    private val preferences: Preferences,
    private val appScope: LibIndexCoroutineScope,
) : ViewModel() {
    private val logger = Logger.withTag("Backup")

    val userId = Firebase.auth.authStateChanged
        .map { it?.uid }
        .stateIn(viewModelScope, SharingStarted.Lazily, Firebase.auth.currentUser?.uid)

    /** Cached count, refreshed by [loadBackupCount] so the dialog renders instantly on open. */
    val backupCount = preferences.lastBackupCount
    val backupEnabled = preferences.backupEnabled
    private val _backupLoading = MutableStateFlow(false)
    val backupLoading = _backupLoading.asStateFlow()
    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus = _backupStatus.asStateFlow()

    private val _backupDownloadStatus = MutableStateFlow<String?>(null)
    val backupDownloadStatus = _backupDownloadStatus.asStateFlow()
    private val _backupDownloading = MutableStateFlow(false)
    val backupDownloading = _backupDownloading.asStateFlow()

    private val _importStatus = MutableStateFlow<String?>(null)
    val importStatus = _importStatus.asStateFlow()
    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()

    fun setBackupEnabled(enabled: Boolean) {
        preferences.setBackupEnabled(enabled)
    }

    fun clearBackupStatus() {
        _backupStatus.value = null
    }

    fun loadBackupCount() {
        appScope.launch {
            _backupLoading.value = true
            try {
                backupManager.refreshBackupCount()
            } catch (e: Exception) {
                // The cached value (or "—" if never synced) stays as-is.
                logger.w(e) { "Failed to refresh backup count" }
            } finally {
                _backupLoading.value = false
            }
        }
    }

    // appScope: destructive multi-step operations must run to completion even if
    // the user leaves Settings mid-way.
    fun deleteBackup() {
        appScope.launch {
            _backupLoading.value = true
            _backupStatus.value = "Deleting backup..."
            try {
                backupManager.deleteCloudBackup { progress ->
                    _backupStatus.value = when (progress) {
                        BackupDeleteProgress.CollectingAudio -> "Collecting audio files..."
                        BackupDeleteProgress.DeletingDocuments -> "Deleting documents..."
                        is BackupDeleteProgress.DeletingAudio -> "Deleting ${progress.count} audio files..."
                    }
                }
                _backupStatus.value = "Backup deleted"
            } catch (e: Exception) {
                logger.e(e) { "Failed to delete backup" }
                _backupStatus.value = "Delete failed: ${e.message}"
            } finally {
                _backupLoading.value = false
            }
        }
    }

    fun deleteLocalFeed() {
        appScope.launch {
            _backupLoading.value = true
            _backupStatus.value = "Deleting local feed..."
            try {
                backupManager.deleteLocalFeed()
                _backupStatus.value = "Local feed deleted"
            } catch (e: Exception) {
                logger.e(e) { "Failed to delete local feed" }
                _backupStatus.value = "Delete failed: ${e.message}"
            } finally {
                _backupLoading.value = false
            }
        }
    }

    fun downloadFullBackup(uiContext: PlatformUiContext) {
        if (_backupDownloading.value) return
        viewModelScope.launch {
            _backupDownloading.value = true
            _backupDownloadStatus.value = "Starting backup..."
            var zipPath: Path? = null
            try {
                val exported = backupManager.exportAll { done, total ->
                    _backupDownloadStatus.value = "Backing up $done${total?.let { "/$it" } ?: ""}..."
                }
                zipPath = exported.zipPath
                if (exported.recordings == 0) {
                    _backupDownloadStatus.value = "No recordings to backup"
                    return@launch
                }
                _backupDownloadStatus.value = "Choose save location..."
                try {
                    writeToDownloads(uiContext, exported.zipPath, "application/zip")
                    _backupDownloadStatus.value = if (exported.decryptSkipped > 0) {
                        "Backup saved — ${exported.decryptSkipped} recordings skipped (key mismatch). Restore the original key and retry."
                    } else {
                        "Backup saved"
                    }
                } catch (e: Exception) {
                    _backupDownloadStatus.value = "Backup created but save failed: ${e.message}"
                }
            } catch (e: Exception) {
                logger.e(e) { "Backup failed" }
                _backupDownloadStatus.value = "Backup failed: ${e.message}"
            } finally {
                zipPath?.let { runCatching { SystemFileSystem.delete(it, mustExist = false) } }
                _backupDownloading.value = false
            }
        }
    }

    fun importBackup(zipPath: Path) {
        if (_importing.value) return
        viewModelScope.launch {
            _importing.value = true
            _importStatus.value = "Reading backup..."
            try {
                val result = backupImporter.import(zipPath) { imported, total ->
                    _importStatus.value = if (imported == 0) {
                        "Importing $total recordings..."
                    } else {
                        "Imported $imported/$total..."
                    }
                }
                _importStatus.value = buildString {
                    append("Done — ${result.imported} imported, ${result.skipped} already existed")
                    append(", ${result.audioUploaded} audio files")
                    if (result.failed > 0) append(", ${result.failed} failed")
                }
            } catch (e: Exception) {
                logger.e(e) { "Import failed" }
                _importStatus.value = "Import failed: ${e.message}"
            } finally {
                _importing.value = false
            }
        }
    }
}
