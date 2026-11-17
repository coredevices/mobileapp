package coredevices.ring.agent

import coredevices.indexai.agent.systemone.SystemOneAgent
import coredevices.indexai.agent.systemone.SystemOneAnswer
import coredevices.indexai.agent.systemone.SystemOneAnswers
import coredevices.indexai.agent.systemone.SystemOneException
import coredevices.indexai.agent.systemone.SystemOneQuestion
import coredevices.ring.api.DecisionAnswer
import coredevices.ring.api.DecisionQuestion
import coredevices.ring.api.NenyaClient
import kotlinx.serialization.json.JsonElement

/**
 * [SystemOneAgent] backed by nenya's decision endpoint.
 * @param model Jev model override, or null for the server default.
 */
class NenyaSystemOneAgent(
    private val nenyaClient: NenyaClient,
    private val model: String? = null,
) : SystemOneAgent {
    override suspend fun decide(state: JsonElement, questions: List<SystemOneQuestion<*>>): SystemOneAnswers {
        val result = nenyaClient.decide(state, questions.associate { it.name to it.toWire() }, model)
        val response = result.response
            ?: throw SystemOneException("Decision request failed: ${result.statusCode}")
        if (!response.success) {
            throw SystemOneException("Decision request failed: ${response.message}")
        }
        return SystemOneAnswers.of(questions, response.answers.mapValues { it.value.toAnswer() })
    }
}

private fun SystemOneQuestion<*>.toWire(): DecisionQuestion = when (this) {
    is SystemOneQuestion.YesNo -> DecisionQuestion.Noul(
        instructions = instructions,
        criteria = if (yes != null || no != null) DecisionQuestion.Noul.Criteria(yes, no) else null,
    )
    is SystemOneQuestion.Choice -> DecisionQuestion.Choice(options, instructions)
    is SystemOneQuestion.Score -> DecisionQuestion.Score(levels, instructions)
}

private fun DecisionAnswer.toAnswer(): SystemOneAnswer = when (this) {
    is DecisionAnswer.Noul -> SystemOneAnswer.YesNo(probability)
    is DecisionAnswer.Choice -> SystemOneAnswer.Choice(choice, confidence, probabilities)
    is DecisionAnswer.Score -> SystemOneAnswer.Score(score, confidence, probabilities)
}
