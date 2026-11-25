package coredevices.indexai.agent.systemone

/**
 * A question for a [SystemOneAgent], answered with an [A].
 * [name] keys the answer; [instructions] is the question put to the model.
 */
sealed interface SystemOneQuestion<out A : SystemOneAnswer> {
    val name: String
    val instructions: String?

    /** [yes] and [no] optionally describe what counts as each outcome. */
    data class YesNo(
        override val name: String,
        override val instructions: String? = null,
        val yes: String? = null,
        val no: String? = null,
    ) : SystemOneQuestion<SystemOneAnswer.YesNo>

    /** Picks one of the [options] keys; each value optionally describes when that option applies. */
    data class Choice(
        override val name: String,
        val options: Map<String, String?>,
        override val instructions: String? = null,
    ) : SystemOneQuestion<SystemOneAnswer.Choice>

    /** Rates against [levels], described lowest first; a level's index is its score. */
    data class Score(
        override val name: String,
        val levels: List<String>,
        override val instructions: String? = null,
    ) : SystemOneQuestion<SystemOneAnswer.Score>
}

sealed interface SystemOneAnswer {
    /** [probability] of a yes answer, 0 to 1. */
    data class YesNo(val probability: Double) : SystemOneAnswer {
        val isYes: Boolean get() = probability >= 0.5
    }

    data class Choice(
        val choice: String,
        val confidence: Double,
        val probabilities: Map<String, Double>,
    ) : SystemOneAnswer

    /** [score] is the probability-weighted average level, so it can fall between levels. */
    data class Score(
        val score: Double,
        val confidence: Double,
        val probabilities: Map<Int, Double>,
    ) : SystemOneAnswer
}

/** The answers to one [SystemOneAgent.decide] call, read by question or by question name. */
class SystemOneAnswers private constructor(private val byName: Map<String, SystemOneAnswer>) {
    @Suppress("UNCHECKED_CAST")
    operator fun <A : SystemOneAnswer> get(question: SystemOneQuestion<A>): A =
        named<SystemOneAnswer>(question.name) as A

    fun yesNo(name: String): SystemOneAnswer.YesNo = named(name)
    fun choice(name: String): SystemOneAnswer.Choice = named(name)
    fun score(name: String): SystemOneAnswer.Score = named(name)

    private inline fun <reified A : SystemOneAnswer> named(name: String): A {
        val answer = byName[name] ?: throw NoSuchElementException("No question named '$name' was asked")
        return answer as? A
            ?: throw IllegalArgumentException("'$name' is not a ${A::class.simpleName} question")
    }

    override fun equals(other: Any?): Boolean = other is SystemOneAnswers && other.byName == byName
    override fun hashCode(): Int = byName.hashCode()
    override fun toString(): String = "SystemOneAnswers($byName)"

    companion object {
        /** @throws SystemOneException if any of [questions] has no answer of its type in [answers]. */
        fun of(
            questions: List<SystemOneQuestion<*>>,
            answers: Map<String, SystemOneAnswer>,
        ): SystemOneAnswers = SystemOneAnswers(
            questions.associate { question ->
                val answer = answers[question.name]
                if (answer == null || !question.accepts(answer)) {
                    throw SystemOneException("No usable answer for '${question.name}': $answer")
                }
                question.name to answer
            }
        )

        private fun SystemOneQuestion<*>.accepts(answer: SystemOneAnswer): Boolean = when (this) {
            is SystemOneQuestion.YesNo -> answer is SystemOneAnswer.YesNo
            is SystemOneQuestion.Choice -> answer is SystemOneAnswer.Choice
            is SystemOneQuestion.Score -> answer is SystemOneAnswer.Score
        }
    }
}
