package coredevices.ring.database.room.repository

import coredevices.ring.data.entity.room.ClickAction
import coredevices.ring.data.entity.room.ClickActionBinding
import coredevices.ring.database.room.dao.ClickActionBindingDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeClickActionBindingDao : ClickActionBindingDao {
    val rows = MutableStateFlow<List<ClickActionBinding>>(emptyList())
    private var nextId = 1L

    /** Set to have the next write fail, standing in for the unique-index constraint. */
    var failNextWrite: Throwable? = null

    override fun getAllFlow(): Flow<List<ClickActionBinding>> =
        rows.map { list -> list.sortedBy { it.clickCount } }

    override suspend fun getEnabledByClickCount(clickCount: Int): ClickActionBinding? =
        rows.value.firstOrNull { it.clickCount == clickCount && it.enabled }

    override suspend fun getByClickCount(clickCount: Int): ClickActionBinding? =
        rows.value.firstOrNull { it.clickCount == clickCount }

    override suspend fun insert(binding: ClickActionBinding): Long {
        failNextWrite?.let { failNextWrite = null; throw it }
        val id = nextId++
        rows.value = rows.value + binding.copy(id = id)
        return id
    }

    override suspend fun update(binding: ClickActionBinding) {
        failNextWrite?.let { failNextWrite = null; throw it }
        rows.value = rows.value.map { if (it.id == binding.id) binding else it }
    }

    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }
}

class ClickActionRepositoryTest {

    private fun binding(clickCount: Int, id: Long = 0L, enabled: Boolean = true) =
        ClickActionBinding(
            id = id,
            clickCount = clickCount,
            action = ClickAction.AgentText("test"),
            enabled = enabled,
        )

    @Test
    fun `saving a new binding assigns an id`() = runTest {
        val repo = ClickActionRepository(FakeClickActionBindingDao())

        val result = repo.save(binding(clickCount = 4))

        assertEquals(ClickActionRepository.SaveResult.Saved(1L), result)
    }

    @Test
    fun `a click count owned by another binding is rejected`() = runTest {
        val repo = ClickActionRepository(FakeClickActionBindingDao())
        repo.save(binding(clickCount = 4))

        val result = repo.save(binding(clickCount = 4))

        assertEquals(ClickActionRepository.SaveResult.ClickCountTaken, result)
    }

    @Test
    fun `a binding may keep its own click count when edited`() = runTest {
        val dao = FakeClickActionBindingDao()
        val repo = ClickActionRepository(dao)
        repo.save(binding(clickCount = 4))

        val result = repo.save(
            ClickActionBinding(id = 1L, clickCount = 4, action = ClickAction.AgentText("hi")),
        )

        assertEquals(ClickActionRepository.SaveResult.Saved(1L), result)
        assertEquals(ClickAction.AgentText("hi"), dao.getByClickCount(4)?.action)
    }

    @Test
    fun `click counts outside the bindable range are rejected`() = runTest {
        val repo = ClickActionRepository(FakeClickActionBindingDao())

        assertEquals(
            ClickActionRepository.SaveResult.ClickCountOutOfRange,
            repo.save(binding(clickCount = ClickActionBinding.MIN_CLICK_COUNT - 1)),
        )
        assertEquals(
            ClickActionRepository.SaveResult.ClickCountOutOfRange,
            repo.save(binding(clickCount = ClickActionBinding.MAX_CLICK_COUNT + 1)),
        )
    }

    @Test
    fun `a write that loses the unique-index race is reported, not thrown`() = runTest {
        val dao = FakeClickActionBindingDao()
        val repo = ClickActionRepository(dao)
        dao.failNextWrite = IllegalStateException("UNIQUE constraint failed")

        val result = repo.save(binding(clickCount = 4))

        assertTrue(result is ClickActionRepository.SaveResult.Failed)
        assertEquals("UNIQUE constraint failed", result.message)
    }

    @Test
    fun `only enabled bindings are dispatchable`() = runTest {
        val repo = ClickActionRepository(FakeClickActionBindingDao())
        repo.save(binding(clickCount = 4, enabled = false))

        assertNull(repo.enabledBindingFor(4))
    }

    @Test
    fun `deleting frees the click count`() = runTest {
        val repo = ClickActionRepository(FakeClickActionBindingDao())
        val saved = repo.save(binding(clickCount = 4)) as ClickActionRepository.SaveResult.Saved

        repo.delete(saved.id)

        assertEquals(
            ClickActionRepository.SaveResult.Saved(2L),
            repo.save(binding(clickCount = 4)),
        )
    }
}
