package coredevices.ring.agent

import coredevices.indexai.agent.systemone.SystemOneAnswer
import coredevices.indexai.agent.systemone.SystemOneException
import coredevices.indexai.agent.systemone.decide
import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.ring.api.DecisionAnswer
import coredevices.ring.api.DecisionQuestion
import coredevices.ring.api.DecisionResponse
import coredevices.ring.api.DecisionResult
import coredevices.ring.api.NenyaClient
import coredevices.ring.api.NenyaModel
import coredevices.ring.api.RunResult
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NenyaSystemOneAgentTest {
    private class DecidingNenyaClient(private val result: DecisionResult) : NenyaClient {
        var state: JsonElement? = null
        var questions: Map<String, DecisionQuestion>? = null
        var model: String? = null

        override suspend fun run(
            conversationHistory: List<ConversationMessageDocument>,
            toolSpecs: List<ToolDeclaration>,
            additionalContext: String,
            timezone: TimeZone,
            model: NenyaModel,
        ): RunResult = error("not called")

        override suspend fun decide(
            state: JsonElement,
            questions: Map<String, DecisionQuestion>,
            model: String?,
        ): DecisionResult {
            this.state = state
            this.questions = questions
            this.model = model
            return result
        }
    }

    private fun ok(answers: Map<String, DecisionAnswer>) =
        DecisionResult(HttpStatusCode.OK, DecisionResponse(success = true, answers = answers))

    @Test
    fun mapsQuestionsToWireFormAndAnswersBack() = runTest {
        val client = DecidingNenyaClient(
            ok(
                mapOf(
                    "is_reminder" to DecisionAnswer.Noul(0.97),
                    "spam" to DecisionAnswer.Noul(0.01),
                    "intent" to DecisionAnswer.Choice("reminder", 0.9, mapOf("reminder" to 0.9, "timer" to 0.1)),
                    "urgency" to DecisionAnswer.Score(0.8, 0.8, mapOf(0 to "low", 1 to "high"), mapOf(0 to 0.2, 1 to 0.8)),
                )
            )
        )

        val answers = NenyaSystemOneAgent(client, model = "jev-test").decide("Remind me to call the dentist") {
            yesNo("is_reminder", "Is the user asking to be reminded?")
            yesNo("spam") { yes = "Advertising" }
            choice("intent", "What does the user want?") {
                "reminder" means "Wants a reminder"
                option("timer")
            }
            score("urgency") {
                level("low")
                level("high")
            }
        }

        assertEquals(JsonPrimitive("Remind me to call the dentist"), client.state)
        assertEquals("jev-test", client.model)
        assertEquals(
            mapOf(
                "is_reminder" to DecisionQuestion.Noul("Is the user asking to be reminded?"),
                "spam" to DecisionQuestion.Noul(criteria = DecisionQuestion.Noul.Criteria(yes = "Advertising")),
                "intent" to DecisionQuestion.Choice(
                    mapOf("reminder" to "Wants a reminder", "timer" to null),
                    "What does the user want?",
                ),
                "urgency" to DecisionQuestion.Score(listOf("low", "high")),
            ),
            client.questions,
        )
        assertEquals(SystemOneAnswer.YesNo(0.97), answers.yesNo("is_reminder"))
        assertEquals(SystemOneAnswer.YesNo(0.01), answers.yesNo("spam"))
        assertEquals(
            SystemOneAnswer.Choice("reminder", 0.9, mapOf("reminder" to 0.9, "timer" to 0.1)),
            answers.choice("intent"),
        )
        assertEquals(SystemOneAnswer.Score(0.8, 0.8, mapOf(0 to 0.2, 1 to 0.8)), answers.score("urgency"))
    }

    @Test
    fun failsWhenRequestFails() = runTest {
        val agent = NenyaSystemOneAgent(DecidingNenyaClient(DecisionResult(HttpStatusCode.BadGateway, null)))

        assertFailsWith<SystemOneException> { agent.decide("hi") { yesNo("q") } }
    }

    @Test
    fun failsWhenResponseIsUnsuccessful() = runTest {
        val agent = NenyaSystemOneAgent(
            DecidingNenyaClient(DecisionResult(HttpStatusCode.OK, DecisionResponse(success = false, message = "nope")))
        )

        assertFailsWith<SystemOneException> { agent.decide("hi") { yesNo("q") } }
    }

    @Test
    fun failsWhenAnAnswerIsMissing() = runTest {
        val agent = NenyaSystemOneAgent(DecidingNenyaClient(ok(emptyMap())))

        assertFailsWith<SystemOneException> { agent.decide("hi") { yesNo("q") } }
    }
}
