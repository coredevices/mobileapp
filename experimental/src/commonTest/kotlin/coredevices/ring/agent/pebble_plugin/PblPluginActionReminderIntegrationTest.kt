package coredevices.ring.agent.pebble_plugin

import coredevices.ring.agent.integrations.ReminderListEntry
import io.rebble.libpebblecommon.plugin.ActionDeclaration
import io.rebble.libpebblecommon.plugin.ActionResult
import io.rebble.libpebblecommon.plugin.PluginErrors
import io.rebble.libpebblecommon.plugin.SourceDeclaration
import io.rebble.libpebblecommon.plugin.SourceEnvelope
import io.rebble.libpebblecommon.plugin.SourceInstance
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private val losAngeles = TimeZone.of("America/Los_Angeles")

private val listSource = SourceDeclaration(category = "reminders", items = listOf("list"))

private fun list(id: String, name: String) = SourceInstance(
    instanceId = id,
    properties = mapOf("name" to mapOf("shortText" to JsonObject(mapOf("text" to JsonPrimitive(name))))),
)

private fun integrationFor(plugin: FakePlugin) =
    PblPluginActionReminderIntegration(plugin.metadata, FakePlugins(plugin), losAngeles)

private fun tickTickPlugin(
    result: ActionResult = ActionResult(ok = true, text = "task-1"),
    sources: List<SourceDeclaration> = emptyList(),
    lists: List<SourceInstance> = emptyList(),
    targets: List<String> = listOf("reminders/list"),
) = FakePlugin(
    actions = listOf(ActionDeclaration(name = "create_reminder", targets = targets)),
    name = "TickTick",
    result = result,
    sources = sources,
    envelope = SourceEnvelope(pluginUuid = "p", instances = lists),
)

class PblPluginActionReminderIntegrationTest {

    @Test
    fun dueTimeCarriesTheLocalWallTimeAndRealOffset() = runTest {
        val plugin = tickTickPlugin()

        val id = integrationFor(plugin).createReminder(
            title = "Call dentist",
            deadline = Instant.parse("2026-08-27T21:30:00Z"),
            listId = "list-7",
            notifyBefore = 1.hours,
        )

        assertEquals("task-1", id)
        assertEquals(
            listOf(
                "create_reminder" to JsonObject(
                    mapOf(
                        "title" to JsonPrimitive("Call dentist"),
                        "due_iso" to JsonPrimitive("2026-08-27T14:30:00-07:00"),
                        "timezone" to JsonPrimitive("America/Los_Angeles"),
                        "notify_before_ms" to JsonPrimitive(3_600_000L),
                        "instanceId" to JsonPrimitive("list-7"),
                    )
                )
            ),
            plugin.invoked,
        )
    }

    @Test
    fun offsetFollowsDaylightSaving() = runTest {
        val plugin = tickTickPlugin()

        integrationFor(plugin)
            .createReminder("Winter", Instant.parse("2026-12-01T17:00:00Z"))

        assertEquals(JsonPrimitive("2026-12-01T09:00:00-08:00"), plugin.invoked.single().second["due_iso"])
    }

    @Test
    fun withoutADeadlineOnlyTheTitleIsSent() = runTest {
        val plugin = tickTickPlugin()

        integrationFor(plugin).createReminder("Buy milk", null, notifyBefore = 1.hours)

        assertEquals(
            listOf("create_reminder" to JsonObject(mapOf("title" to JsonPrimitive("Buy milk")))),
            plugin.invoked,
        )
    }

    @Test
    fun aFailedActionThrows() = runTest {
        val plugin = tickTickPlugin(ActionResult.error(PluginErrors.INVALID_ARGS, "bad due_iso"))

        val e = assertFailsWith<IllegalStateException> {
            integrationFor(plugin).createReminder("Buy milk", null)
        }
        assertEquals("bad due_iso", e.message)
    }

    @Test
    fun listsComeFromTheTargetedSource() = runTest {
        val plugin = tickTickPlugin(
            sources = listOf(listSource),
            lists = listOf(list("inbox", "Inbox"), list("work", "Work"), SourceInstance("unnamed", emptyMap())),
        )

        assertEquals(
            listOf(ReminderListEntry("inbox", "Inbox"), ReminderListEntry("work", "Work")),
            integrationFor(plugin).getAllLists(),
        )
    }

    @Test
    fun searchFiltersTheListsByName() = runTest {
        val plugin = tickTickPlugin(
            sources = listOf(listSource),
            lists = listOf(list("inbox", "Inbox"), list("work", "Work projects")),
        )

        assertEquals(
            listOf(ReminderListEntry("work", "Work projects")),
            integrationFor(plugin).searchForList("work"),
        )
    }

    @Test
    fun aPluginWithoutTheListSourceHasNoLists() = runTest {
        val plugin = tickTickPlugin(lists = listOf(list("inbox", "Inbox")))

        assertEquals(emptyList(), integrationFor(plugin).getAllLists())
    }

    @Test
    fun theActionTargetNamesTheListSource() = runTest {
        val plugin = tickTickPlugin(
            sources = listOf(SourceDeclaration(category = "tasks", items = listOf("project"))),
            lists = listOf(list("p1", "Personal")),
            targets = listOf("tasks/project/name"),
        )

        assertEquals(
            listOf(ReminderListEntry("p1", "Personal")),
            integrationFor(plugin).getAllLists(),
        )
    }
}
