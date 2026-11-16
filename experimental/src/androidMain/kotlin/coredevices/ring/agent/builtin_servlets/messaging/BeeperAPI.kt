package coredevices.ring.agent.builtin_servlets.messaging

import android.content.ContentResolver
import androidx.core.database.getStringOrNull
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import kotlin.time.measureTimedValue

object BeeperAPI {
    const val CONTACTS_URI = "content://com.beeper.api/contacts"
    const val CHATS_URI = "content://com.beeper.api/chats"
    const val MESSAGES_URI = "content://com.beeper.api/messages"

    private val logger = Logger.withTag("BeeperAPI")

    private inline fun <T> timed(query: String, block: () -> T): T {
        val (value, duration) = measureTimedValue(block)
        logger.d { "$query took $duration" }
        return value
    }

    data class Contact(
        val id: String,
        val name: String,
        val contactName: String?,
        val protocol: String,
        val roomIds: String?,
    )

    data class Chat(
        val roomId: String,
        val timestamp: Long,
        val title: String?,
        val isOneToOne: Boolean,
    )

    fun searchContacts(contentResolver: ContentResolver, query: String?, limit: Int, offset: Int = 0): List<Contact> = timed("searchContacts") {
        val uri = CONTACTS_URI.toUri().buildUpon()
            .appendQueryParameter("limit", limit.toString())
            .appendQueryParameter("offset", offset.toString())
            .apply { if (!query.isNullOrBlank()) appendQueryParameter("query", query) }
            .build()
        val contacts = mutableListOf<Contact>()
        contentResolver.query(uri, arrayOf("id", "displayName", "contactDisplayName", "protocol", "roomIds"), null, null, null)?.use { c ->
            val idIdx = c.getColumnIndexOrThrow("id")
            val nameIdx = c.getColumnIndexOrThrow("displayName")
            val contactNameIdx = c.getColumnIndex("contactDisplayName")
            val protocolIdx = c.getColumnIndexOrThrow("protocol")
            val roomIdsIdx = c.getColumnIndex("roomIds")
            while (c.moveToNext()) {
                contacts.add(
                    Contact(
                        id = c.getString(idIdx),
                        name = c.getString(nameIdx),
                        contactName = if (contactNameIdx >= 0) c.getStringOrNull(contactNameIdx) else null,
                        protocol = c.getString(protocolIdx),
                        roomIds = if (roomIdsIdx >= 0) c.getStringOrNull(roomIdsIdx) else null,
                    )
                )
            }
        }
        contacts
    }

    /** Chats newest first, either the given [roomIds] or the [limit] most recent. */
    fun chats(contentResolver: ContentResolver, roomIds: Collection<String>? = null, limit: Int? = null): List<Chat> = timed(if (roomIds != null) "chats(${roomIds.size} rooms)" else "chats(limit=$limit)") {
        val uri = CHATS_URI.toUri().buildUpon()
            .apply {
                roomIds?.let { appendQueryParameter("roomIds", it.joinToString(",")) }
                limit?.let { appendQueryParameter("limit", it.toString()) }
            }
            .build()
        val chats = mutableListOf<Chat>()
        contentResolver.query(
            uri,
            arrayOf("roomId", "timestamp", "oneToOne", "title"),
            null, null, "timestamp DESC"
        )?.use { c ->
            val roomIdIdx = c.getColumnIndexOrThrow("roomId")
            val tsIdx = c.getColumnIndexOrThrow("timestamp")
            val oneToOneIdx = c.getColumnIndex("oneToOne")
            val titleIdx = c.getColumnIndex("title")
            while (c.moveToNext()) {
                chats.add(
                    Chat(
                        roomId = c.getString(roomIdIdx),
                        timestamp = c.getLong(tsIdx),
                        title = if (titleIdx >= 0) c.getStringOrNull(titleIdx) else null,
                        isOneToOne = if (oneToOneIdx >= 0) c.getInt(oneToOneIdx) == 1 else true,
                    )
                )
            }
        }
        chats
    }

    /** The most recent one-to-one chat among [roomIds] (comma-separated), else the most recent group chat. */
    fun latestChat(contentResolver: ContentResolver, roomIds: String): Chat? {
        val chats = chats(contentResolver, roomIds.split(","))
        return chats.firstOrNull { it.isOneToOne } ?: chats.maxByOrNull { it.timestamp }
    }

    /** Timestamp of the newest message in [roomId], from anyone including the user. */
    fun latestMessageTime(contentResolver: ContentResolver, roomId: String): Long? = timed("latestMessageTime") {
        val uri = MESSAGES_URI.toUri().buildUpon()
            .appendQueryParameter("limit", "1")
            .appendQueryParameter("roomIds", roomId)
            .build()
        contentResolver.query(uri, arrayOf("timestamp"), null, null, "timestamp DESC")?.use { c ->
            if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow("timestamp")) else null
        }
    }
}
