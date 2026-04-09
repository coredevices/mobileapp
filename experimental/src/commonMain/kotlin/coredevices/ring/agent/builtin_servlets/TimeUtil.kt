package coredevices.ring.agent.builtin_servlets

import kotlinx.datetime.LocalDateTime

object TimeUtil {
    private fun getNextOfDay(day: String, currentTime: LocalDateTime): Int {
        val dayOfWeek = currentTime.dayOfWeek
        val dayNum = dayOfWeek.ordinal+1
        val daysUntil = when (day.lowercase()) {
            "sunday" -> 7 - dayNum
            "monday" -> 1 - dayNum
            "tuesday" -> 2 - dayNum
            "wednesday" -> 3 - dayNum
            "thursday" -> 4 - dayNum
            "friday" -> 5 - dayNum
            "saturday" -> 6 - dayNum
            else -> throw IllegalArgumentException("Invalid day: $day")
        }
        return if (daysUntil < 0) daysUntil + 7 else daysUntil
    }

    fun calculateDayOffset(input: String?, currentTime: LocalDateTime) = when (input?.lowercase()) {
        "today" -> 0
        "tomorrow" -> 1
        "sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday" -> getNextOfDay(input, currentTime)
        else -> input?.toIntOrNull() ?: 0
    }
}