package com.gullak.core.domain.usecase

import com.gullak.core.domain.analytics.Analytics
import com.gullak.core.domain.analytics.AnalyticsEvent
import com.gullak.core.domain.gateway.IdGenerator
import com.gullak.core.domain.gateway.SavingsDestinationGateway
import com.gullak.core.domain.plan.PlanEngine
import com.gullak.core.domain.repository.DebitRepository
import com.gullak.core.domain.repository.GoalRepository
import com.gullak.core.model.goal.Debit
import com.gullak.core.model.goal.DebitKind
import com.gullak.core.model.goal.DebitStatus
import com.gullak.core.model.goal.Goal
import com.gullak.core.model.money.Money
import com.gullak.core.model.money.sum
import com.gullak.core.model.result.AppError
import com.gullak.core.model.result.AppResult
import com.gullak.core.model.result.getOrElse
import javax.inject.Inject

/** Adds a one-time payment to a running Gullak, at any time. It can fill the goal, not go past it. */
class AddMoneyUseCase @Inject constructor(
    private val engine: PlanEngine,
    private val goals: GoalRepository,
    private val debits: DebitRepository,
    private val ids: IdGenerator,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(
        goalId: String,
        amount: Money,
    ): AppResult<Debit> {
        val goal = goals.getGoal(goalId) ?: return AppResult.Failure(AppError.NotFound("goal $goalId"))
        val existing = debits.getDebits(goalId)
        val saved = engine.saved(existing)
        val room = goal.target - saved
        if (engine.isPaidOut(existing) || !amount.isPositive || amount > room) {
            return AppResult.Failure(AppError.NothingDue)
        }
        val debit = Debit(ids.newId(), goalId, engine.today(), amount, DebitStatus.SUCCEEDED, DebitKind.LUMP_SUM)
        debits.insert(debit)
        analytics.log(AnalyticsEvent.DebitSucceeded(DebitKind.LUMP_SUM))
        if (saved + amount >= goal.target) analytics.log(AnalyticsEvent.GoalReached)
        return AppResult.Success(debit)
    }
}

/**
 * Sends the saved money to the user's bank, once the goal is reached or its date has come. It never runs by itself:
 * the user asks for it, on goal day or after.
 */
class PayOutGoalUseCase @Inject constructor(
    private val engine: PlanEngine,
    private val goals: GoalRepository,
    private val debits: DebitRepository,
    private val destinations: SavingsDestinationGateway,
    private val ids: IdGenerator,
    private val analytics: Analytics,
) {
    fun canPayOut(
        goal: Goal,
        goalDebits: List<Debit>,
    ): Boolean {
        val saved = engine.saved(goalDebits)
        val due = saved >= goal.target || engine.today() >= goal.targetDate
        return saved.isPositive && due && !engine.isPaidOut(goalDebits)
    }

    suspend operator fun invoke(goalId: String): AppResult<Debit> {
        val goal = goals.getGoal(goalId) ?: return AppResult.Failure(AppError.NotFound("goal $goalId"))
        val existing = debits.getDebits(goalId)
        if (!canPayOut(goal, existing)) return AppResult.Failure(AppError.NothingDue)
        val amount = engine.saved(existing)
        destinations.payOut(goal.destination, goalId, amount).getOrElse { return it }
        val debit = Debit(ids.newId(), goalId, engine.today(), amount, DebitStatus.SUCCEEDED, DebitKind.PAYOUT)
        debits.insert(debit)
        analytics.log(AnalyticsEvent.GoalPaidOut)
        return AppResult.Success(debit)
    }
}

/** What Gullak can say when someone asks about a top-up loan. */
sealed interface TopUpAnswer {
    /** Their remaining payments already reach the goal. */
    data object NoGap : TopUpAnswer

    /** Too few payments yet to count as a proven saver. */
    data class NotYetProven(val shortfall: Money) : TopUpAnswer

    data class Offer(val shortfall: Money) : TopUpAnswer
}

/**
 * Save first, borrow small. Only ever runs when the user asks, and only for the gap their remaining payments will not
 * cover. The prototype sends no request anywhere and quotes no terms: a real one would go to a lending partner.
 */
class CheckTopUpUseCase @Inject constructor(
    private val engine: PlanEngine,
    private val goals: GoalRepository,
    private val debits: DebitRepository,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(goalId: String): AppResult<TopUpAnswer> {
        val goal = goals.getGoal(goalId) ?: return AppResult.Failure(AppError.NotFound("goal $goalId"))
        val existing = debits.getDebits(goalId)
        analytics.log(AnalyticsEvent.TopUpAsked)
        if (engine.isPaidOut(existing)) return AppResult.Success(TopUpAnswer.NoGap)
        val coming = engine.upcomingDebits(goal, existing).map { it.amount }.sum()
        val covered = engine.saved(existing) + coming
        if (covered >= goal.target) return AppResult.Success(TopUpAnswer.NoGap)
        val shortfall = goal.target - covered
        val paid = existing.count { it.status == DebitStatus.SUCCEEDED && it.kind != DebitKind.PAYOUT }
        return AppResult.Success(
            if (paid >= PROVEN_SAVER_PAYMENTS) TopUpAnswer.Offer(shortfall) else TopUpAnswer.NotYetProven(shortfall),
        )
    }

    private companion object {
        const val PROVEN_SAVER_PAYMENTS = 3
    }
}

/** The three things a user can do with the money in a running Gullak. */
class GoalMoneyUseCases @Inject constructor(
    val addMoney: AddMoneyUseCase,
    val payOut: PayOutGoalUseCase,
    val checkTopUp: CheckTopUpUseCase,
)
