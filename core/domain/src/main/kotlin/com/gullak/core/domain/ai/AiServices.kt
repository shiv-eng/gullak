package com.gullak.core.domain.ai

import com.gullak.core.model.ai.ConversationTurn
import com.gullak.core.model.ai.GoalLanguage
import com.gullak.core.model.ai.ParsedGoal
import com.gullak.core.model.plan.Plan
import com.gullak.core.model.plan.PlanOption
import com.gullak.core.model.result.AppResult
import java.time.LocalDate

/** Reads a goal out of the user's words. It never decides amounts: `UnderstandGoalUseCase` validates. */
interface GoalUnderstandingService {
    suspend fun understand(
        conversation: List<ConversationTurn>,
        today: LocalDate,
    ): AppResult<ParsedGoal>
}

/** Explains a plan the engine already calculated. Never fails: falls back to a template. */
interface PlanExplanationService {
    suspend fun explain(
        goalName: String,
        plan: Plan,
        chosen: PlanOption,
        language: GoalLanguage,
    ): String
}
