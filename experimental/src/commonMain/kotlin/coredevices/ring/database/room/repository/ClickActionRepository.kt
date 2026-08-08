package coredevices.ring.database.room.repository

import coredevices.ring.data.entity.room.ClickActionBinding
import coredevices.ring.database.room.dao.ClickActionBindingDao
import kotlinx.coroutines.flow.Flow

class ClickActionRepository(private val dao: ClickActionBindingDao) {

    fun bindingsFlow(): Flow<List<ClickActionBinding>> = dao.getAllFlow()

    suspend fun enabledBindingFor(clickCount: Int): ClickActionBinding? =
        dao.getEnabledByClickCount(clickCount)

    /**
     * Inserts or updates [binding]. Click counts are unique, so a count already owned by a
     * different binding is rejected rather than allowed to fail on the unique index.
     */
    suspend fun save(binding: ClickActionBinding): SaveResult {
        if (binding.clickCount !in ClickActionBinding.CLICK_COUNT_RANGE) {
            return SaveResult.ClickCountOutOfRange
        }
        val existing = dao.getByClickCount(binding.clickCount)
        if (existing != null && existing.id != binding.id) {
            return SaveResult.ClickCountTaken
        }
        // The check above is not atomic with the write, and clickCount is uniquely indexed, so
        // a concurrent save can still lose the race — surface it rather than crash the caller.
        return runCatching {
            if (binding.id == 0L) {
                SaveResult.Saved(dao.insert(binding))
            } else {
                dao.update(binding)
                SaveResult.Saved(binding.id)
            }
        }.getOrElse { SaveResult.Failed(it.message ?: "Couldn't save the click action") }
    }

    suspend fun delete(id: Long) = dao.delete(id)

    sealed interface SaveResult {
        data class Saved(val id: Long) : SaveResult
        data object ClickCountTaken : SaveResult
        data object ClickCountOutOfRange : SaveResult
        data class Failed(val message: String) : SaveResult
    }
}
