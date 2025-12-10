package coredevices.analytics

interface AnalyticsBackend {
    fun logEvent(name: String, parameters: Map<String, Any>? = null)
    fun addGlobalProperty(name: String, value: String?)
    fun setEnabled(enabled: Boolean)
}

const val PROP_EMAIL = "email"

fun AnalyticsBackend.setUser(email: String?) = addGlobalProperty(PROP_EMAIL, email)