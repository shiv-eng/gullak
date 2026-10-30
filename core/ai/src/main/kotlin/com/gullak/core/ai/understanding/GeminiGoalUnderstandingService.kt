package com.gullak.core.ai.understanding

import com.gullak.core.ai.gemini.GeminiClient
import com.gullak.core.domain.ai.GoalUnderstandingService
import com.gullak.core.model.ai.ConversationTurn
import com.gullak.core.model.ai.GoalLanguage
import com.gullak.core.model.ai.ParsedGoal
import com.gullak.core.model.money.Money
import com.gullak.core.model.result.AppError
import com.gullak.core.model.result.AppResult
import com.gullak.core.model.result.flatMap
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Reads the goal with Gemini structured output. Any unreadable or out-of-schema answer is a failure. */
internal class GeminiGoalUnderstandingService @Inject constructor(
    private val client: GeminiClient,
    private val json: Json,
) : GoalUnderstandingService {

    val isConfigured: Boolean get() = client.isConfigured

    override suspend fun understand(
        conversation: List<ConversationTurn>,
        today: LocalDate,
    ): AppResult<ParsedGoal> = rawAnswer(conversation, today).flatMap(::parse)

    /** The model's JSON text, for the debug check. */
    suspend fun rawAnswer(
        conversation: List<ConversationTurn>,
        today: LocalDate,
    ): AppResult<String> = client.generate(
        systemInstruction = GoalUnderstandingPrompt.systemInstruction(today),
        contents = GoalUnderstandingPrompt.contents(conversation),
        temperature = UNDERSTANDING_TEMPERATURE,
        responseSchema = GoalUnderstandingPrompt.responseSchema,
    )

    fun parse(answer: String): AppResult<ParsedGoal> {
        val goal = try {
            json.decodeFromString<GoalJson>(answer)
        } catch (e: SerializationException) {
            return invalid("not the expected JSON: ${e.message}")
        } catch (e: IllegalArgumentException) {
            return invalid("not the expected JSON: ${e.message}")
        }
        val language = GoalLanguage.fromTag(goal.language) ?: return invalid("unknown language ${goal.language}")
        val amount = goal.targetAmount
        if (amount != null && amount < 0) return invalid("negative amount")
        val date = goal.targetDate?.let {
            try {
                LocalDate.parse(it)
            } catch (e: DateTimeParseException) {
                return invalid("bad date $it")
            }
        }
        return AppResult.Success(
            ParsedGoal(
                goalName = goal.goalName?.trim()?.takeIf { it.isNotEmpty() },
                targetAmount = amount?.let(Money::fromRupees),
                targetDate = date,
                dateHint = goal.dateHint?.trim()?.takeIf { it.isNotEmpty() },
                language = language,
                followUp = goal.followUp?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }

    private fun invalid(detail: String) = AppResult.Failure(AppError.InvalidResponse(detail))

    @Serializable
    internal data class GoalJson(
        @SerialName("goal_name") val goalName: String? = null,
        @SerialName("target_amount") val targetAmount: Long? = null,
        @SerialName("target_date") val targetDate: String? = null,
        @SerialName("date_hint") val dateHint: String? = null,
        val language: String,
        @SerialName("follow_up") val followUp: String? = null,
    )

    private companion object {
        /** Sent only to models before Gemini 3, which should stay at the default temperature. */
        const val UNDERSTANDING_TEMPERATURE = 0.0
    }
}
