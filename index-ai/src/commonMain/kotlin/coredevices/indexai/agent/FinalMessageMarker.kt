package coredevices.indexai.agent

/**
 * Marker the model prefixes its final message of a turn with, so the harness can tell an
 * answer the user should see from a bare completion notice.
 */
object FinalMessageMarker {
    const val QUERY_ANSWER = "<query_answer>"
    const val ACTION_COMPLETE = "<action_complete>"

    val PROMPT_INSTRUCTION = """
## Final message:
 - Start your final message of a turn (once no more tools need calling) with exactly one marker: '$QUERY_ANSWER' when the message answers a question or gives the user requested information, or '$ACTION_COMPLETE' when you only carried out actions. For example '${ACTION_COMPLETE}Done' or '${QUERY_ANSWER}Your next meeting is at 3pm.'
""".trimIndent()

    data class Parsed(val isQueryAnswer: Boolean, val text: String?)

    fun parse(content: String?): Parsed {
        val trimmed = content?.trim() ?: return Parsed(false, null)
        val marker = listOf(QUERY_ANSWER, ACTION_COMPLETE).firstOrNull { trimmed.startsWith(it) }
            ?: return Parsed(false, content)
        val text = trimmed.removePrefix(marker)
            .removeSuffix(marker.replaceFirst("<", "</"))
            .trim()
        return Parsed(isQueryAnswer = marker == QUERY_ANSWER, text = text.ifEmpty { null })
    }
}
