package io.rebble.libpebblecommon.contacts

import androidx.paging.PagingSource
import io.rebble.libpebblecommon.database.dao.ContactWithCount
import io.rebble.libpebblecommon.database.entity.MuteState
import io.rebble.libpebblecommon.image.PebbleBitmap
import kotlinx.coroutines.flow.Flow

interface Contacts {
    fun getContactsWithCounts(searchTerm: String, onlyNotified: Boolean): PagingSource<Int, ContactWithCount>
    fun getContact(id: String): Flow<ContactWithCount?>
    fun updateContactState(contactId: String, muteState: MuteState, vibePatternName: String?)
    suspend fun getContactImage(lookupKey: String): PebbleBitmap?
}
