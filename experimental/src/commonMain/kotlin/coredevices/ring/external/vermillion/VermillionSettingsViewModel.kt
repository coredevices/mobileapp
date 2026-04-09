package coredevices.ring.external.vermillion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class VermillionSettingsViewModel(
    private val vermillionPreferences: VermillionPreferences
) : ViewModel() {

    private val _token = MutableStateFlow<String?>(null)
    val token = _token.asStateFlow()

    private val _dialogOpen = MutableStateFlow(false)
    val dialogOpen = _dialogOpen.asStateFlow()

    private val _tokenInput = MutableStateFlow("")
    val tokenInput = _tokenInput.asStateFlow()

    val isLinked: Boolean
        get() = _token.value != null

    init {
        viewModelScope.launch {
            vermillionPreferences.widgetToken.collectLatest { token ->
                _token.value = token?.ifBlank { null }
            }
        }
    }

    fun openDialog() {
        _tokenInput.value = _token.value ?: ""
        _dialogOpen.value = true
    }

    fun closeDialog() {
        _dialogOpen.value = false
    }

    fun updateTokenInput(token: String) {
        _tokenInput.value = token
    }

    fun saveToken() {
        viewModelScope.launch {
            val token = _tokenInput.value.ifBlank { null }?.trim()
            vermillionPreferences.setWidgetToken(token)
            closeDialog()
        }
    }

    fun clearToken() {
        viewModelScope.launch {
            vermillionPreferences.setWidgetToken(null)
            closeDialog()
        }
    }
}
