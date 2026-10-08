package io.rebble.libpebblecommon.js

import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.serialization.json.Json
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JSLocalStorageInterfaceTest {
    private val settings: Settings = PropertiesSettings(Properties())
    private var length = -1
    private val storage = object : JSLocalStorageInterface("test-uuid", AppContext(), settings) {
        override fun setLength(value: Int) {
            length = value
        }
    }

    @Test
    fun saveStateWritesNewKeysAndDropsMissingOnes() {
        storage.setItem("keep", "old")
        storage.setItem("drop", "gone")

        storage.saveState("""{"keep":"new","added":"value","nil":null}""")

        assertEquals("new", storage.getItem("keep"))
        assertEquals("value", storage.getItem("added"))
        assertEquals("null", storage.getItem("nil"))
        assertNull(storage.getItem("drop"))
        assertEquals(3, length)
    }

    @Test
    fun saveStateRefusesToEmptyANonEmptyStore() {
        storage.setItem("options", "{\"token\":\"secret\"}")

        storage.saveState("{}")

        assertEquals("{\"token\":\"secret\"}", storage.getItem("options"))
        assertEquals(setOf("options"), settings.keys)
    }

    @Test
    fun saveStateAcceptsAnEmptySnapshotAfterTheAppClearedTheStore() {
        storage.setItem("options", "value")
        // What the shimmed localStorage.clear() does before stop() persists
        storage.clear()

        storage.saveState("{}")

        assertEquals(emptySet(), settings.keys)
        assertEquals(0, length)
    }

    @Test
    fun restoreStateRoundTrips() {
        storage.saveState("""{"a":"1","b":"2"}""")

        val restored = Json.decodeFromString<Map<String, String?>>(storage.restoreState())
        assertEquals(mapOf("a" to "1", "b" to "2"), restored)
    }
}
