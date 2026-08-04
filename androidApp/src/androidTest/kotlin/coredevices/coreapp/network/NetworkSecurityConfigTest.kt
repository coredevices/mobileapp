package coredevices.coreapp.network

import android.security.NetworkSecurityPolicy
import androidx.test.filters.SdkSuppress
import org.junit.Test
import kotlin.test.assertTrue

class NetworkSecurityConfigTest {
    @Test
    @SdkSuppress(minSdkVersion = 24)
    fun cleartextTrafficIsPermittedForNonLoopbackHosts() {
        assertTrue(
            NetworkSecurityPolicy.getInstance()
                .isCleartextTrafficPermitted("192.0.2.1"),
            "The Network Security Config must preserve the manifest's cleartext opt-in",
        )
    }
}
