package com.gullak.core.domain.usecase

import com.gullak.core.domain.analytics.Analytics
import com.gullak.core.domain.analytics.AnalyticsEvent
import com.gullak.core.domain.plan.PlanEngine
import com.gullak.core.domain.repository.DebitRepository
import com.gullak.core.domain.repository.GoalRepository
import com.gullak.core.model.goal.Debit
import com.gullak.core.model.goal.DebitStatus
import com.gullak.core.model.goal.Goal
import com.gullak.core.model.plan.GoalStatus
import com.gullak.core.model.plan.ReplanChoice
import com.gullak.core.model.result.AppError
import com.gullak.core.model.result.AppResult
import com.gullak.core.model.result.getOrElse
import javax.inject.Inject

/** The recovery choices after a failed AutoPay debit. */
class GetReplanOptionsUseCase @Inject constructor(
    private val engine: PlanEngine,
    private val goals: GoalRepository,
    private val debits: DebitRepository,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(
        goalId: String,
        failedDebitId: String,
    ): AppResult<ReplanOffer> {
        val (goal, failed) = loadFailedDebit(goals, debits, goalId, failedDebitId).getOrElse { return it }
        val offer = ReplanOffer(
            goal = goal,
            failedDebit = failed,
            status = engine.status(goal, debits.getDebits(goalId)),
            choices = engine.replanOptions(goal, failed),
        )
        analytics.log(AnalyticsEvent.ReplanShown(offer.choices.size))
        return AppResult.Success(offer)
    }
}

/** Applies the chosen recovery and returns the recalculated status. */
class ApplyReplanUseCase @Inject constructor(
    private val engine: PlanEngine,
    private val goals: GoalRepository,
    private val debits: DebitRepository,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(
        goalId: String,
        failedDebitId: String,
        choice: ReplanChoice,
    ): AppResult<GoalStatus> {
        val (goal, failed) = loadFailedDebit(goals, debits, goalId, failedDebitId).getOrElse { return it }
        val updated = engine.applyReplan(goal, choice, failed)
        goals.upsert(updated)
        analytics.log(AnalyticsEvent.ReplanApplied(choice.analyticsName))
        return AppResult.Success(engine.status(updated, debits.getDebits(goalId)))
    }
}

private suspend fun loadFailedDebit(
    goals: GoalRepository,
    debits: DebitRepository,
    goalId: String,
    failedDebitId: String,
): AppResult<Pair<Goal, Debit>> {
    val goal = goals.getGoal(goalId) ?: return AppResult.Failure(AppError.NotFound("goal $goalId"))
    val debit = debits.getDebit(failedDebitId)
        ?.takeIf { it.goalId == goalId && it.status == DebitStatus.FAILED }
        ?: return AppResult.Failure(AppError.NotFound("failed debit $failedDebitId"))
    return AppResult.Success(goal to debit)
}

private val ReplanChoice.analyticsName: String
    get() = when (this) {
        is ReplanChoice.CatchUp -> "catch_up"
        is ReplanChoice.Spread -> "spread"
        is ReplanChoice.Extend -> "extend"
    }
