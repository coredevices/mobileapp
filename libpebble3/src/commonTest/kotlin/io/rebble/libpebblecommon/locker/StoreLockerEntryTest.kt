package io.rebble.libpebblecommon.locker

import io.rebble.libpebblecommon.plugin.PluginManifest
import io.rebble.libpebblecommon.web.LockerEntry
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoreLockerEntryTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val watch = """{"supported":true,"firmware":{"major":3}}"""
    private val phone = """{"supported":true}"""

    private fun entry(extra: String) = json.decodeFromString(
        LockerEntry.serializer(),
        """{"id":"abc","uuid":"0d520cba-326c-4a22-a269-d1ab1ae38977","title":"Todoist","type":"plugin",
           "category":"Productivity","hearts":0,"is_configurable":false,"is_timeline_enabled":false,
           "links":{"remove":"","href":"","share":""},"developer":{"id":"d","name":"Dev","contact_email":""},
           "hardware_platforms":[],"companions":{"android":null,"ios":null},
           "compatibility":{"ios":$phone,"android":$phone,"aplite":$watch,"basalt":$watch,"chalk":$watch,
             "diorite":$watch,"emery":$watch}$extra}""",
    )

    private val manifest = """"plugin_manifest":{"description":"Todoist","unknownField":1,
        "sources":[{"category":"reminders","items":["list"]}]}"""

    @Test
    fun aStorePluginIsStoredAsASideloadedOneWouldBe() {
        val entity = entry(""","manifest_version":2,$manifest,"config_page":"config.html"""").asEntity(0)
        assertEquals(2, entity.manifestVersion)
        assertEquals("config.html", entity.configPage)
        val stored = Json.decodeFromString(PluginManifest.serializer(), entity.pluginManifest!!)
        assertEquals("reminders", stored.sources.single().category)
    }

    @Test
    fun aLegacyOrOtherStoresAppHasNoPlugin() {
        assertNull(entry(""","manifest_version":1,$manifest""").asEntity(0).pluginManifest)
        val other = entry("").asEntity(0)
        assertEquals(1, other.manifestVersion)
        assertNull(other.pluginManifest)
    }

    @Test
    fun anUnreadableManifestIsDropped() {
        assertNull(entry(""","manifest_version":2,"plugin_manifest":{"sources":"nope"}""").asEntity(0).pluginManifest)
    }
}
