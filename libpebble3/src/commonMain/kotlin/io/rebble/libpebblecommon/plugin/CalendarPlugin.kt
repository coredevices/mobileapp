package io.rebble.libpebblecommon.plugin

import io.rebble.libpebblecommon.calendar.CalendarEvent
import io.rebble.libpebblecommon.calendar.SystemCalendar
import io.rebble.libpebblecommon.connection.endpointmanager.blobdb.TimeProvider
import io.rebble.libpebblecommon.database.dao.CalendarDao
import io.rebble.libpebblecommon.database.entity.CalendarEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.sample
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Built-in calendar plugin. One instance per upcoming event across the user's enabled calendars,
 * soonest first, so a watchface binding `instanceId` "0" always has the next event.
 */
class CalendarPlugin(
    private val systemCalendar: SystemCalendar,
    private val calendarDao: CalendarDao,
    private val timeProvider: TimeProvider,
) : Plugin {
    override val pluginUuid: Uuid = BUILT_IN_CALENDAR_UUID
    override val name: String = "Calendar"

    override val sources: List<SourceDeclaration> = listOf(
        SourceDeclaration(
            category = CATEGORY,
            items = listOf(ITEM_EVENT),
            properties = mapOf(
                SourceInstance.PROPERTY_NAME to TEXT_SHAPES,
                PROPERTY_LOCATION to TEXT_SHAPES,
                PROPERTY_STARTS_AT to TEXT_SHAPES + SourceShapeNames.TIMESTAMP,
                PROPERTY_ENDS_AT to
                    listOf(SourceShapeNames.SHORT_TEXT, SourceShapeNames.TIMESTAMP),
                PROPERTY_ALL_DAY to listOf(SourceShapeNames.BOOLEAN),
                PROPERTY_CALENDAR to listOf(SourceShapeNames.SHORT_TEXT),
            ),
            supportsMultiple = true,
            usesPermissions = listOf(PluginPermission(PERMISSION)),
            suggestedRefreshIntervalSec = 300,
        ),
    )

    override fun observe(
        category: String,
        item: String,
        properties: List<String>?,
        iconPixelSize: IconPixelSize?,
    ): Flow<SourceEnvelope> {
        if (!serves(category, item)) return emptyFlow()
        val changes = systemCalendar.registerForCalendarChanges()?.sample(CHANGE_DEBOUNCE)
            ?: emptyFlow()
        // Events fall out of the window with the passage of time alone, so a change signal isn't
        // enough on its own to keep "the next event" true.
        return combine(calendarDao.getFlow(), merge(tick(REFRESH_INTERVAL), changes)) { cals, _ ->
            toEnvelope(load(cals))
        }.distinctUntilChanged()
    }

    private suspend fun load(calendars: List<CalendarEntity>): List<EventInCalendar> {
        if (!systemCalendar.hasPermission()) return emptyList()
        val now = timeProvider.now()
        return calendars
            .filter { it.enabled }
            .flatMap { calendar ->
                systemCalendar.getCalendarEvents(calendar, now, now + WINDOW)
                    .map { EventInCalendar(it, calendar.name) }
            }
            .upcoming(now, MAX_EVENTS)
    }

    private fun toEnvelope(events: List<EventInCalendar>) = SourceEnvelope(
        pluginUuid = pluginUuid.toString(),
        validUntilMs = null,
        instances = events.mapIndexed { index, event -> toInstance(index, event) },
    )

    private fun toInstance(index: Int, entry: EventInCalendar): SourceInstance {
        val event = entry.event
        val startsAt = if (event.allDay) ALL_DAY_LABEL else event.startTime.localTime()
        return SourceInstance(
            instanceId = index.toString(),
            properties = buildMap {
                put(SourceInstance.PROPERTY_NAME, textShapes(event.title))
                event.location?.takeIf { it.isNotBlank() }?.let {
                    put(PROPERTY_LOCATION, textShapes(it))
                }
                // The rendered time reads on any tile; the timestamp lets a watchface count
                // down to it, which it can only do with the raw moment.
                put(
                    PROPERTY_STARTS_AT,
                    textShapes(startsAt) +
                        (SourceShapeNames.TIMESTAMP to
                            encode(TimestampShape(event.startTime.epochSeconds))),
                )
                put(
                    PROPERTY_ENDS_AT,
                    mapOf(
                        SourceShapeNames.SHORT_TEXT to encode(
                            ShortTextShape(
                                if (event.allDay) ALL_DAY_LABEL else event.endTime.localTime()
                            )
                        ),
                        SourceShapeNames.TIMESTAMP to
                            encode(TimestampShape(event.endTime.epochSeconds)),
                    ),
                )
                put(
                    PROPERTY_ALL_DAY,
                    mapOf(
                        SourceShapeNames.BOOLEAN to encode(BooleanShape(event.allDay)),
                    ),
                )
                put(
                    PROPERTY_CALENDAR,
                    mapOf(
                        SourceShapeNames.SHORT_TEXT to encode(ShortTextShape(entry.calendarName)),
                    ),
                )
            },
        )
    }

    private fun textShapes(reading: String) = mapOf(
        SourceShapeNames.SHORT_TEXT to encode(ShortTextShape(reading)),
        SourceShapeNames.LONG_TEXT to encode(LongTextShape(reading)),
    )

    private fun tick(interval: Duration) = flow {
        while (true) {
            emit(Unit)
            delay(interval)
        }
    }

    private inline fun <reified T> encode(value: T): JsonElement = Json.encodeToJsonElement(value)

    companion object {
        const val CATEGORY = "calendar"
        const val ITEM_EVENT = "event"
        const val PROPERTY_LOCATION = "location"
        const val PROPERTY_STARTS_AT = "starts_at"
        const val PROPERTY_ENDS_AT = "ends_at"
        const val PROPERTY_ALL_DAY = "all_day"
        const val PROPERTY_CALENDAR = "calendar"

        private const val PERMISSION = "Calendar"
        private const val ALL_DAY_LABEL = "All day"
        private const val MAX_EVENTS = 10
        private val WINDOW = 7.days
        private val REFRESH_INTERVAL = 1.minutes
        private val CHANGE_DEBOUNCE = 5.seconds
        private val TEXT_SHAPES =
            listOf(SourceShapeNames.SHORT_TEXT, SourceShapeNames.LONG_TEXT)

        // Reserved built-in UUID namespace: prefix 00000000-0000-0000-0001-* for built-ins.
        val BUILT_IN_CALENDAR_UUID: Uuid = Uuid.parse("00000000-0000-0000-0001-000000000004")
    }
}

internal data class EventInCalendar(val event: CalendarEvent, val calendarName: String)

/** Ordered by start, but selected on end: an event you are in the middle of is still upcoming. */
internal fun List<EventInCalendar>.upcoming(now: Instant, limit: Int): List<EventInCalendar> =
    filter { it.event.endTime > now }
        .sortedBy { it.event.startTime }
        .take(limit)

private fun Instant.localTime(): String {
    val local = toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

private fun Instant.localDay(): String =
    toLocalDateTime(TimeZone.currentSystemDefault()).dayOfWeek.name
        .lowercase()
        .replaceFirstChar { it.uppercase() }
        .take(3)
