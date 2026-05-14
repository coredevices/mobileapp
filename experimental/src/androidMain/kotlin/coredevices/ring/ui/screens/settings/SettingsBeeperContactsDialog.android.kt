package coredevices.ring.ui.screens.settings

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.database.getStringOrNull
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingSource
import androidx.paging.PagingState
import coredevices.ring.agent.builtin_servlets.messaging.ApprovedBeeperContact
import coredevices.ring.agent.builtin_servlets.messaging.BeeperAPI
import coredevices.ring.database.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

actual class SettingsBeeperContactsDialogViewModel actual constructor() : ViewModel(), KoinComponent {
    private val context: Context by inject()
    private val contentResolver: ContentResolver by lazy { context.contentResolver }
    private val prefs: Preferences by inject()

    private val _hasPermission = MutableStateFlow(checkBeeperPermission())
    actual val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private fun checkBeeperPermission(): Boolean {
        return context.checkSelfPermission("com.beeper.android.permission.READ_PERMISSION") == PackageManager.PERMISSION_GRANTED
    }

    actual fun refreshPermission() {
        _hasPermission.value = checkBeeperPermission()
    }

    private val _approvedIds = MutableStateFlow(
        prefs.approvedBeeperContacts.value.map { it.roomId }.toHashSet()
    )
    actual val approvedIds: StateFlow<Set<String>> = _approvedIds.asStateFlow()

    private val _approvedContacts = MutableStateFlow<List<SettingsBeeperContact>>(emptyList())
    actual val approvedContacts: StateFlow<List<SettingsBeeperContact>> = _approvedContacts.asStateFlow()

    actual fun loadApprovedContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            val savedContacts = prefs.approvedBeeperContacts.value
            val ids = _approvedIds.value
            if (ids.isEmpty() && savedContacts.isEmpty()) {
                _approvedContacts.value = emptyList()
                return@launch
            }
            // Build a map of roomId -> saved nickname/name for merging
            val savedByRoom = savedContacts.associateBy { it.roomId }

            try {
                val results = mutableListOf<SettingsBeeperContact>()
                val foundRoomIds = mutableSetOf<String>()

                // First, try loading as roomIds (new format)
                val roomIdCandidates = ids.filter { it.startsWith("!") }
                if (roomIdCandidates.isNotEmpty()) {
                    val uri = BeeperAPI.CHATS_URI.toUri().buildUpon()
                        .appendQueryParameter("roomIds", roomIdCandidates.joinToString(","))
                        .build()
                    loadChatsFromUri(uri, results, foundRoomIds)
                }

                // Then, try loading as contact/sender IDs (old format) via contacts API
                val contactIdCandidates = ids.filter { it.startsWith("@") }
                if (contactIdCandidates.isNotEmpty()) {
                    val contactsUri = BeeperAPI.CONTACTS_URI.toUri().buildUpon()
                        .appendQueryParameter("senderIds", contactIdCandidates.joinToString(","))
                        .build()
                    contentResolver.query(contactsUri, null, null, null, null)?.use { c ->
                        val idIdx = c.getColumnIndexOrThrow("id")
                        val nameIdx = c.getColumnIndexOrThrow("displayName")
                        val protocolIdx = c.getColumnIndexOrThrow("protocol")
                        val roomIdsIdx = c.getColumnIndex("roomIds")
                        while (c.moveToNext()) {
                            val contactId = c.getString(idIdx)
                            val name = c.getString(nameIdx)
                            val protocol = c.getString(protocolIdx)
                            val roomIds = if (roomIdsIdx >= 0) c.getStringOrNull(roomIdsIdx) else null
                            val firstRoom = roomIds?.split(",")?.firstOrNull()
                            if (firstRoom != null && !foundRoomIds.contains(firstRoom)) {
                                foundRoomIds.add(firstRoom)
                                results.add(SettingsBeeperContact(
                                    id = contactId,
                                    name = name,
                                    protocol = protocol,
                                    roomId = firstRoom
                                ))
                                // Migrate: replace old contact ID with roomId in approved set
                                val updated = HashSet(_approvedIds.value)
                                updated.remove(contactId)
                                updated.add(firstRoom)
                                _approvedIds.value = updated
                            }
                        }
                    }
                }

                // Merge saved nicknames into loaded contacts
                _approvedContacts.value = results.map { contact ->
                    val saved = savedByRoom[contact.roomId]
                    if (saved != null) {
                        contact.copy(nickname = saved.nickname)
                    } else contact
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Permission denied loading approved contacts")
                // Fall back to showing saved contacts without live Beeper data
                _approvedContacts.value = savedContacts.map {
                    SettingsBeeperContact(
                        id = it.roomId,
                        name = it.name.ifEmpty { it.roomId },
                        protocol = "",
                        roomId = it.roomId,
                        nickname = it.nickname
                    )
                }
            }
        }
    }

    private fun loadChatsFromUri(uri: Uri, results: MutableList<SettingsBeeperContact>, foundRoomIds: MutableSet<String>) {
        contentResolver.query(
            uri,
            arrayOf("roomId", "title", "timestamp", "oneToOne", "protocol", "senderEntityId"),
            null, null, "timestamp DESC"
        )?.use { c ->
            val roomIdIdx = c.getColumnIndexOrThrow("roomId")
            val titleIdx = c.getColumnIndex("title")
            val tsIdx = c.getColumnIndexOrThrow("timestamp")
            val oneToOneIdx = c.getColumnIndex("oneToOne")
            val protocolIdx = c.getColumnIndex("protocol")
            val senderIdx = c.getColumnIndex("senderEntityId")
            while (c.moveToNext()) {
                val roomId = c.getString(roomIdIdx)
                val title = if (titleIdx >= 0) c.getStringOrNull(titleIdx) else null
                val timestamp = c.getLong(tsIdx)
                val isOneToOne = if (oneToOneIdx >= 0) c.getInt(oneToOneIdx) == 1 else true
                val protocol = if (protocolIdx >= 0) c.getStringOrNull(protocolIdx) ?: "" else ""
                val senderId = if (senderIdx >= 0) c.getStringOrNull(senderIdx) else null
                foundRoomIds.add(roomId)
                results.add(SettingsBeeperContact(
                    id = senderId ?: roomId,
                    name = title ?: roomId,
                    protocol = protocol,
                    roomId = roomId,
                    chatTitle = if (!isOneToOne) title else null,
                    isGroupChat = !isOneToOne,
                    lastMessageTimestamp = timestamp
                ))
            }
        }
    }

    actual fun getContacts(query: String?): PagingSource<Int, SettingsBeeperContact> {
        return BeeperContactsPagingSource(contentResolver, query, _approvedIds.value)
    }

    actual fun addContact(roomId: String, contact: SettingsBeeperContact) {
        Log.d(TAG, "addContact: roomId=$roomId")
        val updated = HashSet(_approvedIds.value)
        updated.add(roomId)
        _approvedIds.value = updated
        val currentList = _approvedContacts.value.toMutableList()
        if (currentList.none { it.roomId == roomId }) {
            currentList.add(0, contact)
            _approvedContacts.value = currentList
        }
    }

    actual fun removeContact(roomId: String) {
        Log.d(TAG, "removeContact: roomId=$roomId")
        val updated = HashSet(_approvedIds.value)
        updated.remove(roomId)
        _approvedIds.value = updated
        _approvedContacts.value = _approvedContacts.value.filter { (it.roomId ?: it.id) != roomId }
    }

    actual fun setNickname(roomId: String, nickname: String?) {
        Log.d(TAG, "setNickname: roomId=$roomId, nickname=$nickname")
        _approvedContacts.value = _approvedContacts.value.map { contact ->
            if ((contact.roomId ?: contact.id) == roomId) {
                contact.copy(nickname = nickname)
            } else contact
        }
    }

    actual fun persist() {
        viewModelScope.launch {
            // Start from the saved contacts as the base to preserve entries
            // that Beeper didn't resolve this session
            val savedByRoom = prefs.approvedBeeperContacts.value.associateBy { it.roomId }
            val resolvedByRoom = _approvedContacts.value.associate { contact ->
                val roomId = contact.roomId ?: contact.id
                roomId to ApprovedBeeperContact(
                    roomId = roomId,
                    name = contact.name,
                    nickname = contact.nickname
                )
            }
            // Merge: use resolved data where available, keep saved data for unresolved,
            // but only for IDs still in the approved set
            val approvedIds = _approvedIds.value
            val merged = approvedIds.mapNotNull { id ->
                resolvedByRoom[id] ?: savedByRoom[id]
            }
            prefs.setApprovedBeeperContacts(merged.ifEmpty { null })
        }
    }

    companion object {
        private const val TAG = "BeeperContacts"
    }
}

