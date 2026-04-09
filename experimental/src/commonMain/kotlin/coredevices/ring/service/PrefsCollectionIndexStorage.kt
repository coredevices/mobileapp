package coredevices.ring.service

import co.touchlab.kermit.Logger
import coredevices.haversine.CollectionIndexStorage
import coredevices.ring.database.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PrefsCollectionIndexStorage(
    private val prefs: Preferences,
): CollectionIndexStorage {
    private val scope = CoroutineScope(Dispatchers.IO)
    companion object {
        private val logger = Logger.withTag("PrefsCollectionIndexStorage")
    }
    override val lastSuccessfulCollectionIndex: StateFlow<Int?>
        get() = prefs.lastSyncIndex

    override fun setLastSuccessfulCollectionIndex(index: Int?) {
        logger.i { "Setting last successful collection index to $index" }
        scope.launch { prefs.setLastSyncIndex(index) }
    }
}