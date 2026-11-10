package coredevices.analytics

import PlatformContext
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.mixpanel.android.mpmetrics.MixpanelOptions
import coredevices.util.CommonBuildKonfig
import org.json.JSONObject

// Second Mixpanel SDK instance sending the same events to our collector (BigQuery).
private const val COLLECTOR_INSTANCE = "collector"
private const val COLLECTOR_URL = "https://events.cluster.repebble.com"

fun createAndroidAnalytics(platformContext: PlatformContext): AnalyticsBackend {
    val token = CommonBuildKonfig.MIXPANEL_TOKEN
    if (token == null) {
        return AndroidAnalyticsBackend(emptyList())
    }
    // Options apply before the SDK starts, so its first automatic events and uploads use them.
    val mixpanel = MixpanelAPI.getInstance(
        platformContext.context, token, true,
        MixpanelOptions.Builder().superProperties(destinations()).build(),
    )
    val collector = MixpanelAPI.getInstance(
        platformContext.context, token, true,
        MixpanelOptions.Builder().instanceName(COLLECTOR_INSTANCE).superProperties(destinations()).serverURL(COLLECTOR_URL).build(),
    )
    return AndroidAnalyticsBackend(listOf(mixpanel, collector))
}

class AndroidAnalyticsBackend(
    private val instances: List<MixpanelAPI>,
) : AnalyticsBackend {
    // Kept here rather than read back from the SDK: reset() and opt-out drop super properties.
    private var firebaseUid: String? = null

    override fun logEvent(
        name: String,
        parameters: Map<String, Any>?,
    ) {
        instances.forEach { it.trackMap(name, parameters) }
    }

    @Synchronized
    override fun addGlobalProperty(name: String, value: String?) {
        if (name == PROP_FIREBASE_UID) firebaseUid = value
        instances.forEach { mixpanel ->
            if (name == PROP_EMAIL) {
                if (value == null) {
                    mixpanel.reset()
                    mixpanel.registerSuperProperties(baseProperties())
                } else {
                    mixpanel.identify(value)
                }
            } else {
                if (value == null) {
                    mixpanel.unregisterSuperProperty(name)
                } else {
                    mixpanel.registerSuperPropertiesMap(mapOf(name to value))
                }
            }
        }
    }

    @Synchronized
    override fun setEnabled(enabled: Boolean) {
        instances.forEach { mixpanel ->
            if (enabled) {
                mixpanel.optInTracking(null, baseProperties())
                mixpanel.registerSuperProperties(baseProperties()) // opt-out cleared it
            } else {
                mixpanel.flush()
                mixpanel.optOutTracking()
            }
        }
    }

    private fun baseProperties() = destinations().putOpt(PROP_FIREBASE_UID, firebaseUid)
}

// Lets BigQuery combine Mixpanel and collector rows without double counting.
private fun destinations() = JSONObject(mapOf("analytics_destinations" to "mixpanel,collector"))
