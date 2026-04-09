package coredevices.ring.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.paging.PagingSource

actual class SettingsBeeperContactsDialogViewModel actual constructor() :
ViewModel() {
    actual fun getApprovedContacts(query: String?): PagingSource<Int, SettingsBeeperContact> {
        throw NotImplementedError()
    }

    actual fun setContactApproved(contactId: String, approved: Boolean) {
    }
}