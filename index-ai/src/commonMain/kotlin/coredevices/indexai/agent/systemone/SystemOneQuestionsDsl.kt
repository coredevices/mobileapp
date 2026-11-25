package coredevices.indexai.agent.systemone

@DslMarker
annotation class SystemOneDsl

/**
 * ```
 * val questions = systemOneQuestions {
 *     yesNo("is_reminder", "Is the user asking to be reminded?")
 *     yesNo("spam") { yes = "Advertising or a scam" }
 *     choice("intent", "What does the user want?") {
 *         "reminder" means "Wants to be reminded of something"
 *         option("timer")
 *     }
 *     score("urgency") {
 *         level("Can wait")
 *         level("Today")
 *         level("Right now")
 *     }
 * }
 * ```
 * Each builder returns its question, which can be kept to read its answer with [SystemOneAnswers.get].
 */
fun systemOneQuestions(block: SystemOneQuestionsBuilder.() -> Unit): List<SystemOneQuestion<*>> =
    SystemOneQuestionsBuilder().apply(block).build()

@SystemOneDsl
class SystemOneQuestionsBuilder internal constructor() {
    private val questions = mutableListOf<SystemOneQuestion<*>>()

    fun yesNo(
        name: String,
        instructions: String? = null,
        block: YesNoBuilder.() -> Unit = {},
    ): SystemOneQuestion.YesNo {
        val criteria = YesNoBuilder().apply(block)
        return add(SystemOneQuestion.YesNo(name, instructions, criteria.yes, criteria.no))
    }

    fun choice(
        name: String,
        instructions: String? = null,
        block: ChoiceBuilder.() -> Unit,
    ): SystemOneQuestion.Choice =
        add(SystemOneQuestion.Choice(name, ChoiceBuilder().apply(block).options.toMap(), instructions))

    fun score(
        name: String,
        instructions: String? = null,
        block: ScoreBuilder.() -> Unit,
    ): SystemOneQuestion.Score =
        add(SystemOneQuestion.Score(name, ScoreBuilder().apply(block).levels.toList(), instructions))

    private fun <Q : SystemOneQuestion<*>> add(question: Q): Q {
        require(questions.none { it.name == question.name }) { "Duplicate question name '${question.name}'" }
        questions += question
        return question
    }

    internal fun build(): List<SystemOneQuestion<*>> = questions.toList()
}

@SystemOneDsl
class YesNoBuilder internal constructor() {
    /** What counts as a yes. */
    var yes: String? = null
    /** What counts as a no. */
    var no: String? = null
}

@SystemOneDsl
class ChoiceBuilder internal constructor() {
    internal val options = linkedMapOf<String, String?>()

    fun option(name: String, description: String? = null) {
        require(name !in options) { "Duplicate option '$name'" }
        options[name] = description
    }

    infix fun String.means(description: String) = option(this, description)
}

@SystemOneDsl
class ScoreBuilder internal constructor() {
    internal val levels = mutableListOf<String>()

    /** Adds the next level up; the first level scores 0. */
    fun level(description: String) {
        levels += description
    }
}
