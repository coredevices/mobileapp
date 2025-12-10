package io.rebble.libpebblecommon.contacts

import androidx.compose.ui.graphics.ImageBitmap
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.dao.ContactDao
import io.rebble.libpebblecommon.database.entity.ContactEntity
import io.rebble.libpebblecommon.database.entity.MuteState
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

data class SystemContact(
    val name: String,
    val key: String,
)

interface SystemContacts {
    fun registerForContactsChanges(): Flow<Unit>
    suspend fun getContacts(): List<SystemContact>
    fun hasPermission(): Boolean
    suspend fun getContactImage(lookupKey: String): ImageBitmap?
}

class PhoneContactsSyncer(
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val systemContacts: SystemContacts,
    private val contactDao: ContactDao,
) {
    private val logger = Logger.withTag("PhoneContactsSyncer")
    private val syncTrigger = MutableSharedFlow<Unit>()
    private val initialized = AtomicBoolean(false)

    fun init() {
        if (!systemContacts.hasPermission()) {
            logger.w { "No permission" }
            return
        }
        if (!initialized.compareAndSet(expectedValue = false, newValue = true)) {
            logger.d { "Already initialized" }
            return
        }
        logger.v { "init()" }
        libPebbleCoroutineScope.launch {
            libPebbleCoroutineScope.launch {
                syncTrigger.conflate().collect {
                    syncDeviceContactsToDb()
                }
            }
            // Make sure the above is collecting already
            delay(1.seconds)
            requestSync()
            libPebbleCoroutineScope.launch {
                systemContacts.registerForContactsChanges().debounce(5.seconds).collect {
                    requestSync()
                }
            }
        }
    }

    private suspend fun requestSync() {
        syncTrigger.emit(Unit)
    }

    private suspend fun syncDeviceContactsToDb() {
        logger.d { "syncDeviceContactsToDb" }
        val newContacts = systemContacts.getContacts()
        logger.d { "Got ${newContacts.size} contacts from device, syncing... " }
        val existingContacts = contactDao.getContacts()
        existingContacts.forEach { existingContact ->
            val matchingNewContact = newContacts.find { it.key == existingContact.lookupKey }
            if (matchingNewContact != null) {
                if (existingContact.name != matchingNewContact.name) {
                    contactDao.insertOrUpdate(existingContact.copy(name = matchingNewContact.name))
                }
            } else {
                contactDao.delete(existingContact.lookupKey)
            }
        }
        newContacts.forEach { newContact ->
            if (existingContacts.none { it.lookupKey == newContact.key }) {
                contactDao.insertOrUpdate(newContact.asEntity())
            }
        }
    }
}

fun SystemContact.asEntity(): ContactEntity = ContactEntity(
    lookupKey = key,
    name = name,
    muteState = MuteState.Never,
    vibePatternName = null,
)