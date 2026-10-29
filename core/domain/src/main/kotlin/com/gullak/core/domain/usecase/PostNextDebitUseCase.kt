package com.gullak.core.domain.usecase

import com.gullak.core.domain.gateway.DemoClockController
import com.gullak.core.domain.plan.PlanEngine
import com.gullak.core.domain.repository.DebitRepository
import com.gullak.core.domain.repository.GoalRepository
import com.gullak.core.model.goal.Debit
import com.gullak.core.model.result.AppError
import com.gullak.core.model.result.AppResult
import com.gullak.core.model.result.getOrElse
import javax.inject.Inject

/**
 * Demo control: posts the goal's earliest unposted debit (scheduled or catch-up) as SUCCEEDED or FAILED.
 * If that debit is in the future, the demo clock moves to its date and other goals' due debits post first.
 */
class PostNextDebitUseCase @Inject constructor(
    private val engine: PlanEngine,
    private val goals: GoalRepository,
    private val debits: DebitRepository,
    private val clock: DemoClockController,
    private val poster: DebitPoster,
) {
    suspend operator fun invoke(
        goalId: String,
        succeed: Boolean,
    ): AppResult<Debit> {
        val goal = goals.getGoal(goalId) ?: return AppResult.Failure(AppError.NotFound("goal $goalId"))
        val next = engine.upcomingDebits(goal, debits.getDebits(goalId)).firstOrNull()
            ?: return AppResult.Failure(AppError.NothingDue)

        if (next.date > engine.today()) {
            poster.postAllDue(upTo = next.date, exceptGoalId = goalId).getOrElse { return it }
            clock.setDate(next.date)
        }
        return poster.post(goal, next, succeed)
    }
}
