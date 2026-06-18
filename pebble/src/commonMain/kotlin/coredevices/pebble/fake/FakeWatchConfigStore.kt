package coredevices.pebble.fake

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val KEY_FAKE_WATCHES = "fake_watch.config.watches"
private const val KEY_FAKE_ACTIVE_WATCH = "fake_watch.config.active"

class FakeWatchConfigStore(private val settings: Settings) {
    private val _fakeWatches = MutableStateFlow(loadFakeWatches())
    val fakeWatches: StateFlow<Set<WatchHardwarePlatform>> = _fakeWatches.asStateFlow()

    private val _activeFakeWatch = MutableStateFlow(loadActiveFakeWatch())
    val activeFakeWatch: StateFlow<WatchHardwarePlatform?> = _activeFakeWatch.asStateFlow()

    fun getFakeWatches(): Set<WatchHardwarePlatform> = fakeWatches.value
    fun getActiveFakeWatch(): WatchHardwarePlatform? = activeFakeWatch.value

    fun setFakeWatches(watches: Set<WatchHardwarePlatform>) {
        settings[KEY_FAKE_WATCHES] = watches.joinToString(",") { it.revision }
        _fakeWatches.value = watches
    }

    fun setActiveFakeWatch(watch: WatchHardwarePlatform?) {
        if (watch == null) {
            settings.remove(KEY_FAKE_ACTIVE_WATCH)
        } else {
            settings[KEY_FAKE_ACTIVE_WATCH] = watch.revision
        }
        _activeFakeWatch.value = watch
    }

    fun addFakeWatches(watches: Set<WatchHardwarePlatform>) {
        if (watches.isEmpty()) return
        val updated = _fakeWatches.value + watches
        setFakeWatches(updated)
        if (_activeFakeWatch.value == null) {
            setActiveFakeWatch(watches.first())
        }
    }

    fun removeFakeWatch(watch: WatchHardwarePlatform) {
        val updated = _fakeWatches.value - watch
        setFakeWatches(updated)
        if (_activeFakeWatch.value == watch) {
            setActiveFakeWatch(updated.firstOrNull())
        }
    }

    private fun loadFakeWatches(): Set<WatchHardwarePlatform> {
        val revisions = settings.getStringOrNull(KEY_FAKE_WATCHES)
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?: return emptySet()
        return revisions.mapNotNull { revision ->
            WatchHardwarePlatform.entries.firstOrNull { it.revision == revision }
        }.toSet()
    }

    private fun loadActiveFakeWatch(): WatchHardwarePlatform? {
        val revision = settings.getStringOrNull(KEY_FAKE_ACTIVE_WATCH) ?: return null
        return WatchHardwarePlatform.entries.firstOrNull { it.revision == revision }
    }
}
