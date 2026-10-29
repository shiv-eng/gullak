package com.gullak.core.domain.usecase

import com.gullak.core.domain.analytics.Analytics
import com.gullak.core.domain.analytics.AnalyticsEvent
import com.gullak.core.domain.gateway.AutoPayGateway
import com.gullak.core.domain.gateway.IdGenerator
import com.gullak.core.domain.gateway.SavingsDestinationGateway
import com.gullak.core.domain.plan.PlanEngine
import com.gullak.core.domain.plan.PlanInput
import com.gullak.core.domain.repository.DebitRepository
import com.gullak.core.domain.repository.GoalRepository
import com.gullak.core.model.goal.Debit
import com.gullak.core.model.goal.DebitKind
import com.gullak.core.model.goal.DebitStatus
import com.gullak.core.model.goal.Goal
import com.gullak.core.model.goal.SavingsSchedule
import com.gullak.core.model.plan.PlanOption
import com.gullak.core.model.result.AppError
import com.gullak.core.model.result.AppResult
import com.gullak.core.model.result.PlanInputError
import com.gullak.core.model.result.getOrElse
import javax.inject.Inject

/**
 * Recalculates the plan (never trusting amounts from the UI), opens the destination, creates the AutoPay
 * mandate, then saves the goal and any lump sum. Nothing is saved if a gateway call fails.
 */
class CreateGoalUseCase @Inject constructor(
    private val engine: PlanEngine,
    private val goals: GoalRepository,
    private val debits: DebitRepository,
    private val autoPay: AutoPayGateway,
    private val destinations: SavingsDestinationGateway,
    private val ids: IdGenerator,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(request: NewGoalRequest): AppResult<Goal> {
        val plan = engine
            .plan(PlanInput(target = request.target, targetDate = request.targetDate, lumpSum = request.lumpSum))
            .getOrElse { return it }
        val option: PlanOption? = request.frequency?.let(plan::option)
        val needsSchedule = !plan.oneTimeOnly && !plan.isFullyFunded
        if (needsSchedule && option == null) {
            return AppResult.Failure(AppError.InvalidPlanInput(PlanInputError.FREQUENCY_NOT_AVAILABLE))
        }

        val goalId = ids.newId()
        val account = destinations.open(plan.destination, goalId).getOrElse { return it }
        val schedule = option?.let { chosen ->
            val mandate = autoPay
                .createMandate(chosen.amountPerDebit, chosen.frequency, chosen.firstDebit, account.displayName)
                .getOrElse { return it }
            SavingsSchedule(
                frequency = chosen.frequency,
                baseAmountPerDebit = chosen.amountPerDebit,
                amountPerDebit = chosen.amountPerDebit,
                startDate = chosen.firstDebit,
                endDate = request.targetDate,
                mandateId = mandate.id,
            )
        }

        val today = engine.today()
        val goal = Goal(
            id = goalId,
            name = request.name,
            target = request.target,
            targetDate = request.targetDate,
            dateIsFixed = request.dateIsFixed,
            createdOn = today,
            destination = plan.destination,
            schedule = schedule,
            languageTag = request.language.tag,
        )
        goals.upsert(goal)
        if (request.lumpSum.isPositive) {
            debits.insert(
                Debit(ids.newId(), goalId, today, request.lumpSum, DebitStatus.SUCCEEDED, DebitKind.LUMP_SUM),
            )
        }

        analytics.log(AnalyticsEvent.GoalCreated(plan.destination))
        option?.let { analytics.log(AnalyticsEvent.PlanStarted(it.frequency)) }
        return AppResult.Success(goal)
    }
}
