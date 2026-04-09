package coredevices.ring.external.vermillion

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class VermillionPreferences(private val settings: Settings) {

    private val _widgetToken = MutableStateFlow(settings.getStringOrNull("vermillion_widget_token"))
    val widgetToken = _widgetToken.asStateFlow()

    fun setWidgetToken(token: String?) {
        if (token != null) {
            settings.putString("vermillion_widget_token", token)
        } else {
            settings.remove("vermillion_widget_token")
        }
        _widgetToken.value = token
    }
}
