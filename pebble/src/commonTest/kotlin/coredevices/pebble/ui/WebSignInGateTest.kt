package coredevices.pebble.ui

import coredevices.pebble.services.PEBBLE_FEED_URL
import coredevices.pebble.services.REBBLE_FEED_URL
import io.rebble.libpebblecommon.locker.AppCapability
import io.rebble.libpebblecommon.locker.AppProperties
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.locker.LockerWrapper
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class WebSignInGateTest {
    private fun app(
        capabilities: List<AppCapability> = listOf(AppCapability.WebSignIn),
        sideloaded: Boolean = false,
        pbwUrl: String? = "$PEBBLE_FEED_URL/assets/pbw/app/1.0.1/x.pbw",
    ) = LockerWrapper.NormalApp(
        properties = AppProperties(
            id = Uuid.random(), type = AppType.Plugin, title = "Plugin", developerName = "Pebble",
            developerId = null, platforms = emptyList(), version = "1.0.1", hearts = null, category = null,
            iosCompanion = null, androidCompanion = null, order = 0, sourceLink = null, storeId = null,
            capabilities = capabilities, pbwUrl = pbwUrl,
        ),
        sideloaded = sideloaded,
        configurable = true,
        sync = false,
    )

    @Test
    fun onlyAnAppFromOurStoreWithTheCapability() {
        assertTrue(app().mayUseWebSignIn(debugBuild = false))
        assertFalse(app(capabilities = emptyList()).mayUseWebSignIn(debugBuild = false))
        assertFalse(app(pbwUrl = "$REBBLE_FEED_URL/assets/pbw/x.pbw").mayUseWebSignIn(debugBuild = false))
        assertFalse(app(pbwUrl = "$PEBBLE_FEED_URL.evil.example/x.pbw").mayUseWebSignIn(debugBuild = false))
        assertFalse(app(sideloaded = true, pbwUrl = null).mayUseWebSignIn(debugBuild = false))
    }

    @Test
    fun debugBuildsAlsoTrustSideloadsThatAskForIt() {
        assertTrue(app(sideloaded = true, pbwUrl = null).mayUseWebSignIn(debugBuild = true))
        assertFalse(app(sideloaded = true, pbwUrl = null, capabilities = emptyList()).mayUseWebSignIn(debugBuild = true))
    }
}
