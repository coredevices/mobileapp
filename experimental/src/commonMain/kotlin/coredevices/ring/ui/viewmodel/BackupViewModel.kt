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
import coredevices.util.writeToDownloads
import coreapp.ring.generated.resources.Res
import coreapp.ring.generated.resources.backup_vm_backing_up
import coreapp.ring.generated.resources.backup_vm_backing_up_of_total
import coreapp.ring.generated.resources.backup_vm_backup_deleted
import coreapp.ring.generated.resources.backup_vm_backup_failed
import coreapp.ring.generated.resources.backup_vm_backup_saved
import coreapp.ring.generated.resources.backup_vm_choose_save_location
import coreapp.ring.generated.resources.backup_vm_collecting_audio
import coreapp.ring.generated.resources.backup_vm_delete_failed
import coreapp.ring.generated.resources.backup_vm_deleting_audio
import coreapp.ring.generated.resources.backup_vm_deleting_backup
import coreapp.ring.generated.resources.backup_vm_deleting_documents
import coreapp.ring.generated.resources.backup_vm_deleting_local_feed
import coreapp.ring.generated.resources.backup_vm_import_done
import coreapp.ring.generated.resources.backup_vm_import_done_with_failures
import coreapp.ring.generated.resources.backup_vm_import_failed
import coreapp.ring.generated.resources.backup_vm_imported_progress
import coreapp.ring.generated.resources.backup_vm_importing
import coreapp.ring.generated.resources.backup_vm_local_feed_deleted
import coreapp.ring.generated.resources.backup_vm_no_recordings
import coreapp.ring.generated.resources.backup_vm_reading_backup
import coreapp.ring.generated.resources.backup_vm_save_failed
import coreapp.ring.generated.resources.backup_vm_saved_with_skipped
import coreapp.ring.generated.resources.backup_vm_starting_backup
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
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

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
            _backupStatus.value = getString(Res.string.backup_vm_deleting_backup)
            try {
                backupManager.deleteCloudBackup { progress ->
                    _backupStatus.value = when (progress) {
                        BackupDeleteProgress.CollectingAudio -> getString(Res.string.backup_vm_collecting_audio)
                        BackupDeleteProgress.DeletingDocuments -> getString(Res.string.backup_vm_deleting_documents)
                        is BackupDeleteProgress.DeletingAudio -> getPluralString(Res.plurals.backup_vm_deleting_audio, progress.count, progress.count)
                    }
                }
                _backupStatus.value = getString(Res.string.backup_vm_backup_deleted)
            } catch (e: Exception) {
                logger.e(e) { "Failed to delete backup" }
                _backupStatus.value = getString(Res.string.backup_vm_delete_failed, e.message.toString())
            } finally {
                _backupLoading.value = false
            }
        }
    }

    fun deleteLocalFeed() {
        appScope.launch {
            _backupLoading.value = true
            _backupStatus.value = getString(Res.string.backup_vm_deleting_local_feed)
            try {
                backupManager.deleteLocalFeed()
                _backupStatus.value = getString(Res.string.backup_vm_local_feed_deleted)
            } catch (e: Exception) {
                logger.e(e) { "Failed to delete local feed" }
                _backupStatus.value = getString(Res.string.backup_vm_delete_failed, e.message.toString())
            } finally {
                _backupLoading.value = false
            }
        }
    }

    fun downloadFullBackup(uiContext: PlatformUiContext) {
        if (_backupDownloading.value) return
        viewModelScope.launch {
            _backupDownloading.value = true
            _backupDownloadStatus.value = getString(Res.string.backup_vm_starting_backup)
            var zipPath: Path? = null
            try {
                val exported = backupManager.exportAll { done, total ->
                    _backupDownloadStatus.value = if (total != null) {
                        getString(Res.string.backup_vm_backing_up_of_total, done, total)
                    } else {
                        getString(Res.string.backup_vm_backing_up, done)
                    }
                }
                zipPath = exported.zipPath
                if (exported.recordings == 0) {
                    _backupDownloadStatus.value = getString(Res.string.backup_vm_no_recordings)
                    return@launch
                }
                _backupDownloadStatus.value = getString(Res.string.backup_vm_choose_save_location)
                try {
                    writeToDownloads(uiContext, exported.zipPath, "application/zip")
                    _backupDownloadStatus.value = if (exported.decryptSkipped > 0) {
                        getPluralString(Res.plurals.backup_vm_saved_with_skipped, exported.decryptSkipped, exported.decryptSkipped)
                    } else {
                        getString(Res.string.backup_vm_backup_saved)
                    }
                } catch (e: Exception) {
                    _backupDownloadStatus.value = getString(Res.string.backup_vm_save_failed, e.message.toString())
                }
            } catch (e: Exception) {
                logger.e(e) { "Backup failed" }
                _backupDownloadStatus.value = getString(Res.string.backup_vm_backup_failed, e.message.toString())
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
            _importStatus.value = getString(Res.string.backup_vm_reading_backup)
            try {
                val result = backupImporter.import(zipPath) { imported, total ->
                    _importStatus.value = if (imported == 0) {
                        getPluralString(Res.plurals.backup_vm_importing, total, total)
                    } else {
                        getString(Res.string.backup_vm_imported_progress, imported, total)
                    }
                }
                _importStatus.value = if (result.failed > 0) {
                    getString(Res.string.backup_vm_import_done_with_failures, result.imported, result.skipped, result.audioUploaded, result.failed)
                } else {
                    getString(Res.string.backup_vm_import_done, result.imported, result.skipped, result.audioUploaded)
                }
            } catch (e: Exception) {
                logger.e(e) { "Import failed" }
                _importStatus.value = getString(Res.string.backup_vm_import_failed, e.message.toString())
            } finally {
                _importing.value = false
            }
        }
    }
}
