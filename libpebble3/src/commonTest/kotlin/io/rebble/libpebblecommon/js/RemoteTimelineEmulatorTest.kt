package io.rebble.libpebblecommon.js

import io.rebble.libpebblecommon.database.entity.BaseAttribute
import io.rebble.libpebblecommon.packets.blobdb.TimelineAttribute
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import io.rebble.libpebblecommon.util.PebbleColor
import io.rebble.libpebblecommon.util.toProtocolNumber
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private val json = Json { ignoreUnknownKeys = true }
private val APP_UUID = Uuid.parse("90a6af17-25ca-409f-be60-7efab77345ee")
private val PIN_UUID = Uuid.parse("9c29c20c-1c1c-43f6-9f9a-2b90a596c15e")

private fun pinFrom(pinJson: String) =
    asPin(json.decodeFromString(TimelinePinJson.serializer(), pinJson), APP_UUID, PIN_UUID)

private fun List<BaseAttribute>.text(attribute: TimelineAttribute): String? =
    filterIsInstance<BaseAttribute.TextAttribute>().find { it.attribute == attribute }?.text

private fun List<BaseAttribute>.uByte(attribute: TimelineAttribute): UByte? =
    filterIsInstance<BaseAttribute.UByteAttribute>().find { it.attribute == attribute }?.value

private fun List<BaseAttribute>.color(attribute: TimelineAttribute): PebbleColor? =
    filterIsInstance<BaseAttribute.ColorAttribute>().find { it.attribute == attribute }?.color

private fun List<BaseAttribute>.icon(attribute: TimelineAttribute): TimelineIcon? =
    filterIsInstance<BaseAttribute.IconAttribute>().find { it.attribute == attribute }?.icon

class RemoteTimelineEmulatorTest {
    /** The pin from MOB-6823: everything below title/subtitle used to be dropped. */
    @Test
    fun sportsPinKeepsScoreboardAttributes() {
        val pin = pinFrom(
            """
            {
              "id": "sports-test-1",
              "time": "2026-09-15T23:30:00Z",
              "layout": {
                "type": "sportsPin",
                "title": "Boston Red Sox at Texas Rangers",
                "subtitle": "Top 9th",
                "tinyIcon": "system://images/TIMELINE_BASEBALL",
                "largeIcon": "system://images/TIMELINE_BASEBALL",
                "lastUpdated": "2026-09-15T23:25:00Z",
                "rankAway": "3",
                "rankHome": "8",
                "nameAway": "BOS",
                "nameHome": "TEX",
                "recordAway": "82-69",
                "recordHome": "75-76",
                "scoreAway": "2",
                "scoreHome": "4",
                "sportsGameState": "in-game",
                "broadcaster": "NBA TV"
              }
            }
            """.trimIndent()
        )!!
        assertEquals(TimelineItem.Layout.SportsPin, pin.content.layout)
        val attributes = pin.content.attributes
        assertEquals("Boston Red Sox at Texas Rangers", attributes.text(TimelineAttribute.Title))
        assertEquals("Top 9th", attributes.text(TimelineAttribute.Subtitle))
        assertEquals("3", attributes.text(TimelineAttribute.RankAway))
        assertEquals("8", attributes.text(TimelineAttribute.RankHome))
        assertEquals("BOS", attributes.text(TimelineAttribute.NameAway))
        assertEquals("TEX", attributes.text(TimelineAttribute.NameHome))
        assertEquals("82-69", attributes.text(TimelineAttribute.RecordAway))
        assertEquals("75-76", attributes.text(TimelineAttribute.RecordHome))
        assertEquals("2", attributes.text(TimelineAttribute.ScoreAway))
        assertEquals("4", attributes.text(TimelineAttribute.ScoreHome))
        assertEquals("NBA TV", attributes.text(TimelineAttribute.Broadcaster))
        assertEquals(TimelineIcon.TimelineBaseball, attributes.icon(TimelineAttribute.TinyIcon))
        assertEquals(TimelineIcon.TimelineBaseball, attributes.icon(TimelineAttribute.LargeIcon))
    }

    /** uint8 on the wire, and the watch renders scores rather than names only when it isn't pre-game. */
    @Test
    fun sportsGameStateIsEncodedAsAByte() {
        fun stateOf(value: String) = pinFrom(
            """{"id":"a","time":"2026-09-15T23:30:00Z",
                "layout":{"type":"sportsPin","title":"t","sportsGameState":"$value"}}"""
        )!!.content.attributes.uByte(TimelineAttribute.SportsGameState)

        assertEquals(0u.toUByte(), stateOf("pre-game"))
        assertEquals(1u.toUByte(), stateOf("in-game"))
        assertNull(stateOf("half-time"))
    }

