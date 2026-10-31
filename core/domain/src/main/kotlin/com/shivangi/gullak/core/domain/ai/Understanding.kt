package com.shivangi.gullak.core.domain.ai

import com.shivangi.gullak.core.model.ai.GoalLanguage
import com.shivangi.gullak.core.model.ai.ParsedGoal
import com.shivangi.gullak.core.model.money.Money
import java.time.LocalDate

/** The validated result of reading the conversation so far. */
sealed interface Understanding {
    /** What was understood, with any value that failed validation removed. */
    val parsed: ParsedGoal

    data class NeedsFollowUp(
        override val parsed: ParsedGoal,
        val question: FollowUpQuestion,
        val festival: FestivalMatch? = null,
    ) : Understanding

    /** Everything needed for the confirm card. If [festival] is set, the user confirms the looked-up date. */
    data class ReadyToConfirm(
        override val parsed: ParsedGoal,
        val goalName: String,
        val target: Money,
        val targetDate: LocalDate,
        val language: GoalLanguage,
        val festival: FestivalMatch? = null,
    ) : Understanding
}

sealed interface FollowUpQuestion {
    /** The model's own question, in the user's language. */
    data class FromModel(val text: String) : FollowUpQuestion

    /** A question from string resources, used when validation rejects a value or the model gave none. */
    data class Standard(val reason: FollowUpReason) : FollowUpQuestion
}

enum class FollowUpReason {
    /** The user said Gullak's read-back was wrong. */
    WHAT_TO_CHANGE,
    MISSING_NAME,
    MISSING_AMOUNT,
    MISSING_DATE,
    AMOUNT_OUT_OF_RANGE,
    DATE_NOT_IN_FUTURE,
    DATE_TOO_FAR,
}

data class FestivalMatch(
    val name: String,
    val date: LocalDate,
)
