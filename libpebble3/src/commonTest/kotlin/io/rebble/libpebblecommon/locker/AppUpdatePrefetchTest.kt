package io.rebble.libpebblecommon.locker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class AppUpdatePrefetchTest {
    private fun uuid(n: Int) = Uuid.fromLongs(0, n.toLong())

    @Test
    fun `prefetches only apps whose new version is uncached but an old version is cached`() {
        val staleUpdate = AppVersionState(uuid(1), currentVersion = "2.0", sideloaded = false, cachedVersions = listOf("1.0"))
        val upToDate = AppVersionState(uuid(2), currentVersion = "2.0", sideloaded = false, cachedVersions = listOf("2.0"))
        val neverCached = AppVersionState(uuid(3), currentVersion = "2.0", sideloaded = false, cachedVersions = emptyList())
        val sideloadedUpdate = AppVersionState(uuid(4), currentVersion = "2.0", sideloaded = true, cachedVersions = listOf("1.0"))

        val result = appsNeedingUpdatePrefetch(listOf(staleUpdate, upToDate, neverCached, sideloadedUpdate))

        assertEquals(listOf(uuid(1)), result)
    }

    @Test
    fun `handles multiple stale cached versions`() {
        val state = AppVersionState(uuid(1), currentVersion = "3.0", sideloaded = false, cachedVersions = listOf("1.0", "2.0"))
        assertEquals(listOf(uuid(1)), appsNeedingUpdatePrefetch(listOf(state)))
    }
}
