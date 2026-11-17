package coredevices.ring.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class NenyaDecisionSerializationTest {
    // Mirrors the Json configured in ApiClient.
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun requestEncodesQuestionsInWireForm() {
        val request = DecisionRequestData(
            state = JsonPrimitive("Remind me to call the dentist"),
            questions = mapOf(
                "is_reminder" to DecisionQuestion.Noul("Is the user asking to be reminded?"),
                "spam" to DecisionQuestion.Noul(criteria = DecisionQuestion.Noul.Criteria(yes = "Advertising")),
                "intent" to DecisionQuestion.Choice(mapOf("reminder" to "Wants a reminder", "timer" to null), "What does the user want?"),
                "urgency" to DecisionQuestion.Score(listOf("low", "medium", "high")),
            ),
        )

        val expected = """{
            "state": "Remind me to call the dentist",
            "questions": {
                "is_reminder": {"type": "noul", "instructions": "Is the user asking to be reminded?"},
                "spam": {"type": "noul", "criteria": {"true": "Advertising"}},
                "intent": {"type": "choice", "criteria": {"reminder": "Wants a reminder", "timer": null}, "instructions": "What does the user want?"},
                "urgency": {"type": "score", "criteria": ["low", "medium", "high"]}
            }
        }"""
        assertEquals(json.parseToJsonElement(expected), json.encodeToJsonElement(DecisionRequestData.serializer(), request))
    }

    @Test
    fun requestIncludesModelOverride() {
        val request = DecisionRequestData(JsonPrimitive("hi"), mapOf("q" to DecisionQuestion.Noul()), model = "jev-test")

        assertEquals(
            json.parseToJsonElement("""{"state": "hi", "questions": {"q": {"type": "noul"}}, "model": "jev-test"}"""),
            json.encodeToJsonElement(DecisionRequestData.serializer(), request),
        )
    }

    @Test
    fun responseDecodesEachAnswerType() {
        val response = json.decodeFromString(
            DecisionResponse.serializer(),
            """{
                "success": true,
                "answers": {
                    "is_reminder": {"type": "noul", "noul": 0.97},
                    "intent": {"type": "choice", "choice": "reminder", "confidence": 0.9, "probabilities": {"reminder": 0.9, "timer": 0.1}},
                    "urgency": {"type": "score", "score": 1.7, "confidence": 0.8, "legend": {"0": "low", "1": "medium", "2": "high"}, "probabilities": {"0": 0.1, "1": 0.1, "2": 0.8}}
                },
                "model": "jev-test",
                "usage": {"input_tokens": 10, "output_tokens": 3}
            }""",
        )

        assertEquals(
            DecisionResponse(
                success = true,
                answers = mapOf(
                    "is_reminder" to DecisionAnswer.Noul(0.97),
                    "intent" to DecisionAnswer.Choice("reminder", 0.9, mapOf("reminder" to 0.9, "timer" to 0.1)),
                    "urgency" to DecisionAnswer.Score(1.7, 0.8, mapOf(0 to "low", 1 to "medium", 2 to "high"), mapOf(0 to 0.1, 1 to 0.1, 2 to 0.8)),
                ),
                model = "jev-test",
                usage = DecisionUsage(inputTokens = 10, outputTokens = 3),
            ),
            response,
        )
    }

    @Test
    fun errorResponseDecodes() {
        val response = json.decodeFromString(DecisionResponse.serializer(), """{"success": false, "message": "Decision request failed."}""")

        assertEquals(DecisionResponse(success = false, message = "Decision request failed."), response)
    }
}
