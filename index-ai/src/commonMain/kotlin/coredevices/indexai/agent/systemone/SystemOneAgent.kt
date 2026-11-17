package coredevices.indexai.agent.systemone

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Answers fast structured questions about a piece of state, without tools or conversation. */
interface SystemOneAgent {
    /**
     * @param state What the questions are about: text, or a JSON object/array.
     * @param questions At least one question, with unique names.
     * @throws SystemOneException if the questions can't be answered; retrying won't help.
     * @throws kotlinx.io.IOException on network failure.
     */
    suspend fun decide(state: JsonElement, questions: List<SystemOneQuestion<*>>): SystemOneAnswers
}

class SystemOneException(message: String, cause: Throwable? = null) : Exception(message, cause)

suspend fun SystemOneAgent.decide(state: String, questions: List<SystemOneQuestion<*>>): SystemOneAnswers =
    decide(JsonPrimitive(state), questions)

suspend fun SystemOneAgent.decide(
    state: JsonElement,
    questions: SystemOneQuestionsBuilder.() -> Unit,
): SystemOneAnswers = decide(state, systemOneQuestions(questions))

suspend fun SystemOneAgent.decide(
    state: String,
    questions: SystemOneQuestionsBuilder.() -> Unit,
): SystemOneAnswers = decide(JsonPrimitive(state), systemOneQuestions(questions))