class BeeperContactsPagingSource(
    private val contentResolver: ContentResolver,
    private val query: String?,
    private val approvedIds: Set<String>
) : PagingSource<Int, SettingsBeeperContact>() {

    companion object {
        private const val TAG = "BeeperContacts"
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SettingsBeeperContact> {
        val offset = params.key ?: 0
        val limit = params.loadSize

        return try {
            val contacts: List<ContactWithTimestamp> = if (query.isNullOrBlank()) {
                withContext(Dispatchers.IO) { fetchChatsDirectly(limit, offset) }
            } else {
                withContext(Dispatchers.IO) { fetchContactsWithTimestamps(limit, offset) }
            }

            // Deduplicate by roomId
            val seenRoomIds = mutableSetOf<String>()
            val deduped = contacts.filter { c ->
                val roomId = c.roomId
                if (roomId == null) true else seenRoomIds.add(roomId)
            }

            // Sort: approved first (keyed by roomId), then by recency
            val sorted = deduped.sortedWith(
                compareByDescending<ContactWithTimestamp> { approvedIds.contains(it.roomId ?: it.id) }
                    .thenByDescending { it.timestamp }
            )

            val result = sorted.map {
                SettingsBeeperContact(
                    id = it.id,
                    name = it.name,
                    protocol = it.protocol,
                    roomId = it.roomId,
                    chatTitle = it.chatTitle,
                    isGroupChat = it.isGroupChat,
                    lastMessageTimestamp = it.timestamp
                )
            }

            LoadResult.Page(
                data = result,
                prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                nextKey = if (contacts.isEmpty()) null else offset + contacts.size
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission denied querying Beeper")
            LoadResult.Error(e)
        } catch (e: Exception) {
            Log.e(TAG, "load() failed: ${e.message}", e)
            LoadResult.Error(e)
        }
    }

    private data class ChatInfo(
        val roomId: String,
        val timestamp: Long,
        val title: String?,
        val isOneToOne: Boolean
    )

    private data class ContactWithTimestamp(
        val id: String,
        val name: String,
        val protocol: String,
        val roomId: String?,
        val chatTitle: String?,
        val isGroupChat: Boolean,
        val timestamp: Long
    )

    /** Fast path: query chats API directly, already sorted by timestamp DESC */
    private fun fetchChatsDirectly(limit: Int, offset: Int): List<ContactWithTimestamp> {
        val uri = BeeperAPI.CHATS_URI.toUri().buildUpon()
            .appendQueryParameter("limit", limit.toString())
            .appendQueryParameter("offset", offset.toString())
            .build()

        val results = mutableListOf<ContactWithTimestamp>()

        contentResolver.query(
            uri,
            arrayOf("roomId", "title", "timestamp", "oneToOne", "protocol", "senderEntityId"),
            null, null, "timestamp DESC"
        )?.use { c ->
            val roomIdIdx = c.getColumnIndexOrThrow("roomId")
            val titleIdx = c.getColumnIndex("title")
            val tsIdx = c.getColumnIndexOrThrow("timestamp")
            val oneToOneIdx = c.getColumnIndex("oneToOne")
            val protocolIdx = c.getColumnIndex("protocol")
            val senderIdx = c.getColumnIndex("senderEntityId")

            while (c.moveToNext()) {
                val roomId = c.getString(roomIdIdx)
                val title = if (titleIdx >= 0) c.getStringOrNull(titleIdx) else null
                val timestamp = c.getLong(tsIdx)
                val isOneToOne = if (oneToOneIdx >= 0) c.getInt(oneToOneIdx) == 1 else true
                val protocol = if (protocolIdx >= 0) c.getStringOrNull(protocolIdx) ?: "" else ""
                val senderId = if (senderIdx >= 0) c.getStringOrNull(senderIdx) else null

                results.add(ContactWithTimestamp(
                    id = senderId ?: roomId,
                    name = title ?: roomId,
                    protocol = protocol,
                    roomId = roomId,
                    chatTitle = if (!isOneToOne) title else null,
                    isGroupChat = !isOneToOne,
                    timestamp = timestamp
                ))
            }
        }

        return results
    }

    /** Slow path: search contacts by name, then resolve each to their latest chat */
    private fun fetchContactsWithTimestamps(limit: Int, offset: Int): List<ContactWithTimestamp> {
        val uriBuilder = BeeperAPI.CONTACTS_URI.toUri().buildUpon()
            .appendQueryParameter("limit", limit.toString())
            .appendQueryParameter("offset", offset.toString())

        if (!query.isNullOrBlank()) {
            uriBuilder.appendQueryParameter("query", query)
        }

        val uri = uriBuilder.build()
        val contacts = mutableListOf<ContactWithTimestamp>()

        contentResolver.query(uri, arrayOf("id", "displayName", "protocol", "roomIds"), null, null, null)?.use { c ->
            val idIdx = c.getColumnIndexOrThrow("id")
            val nameIdx = c.getColumnIndexOrThrow("displayName")
            val protocolIdx = c.getColumnIndexOrThrow("protocol")
            val roomIdsIdx = c.getColumnIndex("roomIds")

            while (c.moveToNext()) {
                val id = c.getString(idIdx)
                val name = c.getString(nameIdx)
                val protocol = c.getString(protocolIdx)
                val roomIds = if (roomIdsIdx >= 0) c.getStringOrNull(roomIdsIdx) else null

                val chatInfo = if (!roomIds.isNullOrBlank()) {
                    getLatestChatInfo(roomIds)
                } else null

                contacts.add(ContactWithTimestamp(
                    id = id,
                    name = name,
                    protocol = protocol,
                    roomId = chatInfo?.roomId,
                    chatTitle = chatInfo?.title,
                    isGroupChat = chatInfo?.isOneToOne == false,
                    timestamp = chatInfo?.timestamp ?: 0L
                ))
            }
        }

        return contacts
    }

    /** Returns ChatInfo for the most recent one-to-one chat, or most recent group chat as fallback */
    private fun getLatestChatInfo(roomIds: String): ChatInfo? {
        val chatsUri = BeeperAPI.CHATS_URI.toUri().buildUpon()
            .appendQueryParameter("roomIds", roomIds)
            .build()

        var bestChat: ChatInfo? = null

        contentResolver.query(
            chatsUri,
            arrayOf("roomId", "timestamp", "oneToOne", "title"),
            null, null, "timestamp DESC"
        )?.use { c ->
            val roomIdIdx = c.getColumnIndexOrThrow("roomId")
            val tsIdx = c.getColumnIndexOrThrow("timestamp")
            val oneToOneIdx = c.getColumnIndex("oneToOne")
            val titleIdx = c.getColumnIndex("title")

            while (c.moveToNext()) {
                val roomId = c.getString(roomIdIdx)
                val timestamp = c.getLong(tsIdx)
                val isOneToOne = if (oneToOneIdx >= 0) c.getInt(oneToOneIdx) == 1 else true
                val title = if (titleIdx >= 0) c.getStringOrNull(titleIdx) else null

                if (isOneToOne) {
                    return ChatInfo(roomId, timestamp, title, true)
                }
                if (bestChat == null || timestamp > bestChat!!.timestamp) {
                    bestChat = ChatInfo(roomId, timestamp, title, false)
                }
            }
        }

        return bestChat
    }

    override fun getRefreshKey(state: PagingState<Int, SettingsBeeperContact>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(state.config.pageSize)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(state.config.pageSize)
        }
    }
}
