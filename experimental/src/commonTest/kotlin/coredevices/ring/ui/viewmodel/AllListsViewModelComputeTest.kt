@file:OptIn(kotlin.time.ExperimentalTime::class)

package coredevices.ring.ui.viewmodel

import coredevices.indexai.data.entity.ItemDocument
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import coredevices.ring.data.entity.room.indexfeed.CachedList
import kotlin.test.Test
import kotlin.test.assertEquals

class AllListsViewModelComputeTest {

    @Test
    fun doneChecklistItemsAreLeftOutOfTheListCardPreview() {
        val list = CachedList(firestoreId = "list-1", title = "Groceries", listKind = "checklist")
        fun item(id: String, title: String, done: Boolean) = CachedItem(
            firestoreId = id,
            title = title,
            done = done,
            parentListIdsCsv = list.firestoreId,
            metadata = ItemDocument.ItemMetadata.Checklist,
        )
        val state = AllListsViewModel.compute(
            lists = listOf(list),
            items = listOf(item("i-1", "Milk", done = true), item("i-2", "Onions", done = false)),
            query = "",
        )
        assertEquals(listOf("Onions"), state.lists.single().items.map { it.title })

        val searched = AllListsViewModel.compute(
            lists = listOf(list),
            items = listOf(item("i-1", "Milk", done = true), item("i-2", "Onions", done = false)),
            query = "milk",
        )
        assertEquals(listOf("Milk", "Onions"), searched.lists.single().items.map { it.title }.sorted())
    }
}
