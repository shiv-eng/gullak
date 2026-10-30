package com.gullak.core.domain.ai

import com.gullak.core.model.ai.ConversationTurn
import com.gullak.core.model.ai.ParsedGoal
import com.gullak.core.model.goal.Frequency
import com.gullak.core.model.money.Money
import com.gullak.core.model.result.AppResult
import java.time.LocalDate

/** What the user meant by what they just said. */
enum class TalkIntent {
    GOAL_DETAILS,
    YES,
    NO,
    CHOOSE_PLAN,
    QUESTION,
    WHERE_MONEY_GOES,
    CHECK_SAVINGS,
    ADD_MONEY,
    PAY_OUT,
    ASK_TOP_UP,
    ANOTHER_GOAL,
    FINISHED,
    UNCLEAR,
}

/**
 * The model's reading of one turn. [say] is its own reaction, always free of numbers: every amount and date that is
 * spoken comes from the plan engine. Whether a Gullak actually starts is decided in code from the user's own words.
 */
data class BrainAnswer(
    val intent: TalkIntent,
    val say: String?,
    val goal: ParsedGoal,
    val frequency: Frequency?,
    /** A one-time payment they want to make now. */
    val oneTime: Money? = null,
    /** They said the date cannot move. */
    val dateFixed: Boolean = false,
)

/** Understands each turn of the spoken conversation and reacts to it like a person. */
interface ConversationBrain {
    /** [situation] is plain text saying where the conversation is, and what has been agreed so far. */
    suspend fun hear(
        conversation: List<ConversationTurn>,
        situation: String,
        today: LocalDate,
    ): AppResult<BrainAnswer>
}