    @Test
    fun coloursParseFromHexAndName() {
        val pin = pinFrom(
            """
            {
              "id": "colours",
              "time": "2026-09-15T23:30:00Z",
              "layout": {
                "type": "genericPin",
                "title": "t",
                "primaryColor": "#FFFFFF",
                "secondaryColor": "IslamicGreen",
                "backgroundColor": "#AA0000"
              }
            }
            """.trimIndent()
        )!!
        val attributes = pin.content.attributes
        assertEquals(
            PebbleColor(0xFFu, 0xFFu, 0xFFu, 0xFFu),
            attributes.color(TimelineAttribute.ForegroundColor),
        )
        assertEquals(
            PebbleColor(0xFFu, 0x00u, 0xAAu, 0x00u),
            attributes.color(TimelineAttribute.SecondaryColor),
        )
        assertEquals(
            PebbleColor(0xFFu, 0xAAu, 0x00u, 0x00u),
            attributes.color(TimelineAttribute.BackgroundColor),
        )
        // ARGB2222 is what actually reaches the watch.
        assertEquals(
            0b11100000u.toUByte(),
            attributes.color(TimelineAttribute.BackgroundColor)!!.toProtocolNumber(),
        )
    }

    /** `foregroundColor` is documented as an alias for `primaryColor`. */
    @Test
    fun foregroundColorIsAnAliasForPrimaryColor() {
        val pin = pinFrom(
            """{"id":"a","time":"2026-09-15T23:30:00Z",
                "layout":{"type":"genericPin","title":"t","foregroundColor":"#FF0000"}}"""
        )!!
        assertEquals(
            PebbleColor(0xFFu, 0xFFu, 0x00u, 0x00u),
            pin.content.attributes.color(TimelineAttribute.ForegroundColor),
        )
    }

    @Test
    fun unparseableValuesAreDroppedRatherThanFailingThePin() {
        val pin = pinFrom(
            """
            {
              "id": "junk",
              "time": "2026-09-15T23:30:00Z",
              "layout": {
                "type": "genericPin",
                "title": "Survives",
                "primaryColor": "puce",
                "tinyIcon": "system://images/NOT_A_REAL_ICON",
                "displayTime": "sometimes"
              }
            }
            """.trimIndent()
        )!!
        val attributes = pin.content.attributes
        assertEquals("Survives", attributes.text(TimelineAttribute.Title))
        assertNull(attributes.color(TimelineAttribute.ForegroundColor))
        assertNull(attributes.icon(TimelineAttribute.TinyIcon))
        assertNull(attributes.uByte(TimelineAttribute.DisplayTime))
    }

    @Test
    fun weatherPinKeepsLocationAndDisplayTime() {
        val pin = pinFrom(
            """
            {
              "id": "weather-1",
              "time": "2026-09-15T23:30:00Z",
              "layout": {
                "type": "weatherPin",
                "title": "Sunny",
                "subtitle": "22C",
                "locationName": "London",
                "shortTitle": "Sun",
                "shortSubtitle": "22C",
                "displayTime": "none",
                "tinyIcon": "system://images/TIMELINE_SUN"
              }
            }
            """.trimIndent()
        )!!
        assertEquals(TimelineItem.Layout.WeatherPin, pin.content.layout)
        val attributes = pin.content.attributes
        assertEquals("London", attributes.text(TimelineAttribute.LocationName))
        assertEquals("Sun", attributes.text(TimelineAttribute.ShortTitle))
        assertEquals("22C", attributes.text(TimelineAttribute.ShortSubtitle))
        assertEquals(0u.toUByte(), attributes.uByte(TimelineAttribute.DisplayTime))
    }

    @Test
    fun calendarPinKeepsSenderAndRecurrence() {
        val pin = pinFrom(
            """
            {
              "id": "cal-1",
              "time": "2026-09-15T23:30:00Z",
              "layout": {
                "type": "calendarPin",
                "title": "Standup",
                "locationName": "Room 3",
                "sender": "Ada",
                "displayRecurring": "recurring"
              }
            }
            """.trimIndent()
        )!!
        assertEquals(TimelineItem.Layout.CalendarPin, pin.content.layout)
        val attributes = pin.content.attributes
        assertEquals("Room 3", attributes.text(TimelineAttribute.LocationName))
        assertEquals("Ada", attributes.text(TimelineAttribute.Sender))
        assertEquals(1u.toUByte(), attributes.uByte(TimelineAttribute.DisplayRecurring))
    }

