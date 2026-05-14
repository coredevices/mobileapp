package io.rebble.libpebblecommon.io.rebble.libpebblecommon.contacts

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.contacts.SystemContact
import io.rebble.libpebblecommon.contacts.SystemContacts
import io.rebble.libpebblecommon.di.LibPebbleCoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class AndroidSystemContacts(
    private val appContext: AppContext,
    private val libPebbleCoroutineScope: LibPebbleCoroutineScope,
    private val contentResolver: ContentResolver,
) : SystemContacts {
    private val logger = Logger.withTag("AndroidSystemContacts")
    private val observerHandler = Handler(Looper.getMainLooper())

    override fun registerForContactsChanges(): Flow<Unit> {
        val flow = MutableSharedFlow<Unit>()
        val observer = object : ContentObserver(observerHandler) {
            override fun onChange(selfChange: Boolean) {
                logger.d("Calendar observer changed")
                libPebbleCoroutineScope.launch {
                    flow.emit(Unit)
                }
            }
        }
        try {
            contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                false,
                observer
            );
        } catch (e: SecurityException) {
            logger.e(e) { "Error registering for calendar changes" }
        }
        return flow
    }

    override suspend fun getContactImage(lookupKey: String): ImageBitmap? {
        val contactId = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID),
            "${ContactsContract.Contacts.LOOKUP_KEY} = ?",
            arrayOf(lookupKey),
            null,
        ).use { cursor ->
            if (cursor?.moveToFirst() == true) {
                cursor.getLong(0)
            } else {
                null
            }
        }
        if (contactId == null) {
            return null
        }

        ContactsContract.Contacts.openContactPhotoInputStream(contentResolver,
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId))?.use { inputStream ->
            return BitmapFactory.decodeStream(inputStream).asImageBitmap()
        }

        return null
    }

    override suspend fun getContacts(): List<SystemContact> {
        val cursor = try {
            appContext.context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME,
                ),
                null,
                null,
                "${ContactsContract.Contacts.DISPLAY_NAME} ASC"
            )
        } catch (e: SecurityException) {
            logger.w(e) { "Contacts query denied" }
            return emptyList()
        }
        return cursor?.use { cursor ->
            val keyCol = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
            val nameCol = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            if (keyCol == -1 || nameCol == -1) {
                logger.w { "Missing required columns in cursor" }
                return emptyList()
            }
            cursor.moveToFirst()
            val contacts = mutableListOf<SystemContact>()

            while (!cursor.isAfterLast) {
                val key = cursor.getString(keyCol)
                val name = cursor.getString(nameCol)
                if (key != null && name != null) {
                    contacts.add(
                        SystemContact(
                            name = name,
                            key = key,
                        )
                    )
                }
                cursor.moveToNext()
            }
            contacts
        } ?: emptyList()
    }

    override fun hasPermission(): Boolean {
        return appContext.context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
}