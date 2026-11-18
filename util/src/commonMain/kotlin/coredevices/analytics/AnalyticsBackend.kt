package coredevices.analytics

interface AnalyticsBackend {
    fun logEvent(name: String, parameters: Map<String, Any>? = null)
    fun addGlobalProperty(name: String, value: String?)
    fun setEnabled(enabled: Boolean)
}

const val PROP_EMAIL = "email"
const val PROP_FIREBASE_UID = "firebase_uid"

fun AnalyticsBackend.setUser(email: String?) = addGlobalProperty(PROP_EMAIL, email)