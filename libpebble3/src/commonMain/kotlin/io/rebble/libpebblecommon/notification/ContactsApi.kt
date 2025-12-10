package io.rebble.libpebblecommon.notification

import androidx.compose.ui.graphics.ImageBitmap
import io.rebble.libpebblecommon.connection.Contacts
import io.rebble.libpebblecommon.contacts.SystemContacts
import io.rebble.libpebblecommon.database.dao.ContactDao
import io.rebble.libpebblecommon.database.dao.ContactWithCount
import io.rebble.libpebblecommon.database.entity.MuteState
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ContactsApi(
    private val contactDao: ContactDao,
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val systemContacts: SystemContacts,
) : Contacts {
    override fun getContactsWithCounts(): Flow<List<ContactWithCount>> {
        return contactDao.getContactsWithCountFlow()
    }

    override fun updateContactState(
        contactId: String,
        muteState: MuteState,
        vibePatternName: String?,
    ) {
        libPebbleCoroutineScope.launch {
            contactDao.updateContactState(contactId, muteState, vibePatternName)
        }
    }

    override suspend fun getContactImage(lookupKey: String): ImageBitmap? {
        return systemContacts.getContactImage(lookupKey)
    }
}