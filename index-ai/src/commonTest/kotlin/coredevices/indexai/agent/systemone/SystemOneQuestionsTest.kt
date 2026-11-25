package coredevices.indexai.agent.systemone

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemOneQuestionsTest {
    @Test
    fun dslBuildsQuestionsInOrder() {
        val questions = systemOneQuestions {
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

        assertEquals(
            listOf(
                SystemOneQuestion.YesNo("is_reminder", "Is the user asking to be reminded?"),
                SystemOneQuestion.YesNo("spam", yes = "Advertising"),
                SystemOneQuestion.Choice(
                    "intent",
                    mapOf("reminder" to "Wants a reminder", "timer" to null),
                    "What does the user want?",
                ),
                SystemOneQuestion.Score("urgency", listOf("low", "high")),
            ),
            questions,
        )
    }

    @Test
    fun dslRejectsDuplicateNames() {
        assertFailsWith<IllegalArgumentException> {
            systemOneQuestions {
                yesNo("q")
                score("q") { level("low") }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            systemOneQuestions {
                choice("q") {
                    option("a")
                    option("a")
                }
            }
        }
    }

    @Test
    fun answersAreReadByQuestionOrName() {
        lateinit var spam: SystemOneQuestion.YesNo
        lateinit var intent: SystemOneQuestion.Choice
        val questions = systemOneQuestions {
            spam = yesNo("spam")
            intent = choice("intent") { option("reminder") }
            score("urgency") { level("low") }
        }
        val choice = SystemOneAnswer.Choice("reminder", 1.0, mapOf("reminder" to 1.0))
        val score = SystemOneAnswer.Score(0.0, 1.0, mapOf(0 to 1.0))

        val answers = SystemOneAnswers.of(
            questions,
            mapOf("spam" to SystemOneAnswer.YesNo(0.2), "intent" to choice, "urgency" to score),
        )

        assertFalse(answers[spam].isYes)
        assertEquals(choice, answers[intent])
        assertEquals(score, answers.score("urgency"))
        assertFailsWith<IllegalArgumentException> { answers.yesNo("urgency") }
        assertFailsWith<NoSuchElementException> { answers.yesNo("missing") }
    }

    @Test
    fun yesNoIsYesFromHalf() {
        assertTrue(SystemOneAnswer.YesNo(0.5).isYes)
        assertFalse(SystemOneAnswer.YesNo(0.49).isYes)
    }

    @Test
    fun answersRejectMissingOrMistypedAnswer() {
        val questions = systemOneQuestions { yesNo("spam") }

        assertFailsWith<SystemOneException> { SystemOneAnswers.of(questions, emptyMap()) }
        assertFailsWith<SystemOneException> {
            SystemOneAnswers.of(questions, mapOf("spam" to SystemOneAnswer.Score(0.0, 1.0, emptyMap())))
        }
    }
}
