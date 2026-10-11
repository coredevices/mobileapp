package coredevices.pebble.services

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StorePluginLockerEntryTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val watch = """{"supported":true,"firmware":{"major":3}}"""

    // Trimmed from the live store's Stocks listing (a plugin-only app).
    private val stocks = """{"author":"Pebble","capabilities":[],"category":"Finance & News","category_color":"7ED321",
        "category_id":"706c7567696e000000000004","changelog":[],"companions":{"android":null,"ios":null},
        "compatibility":{"aplite":$watch,"basalt":$watch,"chalk":$watch,"diorite":$watch,"emery":$watch,
          "flint":$watch,"gabbro":$watch,"android":{"supported":true},"ios":{"supported":true}},
        "hardware_platforms":[],"created_at":"2026-10-05T20:17:46.203","description":"Share prices.",
        "developer_id":"d","header_images":[],"hearts":0,"icon_image":{"48x48":"https://icon"},
        "id":"730a1d95229d42e9b66098cb","latest_release":{"id":"r","js_md5":null,"js_version":-1,
          "pbw_file":"https://store/stocks.pbw","published_date":null,"release_notes":null,"version":"1.0.0"},
        "links":{"add":"","add_flag":"","add_heart":"","remove":"","remove_flag":"","remove_heart":"","share":""},
        "list_image":{"144x144":"https://list"},"published_date":null,"screenshot_hardware":"basalt",
        "screenshot_images":[],"source":null,"title":"Stocks","type":"plugin",
        "uuid":"2c4b7f10-9a3d-4e6b-8f21-5d0c7e9a1b34","visible":true,"website":null}"""

    @Test
    fun aPluginOnlyAppKeepsItsDescriptionAndImagesInTheLocker() {
        val entry = json.decodeFromString(StoreApplication.serializer(), stocks)
            .toLockerEntry(sourceUrl = "https://store/api", timelineToken = null)!!
        assertEquals(7, entry.hardwarePlatforms.size)
        assertTrue(entry.hardwarePlatforms.all { it.description == "Share prices." && it.images.list == "https://list" })
    }

    @Test
    fun aPluginWithAConfigPageIsConfigurable() {
        val withConfigPage = stocks.replace(""""title":"Stocks",""", """"plugin":{"config_page":"config.html"},"title":"Stocks",""")
        fun configurable(app: String) = json.decodeFromString(StoreApplication.serializer(), app)
            .toLockerEntry(sourceUrl = "https://store/api", timelineToken = null)!!.isConfigurable
        assertFalse(configurable(stocks))
        assertTrue(configurable(withConfigPage))
    }
}
