package coredevices.ring.agent.builtin_servlets.calendar

import co.touchlab.kermit.Logger
import coredevices.indexai.time.HumanDateTimeParser
import coredevices.indexai.time.InterpretedDateTime
import coredevices.indexai.util.JsonSnake
import coredevices.mcp.BuiltInMcpTool
import coredevices.mcp.SessionContext
import coredevices.mcp.data.SemanticResult
import coredevices.mcp.data.ToolCallResult
import coredevices.ring.agent.builtin_servlets.clock.SetTimerTool
import coredevices.ring.database.Preferences
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.toJson
import coredevices.util.Permission
import coredevices.util.PermissionRequester
import io.rebble.libpebblecommon.calendar.NewCalendarEvent
import io.rebble.libpebblecommon.connection.LibPebble
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class CreateCalendarEventTool : BuiltInMcpTool(
    definition = Tool(
        name = TOOL_NAME,
        description = TOOL_DESCRIPTION,
        inputSchema = ToolSchema(
            properties = JsonObject(
                mapOf(
                    "title" to JsonObject(
                        mapOf(
                            "type" to "string",
                            "description" to "The title of the calendar event e.g. 'Lunch with Sam'"
                        ).toJson()
                    ),
                    "start_date_time_human" to JsonObject(
                        mapOf(
                            "type" to "string",
                            "description" to "The start date and time in human readable format, use English keywords e.g. 'tomorrow at 3pm', 'next Monday at 9am', 'on July 5th at 14:30'"
                        ).toJson()
                    ),
                    "end_date_time_human" to JsonObject(
                        mapOf(
                            "type" to "string",
                            "description" to "If the user gives an explicit end time, the end time in human readable format e.g. 'at 5pm', 'tomorrow at 4pm'. Interpreted relative to the start. If omitted, the event defaults to 1 hour long."
                        ).toJson()
                    ),
                    "duration_human" to JsonObject(
                        mapOf(
                            "type" to "string",
                            "description" to "If the user gives a duration instead of an end time, the duration in human readable format e.g. '30 minutes', '1 hour', '1 hour and 30 minutes'. Takes precedence over end_date_time_human."
                        ).toJson()
                    ),
                    "location" to JsonObject(
                        mapOf(
                            "type" to "string",
                            "description" to "If provided by the user, the location of the event"
                        ).toJson()
                    ),
                    "description" to JsonObject(
                        mapOf(
                            "type" to "string",
                            "description" to "If provided by the user, additional notes/description for the event"
                        ).toJson()
                    ),
                )
            ),
            required = listOf("title", "start_date_time_human")
        )
    ),
    extraContext = """
        'create_calendar_event' is ONLY for requests where the user literally says the word
        "calendar" or "event" while asking to create one, e.g. "add an event to my calendar",
        "create a calendar event", "put an event on my calendar for Friday at 3pm". Decision rules:
        - The words "schedule", "create", "book", "meeting", "appointment" or "time" on their own
          NEVER trigger this tool. "schedule time with X", "schedule a meeting", "book an
          appointment" are NOT calendar requests — handle them with the reminder or note tools.
        - If the user says "remind me", wants a nudge/to-do, or is setting a timer/alarm, use the
          reminder/timer/alarm tools instead — never this one.
        - When a request is on the edge between a reminder and a calendar event, ALWAYS prefer the
          reminder tool.
        - A calendar event requires a clear, specific date and time SPOKEN BY THE USER. Never
          invent or guess a time. If the time is vague, ambiguous, or missing, do NOT create a
          calendar event — create a note instead.
        - Provide times in the user's local timezone. Use 'duration_human' for a stated duration,
          or 'end_date_time_human' for an explicit end time; omit both for a default 1-hour event.
        The tool itself rejects calls where the user's message does not contain "calendar" or
        "event" — do not attempt it for other phrasings.
    """.trimIndent()
), KoinComponent {
    private val libPebble: LibPebble by inject()
    private val permissionRequester: PermissionRequester by inject()
    private val preferences: Preferences by inject()

    companion object {
        const val TOOL_NAME = "create_calendar_event"
        const val TOOL_DESCRIPTION = "Add an event to the user's phone calendar at a specific date and time. " +
            "Use ONLY when the user literally says 'calendar' or 'event' while asking to create one " +
            "(e.g. 'add an event to my calendar', 'create a calendar event'). Words like 'schedule', " +
            "'create', 'book', 'meeting', 'appointment' or 'time' alone must NOT trigger this tool — " +
            "use the reminder/note tools for those. Do NOT use for reminders, to-dos, timers, or alarms. " +
            "Requires a clear date and time spoken by the user; never invent one."
        private val logger = Logger.withTag("CreateCalendarEventTool")
        val DEFAULT_DURATION = 1.hours

        /**
         * Deterministic trigger guard: the tool only runs when the user's message literally
         * contains "calendar" or "event(s)" (the LLM steering says the same, but mis-transcribed
         * or paraphrased requests were still slipping through — MOB-8701). Word-bounded so e.g.
         * "eventually" doesn't count. Fails closed when the message is null/blank — a request
         * we can't verify must not write to the user's calendar; the agent falls back to a note.
         */
        private val TRIGGER_WORDS = Regex("""\b(calendar|events?)\b""", RegexOption.IGNORE_CASE)
        fun mentionsCalendarOrEvent(userMessage: String?): Boolean =
            userMessage != null && TRIGGER_WORDS.containsMatchIn(userMessage)

        /** Parses a human date/time string to an absolute instant, reusing the shared timer resolution. */
        private fun parse(human: String, tz: TimeZone, now: Instant): Instant? {
            val interpreted: InterpretedDateTime =
                HumanDateTimeParser(timeZone = tz).parse(human) ?: return null
            return SetTimerTool.interpretedTimeToFireTime(interpreted, now, tz)
        }

        /**
         * Resolves the start/end instants for an event from human-readable inputs. The end (whether a
         * duration like "30 minutes" or an explicit time like "5pm") is interpreted **relative to the
         * start**, not to now. `durationHuman` wins over `endHuman`. End defaults to [DEFAULT_DURATION]
         * after start and is clamped to be strictly after start. Returns null if start can't be parsed.
         */
        fun resolveEventTimes(
            startHuman: String,
            endHuman: String?,
            durationHuman: String?,
            tz: TimeZone,
            now: Instant,
        ): Pair<Instant, Instant>? {
            val start = parse(startHuman, tz, now) ?: return null
            val end = when {
                durationHuman != null -> parse(durationHuman, tz, start)
                endHuman != null -> parse(endHuman, tz, start)
                else -> start + DEFAULT_DURATION
            }
            val safeEnd = if (end == null || end <= start) start + DEFAULT_DURATION else end
            return start to safeEnd
        }

        /**
         * True when [startHuman] gives a date but no specific time of day (e.g. "tomorrow",
         * "next Friday"). Such a request is too ambiguous to place a calendar event, so the
         * tool declines and the agent falls back to a note.
         */
        fun isDateOnly(startHuman: String, tz: TimeZone): Boolean =
            HumanDateTimeParser(timeZone = tz).parse(startHuman) is InterpretedDateTime.AbsoluteDate
    }

    @Serializable
    private data class CreateEventArgs(
        val title: String,
        val start_date_time_human: String,
        val end_date_time_human: String? = null,
        val duration_human: String? = null,
        val location: String? = null,
        val description: String? = null,
    )

    @Serializable
    private data class CreateEventResult(
        val success: Boolean,
        val errorMessage: String? = null,
        val eventId: String? = null,
    )

    private fun failure(message: String): ToolCallResult = ToolCallResult(
        JsonSnake.encodeToString(CreateEventResult(success = false, errorMessage = message)),
        SemanticResult.GenericFailure(message, llmRecoverable = true)
    )

    override suspend fun call(jsonInput: String, context: SessionContext): ToolCallResult {
        // Defense in depth: the servlet hides this tool when the Phone Calendar integration is
        // disconnected or calendar access is missing, but a stale or forced tool call could still
        // reach here — bail before touching the calendar (which on iOS would otherwise pop a
        // permission prompt from the background agent). Not LLM-recoverable.
        if (!preferences.phoneCalendarEnabled.value ||
            !permissionRequester.grantedPermissions.value.contains(Permission.Calendar)
        ) {
            return ToolCallResult(
                JsonSnake.encodeToString(CreateEventResult(success = false, errorMessage = "The Phone Calendar integration is not connected")),
                SemanticResult.GenericFailure("The Phone Calendar integration is not connected", llmRecoverable = false)
            )
        }

        // Trigger guard (MOB-8701): only act when the user literally said "calendar" or "event".
        // Anything else ("schedule time with Lincoln") belongs to the reminder/note tools.
        val userMessage = try {
            context.userMessageText.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Failed to resolve user message text for trigger guard" }
            null
        }
        if (!mentionsCalendarOrEvent(userMessage)) {
            return ToolCallResult(
                JsonSnake.encodeToString(
                    CreateEventResult(
                        success = false,
                        errorMessage = "The user did not explicitly ask for a calendar event " +
                            "(no mention of 'calendar' or 'event'). Use the reminder or note tools instead."
                    )
                ),
                SemanticResult.GenericFailure(
                    "User did not explicitly ask for a calendar event; use a reminder or note instead",
                    llmRecoverable = true
                )
            )
        }

        // Decode args only after the guards: a stale/forced call with malformed args must still
        // get the controlled failures above rather than a decode exception.
        val args = try {
            JsonSnake.decodeFromString<CreateEventArgs>(jsonInput)
        } catch (e: Exception) {
            return failure("Invalid arguments for create_calendar_event: ${e.message}")
        }

        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()

        // Product rule: a calendar event needs a specific time of day. A date-only request
        // ("tomorrow") is ambiguous — don't guess a clock time; bounce back so the agent makes a
        // note instead.
        if (isDateOnly(args.start_date_time_human, tz)) {
            return ToolCallResult(
                JsonSnake.encodeToString(
                    CreateEventResult(success = false, errorMessage = "The event time is ambiguous (no specific time of day). Make a note instead.")
                ),
                SemanticResult.GenericFailure("Event time is ambiguous; make a note instead", llmRecoverable = true)
            )
        }

        val (start, safeEnd) = resolveEventTimes(
            args.start_date_time_human, args.end_date_time_human, args.duration_human, tz, now
        ) ?: return failure("Failed to parse start date time: '${args.start_date_time_human}'")

        return try {
            val eventId = libPebble.createEvent(
                NewCalendarEvent(
                    title = args.title,
                    startTime = start,
                    endTime = safeEnd,
                    location = args.location,
                    description = args.description,
                )
            ) ?: return ToolCallResult(
                JsonSnake.encodeToString(CreateEventResult(success = false, errorMessage = "Could not create the event. The calendar may not be accessible.")),
                SemanticResult.GenericFailure("Could not create the calendar event")
            )
            ToolCallResult(
                JsonSnake.encodeToString(CreateEventResult(success = true, eventId = eventId)),
                SemanticResult.CalendarEventCreation(
                    title = args.title,
                    startTime = start,
                    endTime = safeEnd,
                    location = args.location,
                )
            )
        } catch (e: Exception) {
            logger.e(e) { "Failed to create calendar event" }
            ToolCallResult(
                JsonSnake.encodeToString(CreateEventResult(success = false, errorMessage = e.message)),
                SemanticResult.GenericFailure("Failed to create calendar event: ${e.message}")
            )
        }
    }
}