    /** Reminders go through the same layout mapping as their parent pin. */
    @Test
    fun remindersGetLayoutAttributesToo() {
        val reminder = asReminder(
            json.decodeFromString(
                TimelineReminderJson.serializer(),
                """{"time":"2026-09-15T23:15:00Z",
                    "layout":{"type":"genericReminder","title":"Soon","locationName":"Room 3"}}""",
            ),
            PIN_UUID,
        )!!
        assertEquals(TimelineItem.Layout.GenericReminder, reminder.content.layout)
        assertEquals("Soon", reminder.content.attributes.text(TimelineAttribute.Title))
        assertEquals("Room 3", reminder.content.attributes.text(TimelineAttribute.LocationName))
    }

    @Test
    fun unknownLayoutTypeIsRejected() {
        assertNull(
            pinFrom(
                """{"id":"a","time":"2026-09-15T23:30:00Z","layout":{"type":"spacePin","title":"t"}}"""
            )
        )
    }

    /**
     * [BaseAttribute.asAttribute] is where a text attribute with no declared max length blows up,
     * and it only runs when the pin is serialised for blobdb - long after the mapping above.
     */
    @Test
    fun everyMappedAttributeSurvivesSerialisationToTheWatch() {
        val pin = pinFrom(
            """
            {
              "id": "everything",
              "time": "2026-09-15T23:30:00Z",
              "duration": 60,
              "layout": {
                "type": "sportsPin",
                "title": "t", "subtitle": "s", "body": "b",
                "shortTitle": "st", "shortSubtitle": "ss",
                "locationName": "here", "sender": "me",
                "subtitleTemplateString": "{time_diff}",
                "tinyIcon": "system://images/TIMELINE_BASEBALL",
                "smallIcon": "system://images/TIMELINE_BASEBALL",
                "largeIcon": "system://images/TIMELINE_BASEBALL",
                "primaryColor": "#FFFFFF", "secondaryColor": "#000000",
                "backgroundColor": "#AA0000",
                "headings": ["h1"], "paragraphs": ["p1"],
                "lastUpdated": "2026-09-15T23:25:00Z",
                "displayTime": "pin", "displayRecurring": "recurring",
                "rankAway": "3", "rankHome": "8",
                "nameAway": "BOS", "nameHome": "TEX",
                "recordAway": "82-69", "recordHome": "75-76",
                "scoreAway": "2", "scoreHome": "4",
                "sportsGameState": "in-game", "broadcaster": "NBA TV"
              }
            }
            """.trimIndent()
        )!!
        val attributes = pin.content.attributes
        assertEquals(29, attributes.size)
        attributes.forEach { it.asAttribute() }
    }

    /** Every text attribute needs a max length, or [BaseAttribute.asAttribute] rejects it. */
    @Test
    fun textAttributesDeclareAMaxLength() {
        val textAttributes = listOf(
            TimelineAttribute.Title,
            TimelineAttribute.Subtitle,
            TimelineAttribute.Body,
            TimelineAttribute.ShortTitle,
            TimelineAttribute.ShortSubtitle,
            TimelineAttribute.LocationName,
            TimelineAttribute.Sender,
            TimelineAttribute.SubtitleTemplateString,
            TimelineAttribute.RankAway,
            TimelineAttribute.RankHome,
            TimelineAttribute.NameAway,
            TimelineAttribute.NameHome,
            TimelineAttribute.RecordAway,
            TimelineAttribute.RecordHome,
            TimelineAttribute.ScoreAway,
            TimelineAttribute.ScoreHome,
            TimelineAttribute.Broadcaster,
        )
        textAttributes.forEach {
            assertTrue(it.maxLength > 0, "$it has no max length, so it cannot hold text")
        }
    }

    /** Every icon code we advertise has to match the id the firmware resolves. */
    @Test
    fun timelineIconCodesAreUnique() {
        val codes = TimelineIcon.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        val ids = TimelineIcon.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(TimelineIcon.entries.all { it.code.startsWith("system://images/") })
    }
}
