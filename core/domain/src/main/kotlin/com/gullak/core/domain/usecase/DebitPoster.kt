package com.gullak.core.domain.usecase

import com.gullak.core.domain.analytics.Analytics
import com.gullak.core.domain.analytics.AnalyticsEvent
import com.gullak.core.domain.gateway.AutoPayGateway
import com.gullak.core.domain.gateway.IdGenerator
import com.gullak.core.domain.plan.PlanEngine
import com.gullak.core.domain.repository.DebitRepository
import com.gullak.core.domain.repository.GoalRepository
import com.gullak.core.model.goal.Debit
import com.gullak.core.model.goal.DebitStatus
import com.gullak.core.model.goal.Goal
import com.gullak.core.model.goal.ScheduledDebit
import com.gullak.core.model.result.AppError
import com.gullak.core.model.result.AppResult
import com.gullak.core.model.result.getOrElse
import java.time.LocalDate
import javax.inject.Inject

/** Runs debits through the (mocked) AutoPay mandate and records the outcome. Shared by the demo use cases. */
class DebitPoster @Inject constructor(
    private val engine: PlanEngine,
    private val goals: GoalRepository,
    private val debits: DebitRepository,
    private val autoPay: AutoPayGateway,
    private val ids: IdGenerator,
    private val analytics: Analytics,
) {
    suspend fun post(
        goal: Goal,
        scheduled: ScheduledDebit,
        succeed: Boolean,
    ): AppResult<Debit> {
        val mandateId = goal.schedule?.mandateId ?: return AppResult.Failure(AppError.NothingDue)
        val savedBefore = engine.saved(debits.getDebits(goal.id))
        val result = autoPay
            .debit(mandateId, scheduled.amount, scheduled.date, forceFail = !succeed)
            .getOrElse { return it }
        val debit = Debit(ids.newId(), goal.id, scheduled.date, scheduled.amount, result.status, scheduled.kind)
        debits.insert(debit)

        if (debit.status == DebitStatus.SUCCEEDED) {
            analytics.log(AnalyticsEvent.DebitSucceeded(debit.kind))
            if (savedBefore < goal.target && savedBefore + debit.amount >= goal.target) {
                analytics.log(AnalyticsEvent.GoalReached)
            }
        } else {
            analytics.log(AnalyticsEvent.DebitFailed(debit.kind))
        }
        return AppResult.Success(debit)
    }

    /**
     * Posts, as SUCCEEDED, every unposted debit dated on or before [upTo] for all goals except
     * [exceptGoalId]. Moving the demo clock forward runs everyone's mandates; only the goal being
     * simulated can fail.
     */
    suspend fun postAllDue(
        upTo: LocalDate,
        exceptGoalId: String? = null,
    ): AppResult<Unit> {
        for (goal in goals.getGoals()) {
            if (goal.id == exceptGoalId) continue
            val due = engine.upcomingDebits(goal, debits.getDebits(goal.id)).takeWhile { it.date <= upTo }
            for (scheduled in due) {
                post(goal, scheduled, succeed = true).getOrElse { return it }
            }
        }
        return AppResult.Success(Unit)
    }
}
