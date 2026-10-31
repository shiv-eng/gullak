package com.shivangi.gullak.core.model.ai

import com.shivangi.gullak.core.model.money.Money
import java.time.LocalDate

enum class GoalLanguage(val tag: String) {
    HINDI("hi"),
    HINGLISH("hinglish"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromTag(tag: String?): GoalLanguage? = entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
    }
}

/** What the understanding step read from the conversation. Unvalidated until `UnderstandGoalUseCase`. */
data class ParsedGoal(
    val goalName: String?,
    val targetAmount: Money?,
    val targetDate: LocalDate?,
    val dateHint: String?,
    val language: GoalLanguage,
    val followUp: String?,
)

enum class Speaker { USER, GULLAK }

data class ConversationTurn(
    val speaker: Speaker,
    val text: String,
)

/** Which path produced the last understanding, shown in Settings for the demo. */
enum class AiPath { DEMO, GEMINI, RULES }
