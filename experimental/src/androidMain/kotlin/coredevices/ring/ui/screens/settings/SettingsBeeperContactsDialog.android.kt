package coredevices.ring.ui.screens.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingSource
import androidx.paging.PagingState
import coredevices.ring.agent.builtin_servlets.messaging.BeeperAPI
import coredevices.ring.database.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

actual class SettingsBeeperContactsDialogViewModel actual constructor() : ViewModel(), KoinComponent {
    private val context: Context by inject()
    private val contentResolver: ContentResolver by lazy { context.contentResolver }
    private val prefs: Preferences by inject()

    actual fun getApprovedContacts(query: String?): PagingSource<Int, SettingsBeeperContact> {
        return BeeperContactsPagingSource(
            contentResolver,
            query
        )
    }

    actual fun setContactApproved(contactId: String, approved: Boolean) {
        val current = prefs.approvedBeeperContacts.value.toMutableSet()
        if (approved) {
            current.add(contactId)
        } else {
            current.remove(contactId)
        }
        viewModelScope.launch {
            prefs.setApprovedBeeperContacts(current.toList())
        }
    }
}

class BeeperContactsPagingSource(
    private val contentResolver: ContentResolver,
    private val query: String?
) : PagingSource<Int, SettingsBeeperContact>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SettingsBeeperContact> {
        val offset = params.key ?: 0
        val limit = params.loadSize

        val uriBuilder = BeeperAPI.CONTACTS_URI.toUri().buildUpon()
            .appendQueryParameter("limit", limit.toString())
            .appendQueryParameter("offset", offset.toString())

        if (!query.isNullOrBlank()) {
            uriBuilder.appendQueryParameter("query", Uri.encode(query))
        }

        val uri = uriBuilder.build()

        return try {
            val proj = arrayOf("id", "displayName", "protocol")
            val cursor = contentResolver.query(uri, proj, null, null, null)
            val contacts = mutableListOf<SettingsBeeperContact>()
            cursor?.use { c ->
                val idIdx = c.getColumnIndexOrThrow("id")
                val nameIdx = c.getColumnIndexOrThrow("displayName")
                val protocolIdx = c.getColumnIndexOrThrow("protocol")

                while (c.moveToNext()) {
                    val id = c.getString(idIdx)
                    val name = c.getString(nameIdx)
                    val protocol = c.getString(protocolIdx)
                    contacts.add(SettingsBeeperContact(id = id, name = name, protocol = protocol) )
                }
            }

            LoadResult.Page(
                data = contacts,
                prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                nextKey = if (contacts.isEmpty()) null else offset + contacts.size
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, SettingsBeeperContact>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(state.config.pageSize)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(state.config.pageSize)
        }
    }
}
