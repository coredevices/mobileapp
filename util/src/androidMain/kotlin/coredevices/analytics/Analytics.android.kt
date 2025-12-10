package coredevices.analytics

import PlatformContext
import com.mixpanel.android.mpmetrics.MixpanelAPI
import coredevices.util.CommonBuildKonfig

fun createAndroidAnalytics(platformContext: PlatformContext): AnalyticsBackend {
    val token = CommonBuildKonfig.MIXPANEL_TOKEN
    if (token == null) {
        return AndroidAnalyticsBackend(null)
    }
    val mixpanel = MixpanelAPI.getInstance(platformContext.context, CommonBuildKonfig.MIXPANEL_TOKEN, true)
    return AndroidAnalyticsBackend(mixpanel)
}

class AndroidAnalyticsBackend(
    private val mixpanel: MixpanelAPI?,
) : AnalyticsBackend {
    override fun logEvent(
        name: String,
        parameters: Map<String, Any>?,
    ) {
        mixpanel?.trackMap(name, parameters)
    }

    override fun addGlobalProperty(name: String, value: String?) {
        if (name == PROP_EMAIL) {
            if (value == null) {
                mixpanel?.reset()
            } else {
                mixpanel?.identify(value)
            }
        } else {
            if (value == null) {
                mixpanel?.unregisterSuperProperty(name)
            } else {
                mixpanel?.registerSuperPropertiesOnceMap(mapOf(name to value))
            }
        }
    }

    override fun setEnabled(enabled: Boolean) {
        if (enabled) {
            mixpanel?.optInTracking()
        } else {
            mixpanel?.optOutTracking()
        }
    }
}
