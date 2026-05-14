package coredevices.ring.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual class SettingsBeeperContactsDialogViewModel actual constructor() : ViewModel() {
    actual fun getContacts(query: String?): PagingSource<Int, SettingsBeeperContact> {
        throw NotImplementedError()
    }

    actual val approvedIds: StateFlow<Set<String>> = MutableStateFlow(emptySet())
    actual val approvedContacts: StateFlow<List<SettingsBeeperContact>> = MutableStateFlow(emptyList())
    actual val hasPermission: StateFlow<Boolean> = MutableStateFlow(true)

    actual fun addContact(roomId: String, contact: SettingsBeeperContact) {
    }

    actual fun removeContact(roomId: String) {
    }

    actual fun setNickname(roomId: String, nickname: String?) {
    }

    actual fun persist() {
    }

    actual fun refreshPermission() {
    }

    actual fun loadApprovedContacts() {
    }
}
