import androidx.compose.runtime.MutableState
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppUpdateTracker(
    private val settings: Settings,
    private val appVersion: CoreAppVersion,
) {
    private val _appWasUpdated = MutableStateFlow(initialValue())
    val appWasUpdated = _appWasUpdated.asStateFlow()

    private fun initialValue(): Boolean {
        val lastAcknowledgedVersion = settings.getStringOrNull(LAST_ACKNOWLEDGED_VERSION)
        return lastAcknowledgedVersion != null && appVersion.version != lastAcknowledgedVersion
    }

    fun acknowledgeCurrentVersion() {
        settings[LAST_ACKNOWLEDGED_VERSION] = appVersion.version
        _appWasUpdated.value = false
    }

    companion object {
        private const val LAST_ACKNOWLEDGED_VERSION = "last_acknowledged_version"
    }
}