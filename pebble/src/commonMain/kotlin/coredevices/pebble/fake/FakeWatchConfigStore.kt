package coredevices.pebble.fake

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform

private const val KEY_FAKE_WATCHES = "fake_watch.config.watches"
private const val KEY_FAKE_ACTIVE_WATCH = "fake_watch.config.active"

class FakeWatchConfigStore(private val settings: Settings) {
    fun getFakeWatches(): Set<WatchHardwarePlatform> {
        val revisions = settings.getStringOrNull(KEY_FAKE_WATCHES)
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?: return emptySet()
        return revisions.mapNotNull { revision ->
            WatchHardwarePlatform.entries.firstOrNull { it.revision == revision }
        }.toSet()
    }

    fun setFakeWatches(watches: Set<WatchHardwarePlatform>) {
        settings[KEY_FAKE_WATCHES] = watches.joinToString(",") { it.revision }
    }

    fun getActiveFakeWatch(): WatchHardwarePlatform? {
        val revision = settings.getStringOrNull(KEY_FAKE_ACTIVE_WATCH) ?: return null
        return WatchHardwarePlatform.entries.firstOrNull { it.revision == revision }
    }

    fun setActiveFakeWatch(watch: WatchHardwarePlatform?) {
        if (watch == null) {
            settings.remove(KEY_FAKE_ACTIVE_WATCH)
        } else {
            settings[KEY_FAKE_ACTIVE_WATCH] = watch.revision
        }
    }

    fun addFakeWatches(watches: Set<WatchHardwarePlatform>) {
        if (watches.isEmpty()) return
        val current = getFakeWatches()
        val updated = current + watches
        setFakeWatches(updated)
        if (getActiveFakeWatch() == null) {
            setActiveFakeWatch(watches.first())
        }
    }

    fun removeFakeWatch(watch: WatchHardwarePlatform) {
        val current = getFakeWatches()
        val updated = current - watch
        setFakeWatches(updated)
        if (getActiveFakeWatch() == watch) {
            setActiveFakeWatch(updated.firstOrNull())
        }
    }
}
