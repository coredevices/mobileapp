package coredevices.ring.agent.pebble_plugin

import coredevices.ring.agent.integrations.providers.IntegrationSupport
import io.rebble.libpebblecommon.plugin.ActionDeclaration
import io.rebble.libpebblecommon.plugin.ActionResult
import io.rebble.libpebblecommon.plugin.PluginErrors
import io.rebble.libpebblecommon.plugin.SourceDeclaration
import io.rebble.libpebblecommon.plugin.SourceEnvelope
import io.rebble.libpebblecommon.plugin.SourceInstance
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private val PLUGIN_UUID = Uuid.parse("00000000-0000-0000-0002-00000000000d")

private val createNote = ActionDeclaration(name = "create_note")
private val createReminder = ActionDeclaration(name = "create_reminder")
private val next = ActionDeclaration(name = "next")

private fun configuredSource(category: String) = SourceDeclaration(category = category, items = listOf("configured"))

private val bothConfiguredSources = listOf(configuredSource("notes"), configuredSource("reminders"))

private fun thingsPlugin(
    actions: List<ActionDeclaration>,
    pluginUuid: Uuid = PLUGIN_UUID,
    result: ActionResult = ActionResult(ok = true, text = "note-1"),
    sources: List<SourceDeclaration> = bothConfiguredSources,
    envelopes: Map<String, SourceEnvelope> = emptyMap(),
) = FakePlugin(
    actions = actions,
    name = "Things",
    pluginUuid = pluginUuid,
    result = result,
    sources = sources,
    envelopes = envelopes,
)

private fun configured(value: Boolean) = SourceEnvelope(
    pluginUuid = "p",
    instances = listOf(
        SourceInstance(
            instanceId = "things",
            properties = mapOf("configured" to mapOf("boolean" to JsonObject(mapOf("value" to JsonPrimitive(value))))),
        )
    ),
)

class PblPluginIntegrationProviderTest {

    @Test
    fun pluginsWithoutNoteOrReminderActionsAreNotProviders() {
        assertNull(PblPluginIntegrationProvider.forPlugin(thingsPlugin(listOf(next))))
    }

    @Test
    fun supportFollowsTheDeclaredActions() {
        assertEquals(
            setOf(IntegrationSupport.Note),
            PblPluginIntegrationProvider.forPlugin(thingsPlugin(listOf(createNote, next)))?.supports
        )
        assertEquals(
            setOf(IntegrationSupport.Note, IntegrationSupport.Reminder),
            PblPluginIntegrationProvider.forPlugin(thingsPlugin(listOf(createNote, createReminder)))?.supports
        )
    }

    @Test
    fun aKindNeedsItsConfiguredSourceAsWellAsTheAction() {
        assertNull(
            PblPluginIntegrationProvider.forPlugin(thingsPlugin(listOf(createNote), sources = emptyList()))
        )
        assertEquals(
            setOf(IntegrationSupport.Reminder),
            PblPluginIntegrationProvider.forPlugin(
                thingsPlugin(listOf(createNote, createReminder), sources = listOf(configuredSource("reminders")))
            )?.supports
        )
        assertNull(
            PblPluginIntegrationProvider.forPlugin(thingsPlugin(listOf(next), sources = bothConfiguredSources))
        )
    }

    @Test
    fun identityComesFromThePlugin() {
        val provider = assertNotNull(PblPluginIntegrationProvider.forPlugin(thingsPlugin(listOf(createNote))))

        assertEquals("plugin:$PLUGIN_UUID", provider.id)
        assertEquals("Things", provider.title)
        assertEquals("Things", provider.notesTitle)
    }

    @Test
    fun providersForTheSamePluginAreEqual() {
        val a = PblPluginIntegrationProvider.forPlugin(thingsPlugin(listOf(createNote)))
        val b = PblPluginIntegrationProvider.forPlugin(thingsPlugin(listOf(createNote, createReminder)))
        val other = PblPluginIntegrationProvider.forPlugin(
            thingsPlugin(listOf(createNote), pluginUuid = Uuid.parse("00000000-0000-0000-0002-00000000000e"))
        )

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, other)
    }

    @Test
    fun theConfiguredSourceDecidesWhetherAPluginIsConnected() = runTest {
        fun provider(value: Boolean) = assertNotNull(
            PblPluginIntegrationProvider.forPlugin(
                thingsPlugin(listOf(createNote), envelopes = mapOf("notes/configured" to configured(value)))
            )
        )

        assertTrue(provider(true).isAuthorized())
        assertFalse(provider(false).isAuthorized())
    }

    @Test
    fun aConfiguredSourceThatNeverAnswersIsNotConnected() = runTest {
        val provider = assertNotNull(PblPluginIntegrationProvider.forPlugin(thingsPlugin(listOf(createNote))))

        assertFalse(provider.isAuthorized())
    }

    @Test
    fun eachKindIsConnectedByItsOwnConfiguredSource() = runTest {
        val provider = assertNotNull(
            PblPluginIntegrationProvider.forPlugin(
                thingsPlugin(
                    listOf(createNote, createReminder),
                    envelopes = mapOf(
                        "notes/configured" to configured(true),
                        "reminders/configured" to configured(false),
                    ),
                )
            )
        )

        assertTrue(provider.isAuthorized(IntegrationSupport.Note))
        assertFalse(provider.isAuthorized(IntegrationSupport.Reminder))
        assertFalse(provider.isAuthorized())
    }

    @Test
    fun creatingANoteInvokesTheCreateNoteAction() = runTest {
        val plugin = thingsPlugin(listOf(createNote))
        val provider = assertNotNull(PblPluginIntegrationProvider.forPlugin(plugin))

        val id = assertIs<PblPluginActionNoteIntegration>(provider.notes()).createNote("buy milk")

        assertEquals("note-1", id)
        assertEquals(listOf("create_note" to JsonObject(mapOf("text" to JsonPrimitive("buy milk")))), plugin.invoked)
    }

    @Test
    fun aFailedCreateNoteActionThrowsTheErrorMessage() = runTest {
        val plugin = thingsPlugin(
            listOf(createNote),
            result = ActionResult.error(PluginErrors.INVALID_ARGS, "Text is required"),
        )

        val e = assertFailsWith<IllegalStateException> {
            PblPluginActionNoteIntegration(plugin).createNote("buy milk")
        }
        assertEquals("Text is required", e.message)
    }
}
