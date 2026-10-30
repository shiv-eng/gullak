package com.gullak.core.ai.conversation

import com.gullak.core.ai.gemini.GeminiClient
import com.gullak.core.ai.gemini.GeminiConfig
import com.gullak.core.ai.text.IndianNumbers
import com.gullak.core.ai.understanding.GoalUnderstandingPrompt
import com.gullak.core.domain.ai.BrainAnswer
import com.gullak.core.domain.ai.ConversationBrain
import com.gullak.core.domain.ai.TalkIntent
import com.gullak.core.model.ai.ConversationTurn
import com.gullak.core.model.ai.GoalLanguage
import com.gullak.core.model.ai.ParsedGoal
import com.gullak.core.model.goal.Frequency
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

internal class GeminiConversationBrain @Inject constructor(
    private val client: GeminiClient,
    private val config: GeminiConfig,
    private val json: Json,
) : ConversationBrain {

    override suspend fun hear(
        conversation: List<ConversationTurn>,
        situation: String,
        today: LocalDate,
    ): AppResult<BrainAnswer> {
        val talk = ask(conversation, situation, today, config.talkModelId)
        // The better model is often rate limited on a free key: the lighter one keeps the conversation going.
        val answer = if (talk is AppResult.Failure && talk.error is AppError.Http) {
            ask(conversation, situation, today, config.modelId)
        } else {
            talk
        }
        return answer.flatMap { parseBrainAnswer(json, it) }
    }

    private suspend fun ask(
        conversation: List<ConversationTurn>,
        situation: String,
        today: LocalDate,
        model: String,
    ): AppResult<String> = client.generate(
        systemInstruction = ConversationPrompt.systemInstruction(today, situation),
        contents = GoalUnderstandingPrompt.contents(conversation),
        temperature = TEMPERATURE,
        responseSchema = ConversationPrompt.responseSchema,
        model = model,
    )

    private companion object {
        const val TEMPERATURE = 0.4
    }
}

@Serializable
internal data class BrainJson(
    val intent: String,
    val say: String? = null,
    @SerialName("goal_name") val goalName: String? = null,
    @SerialName("target_amount") val targetAmount: Long? = null,
    @SerialName("target_date") val targetDate: String? = null,
    @SerialName("date_hint") val dateHint: String? = null,
    val language: String,
    @SerialName("follow_up") val followUp: String? = null,
    val frequency: String? = null,
)

/** Anything the model says with a number in it is dropped: numbers are for the plan engine alone. */
internal fun parseBrainAnswer(
    json: Json,
    answer: String,
): AppResult<BrainAnswer> {
    val reply = try {
        json.decodeFromString<BrainJson>(answer)
    } catch (e: SerializationException) {
        return invalid("not the expected JSON: ${e.message}")
    } catch (e: IllegalArgumentException) {
        return invalid("not the expected JSON: ${e.message}")
    }
    val intent = TalkIntent.entries.firstOrNull { it.name == reply.intent } ?: return invalid("intent ${reply.intent}")
    val language = GoalLanguage.fromTag(reply.language) ?: return invalid("language ${reply.language}")
    val amount = reply.targetAmount
    if (amount != null && amount < 0) return invalid("negative amount")
    val date = reply.targetDate?.let {
        try {
            LocalDate.parse(it)
        } catch (e: DateTimeParseException) {
            return invalid("bad date $it: ${e.message}")
        }
    }
    return AppResult.Success(
        BrainAnswer(
            intent = intent,
            say = reply.say.withoutNumbers().inLanguageOf(language).withoutPromises(),
            goal = ParsedGoal(
                goalName = reply.goalName?.trim()?.takeIf { it.isNotEmpty() },
                targetAmount = amount?.let(Money::fromRupees),
                targetDate = date,
                dateHint = reply.dateHint?.trim()?.takeIf { it.isNotEmpty() },
                language = language,
                followUp = reply.followUp.withoutNumbers().inLanguageOf(language),
            ),
            frequency = Frequency.entries.firstOrNull { it.name == reply.frequency },
        ),
    )
}

private fun String?.withoutNumbers(): String? = this?.trim()?.takeIf { text ->
    text.isNotEmpty() && text.none(Char::isDigit) && IndianNumbers.find(text).isEmpty()
}

/** The voice reads Hindi only in Devanagari, and English only in Latin letters, so a mismatch is not spoken. */
private fun String?.inLanguageOf(language: GoalLanguage): String? = this?.takeIf { text ->
    text.any { it in DEVANAGARI } == (language != GoalLanguage.ENGLISH)
}

/** Nobody is told their money is safe or sure, whatever the model thinks. */
private fun String?.withoutPromises(): String? = this?.takeIf { text ->
    val lower = text.lowercase()
    PROMISES.none { it in lower }
}

private val DEVANAGARI = 'ऀ'..'ॿ'

private val PROMISES = listOf(
    "safe",
    "secure",
    "guarantee",
    "risk-free",
    "no risk",
    "सुरक्षित",
    "गारंटी",
    "पक्का",
    "बेहतर",
    "better",
    "recommend",
    "should invest",
)

private fun invalid(reason: String) = AppResult.Failure(AppError.InvalidResponse(reason))
