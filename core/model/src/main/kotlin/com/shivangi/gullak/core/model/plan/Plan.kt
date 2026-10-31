package com.shivangi.gullak.core.model.plan

import com.shivangi.gullak.core.model.goal.Destination
import com.shivangi.gullak.core.model.goal.Frequency
import com.shivangi.gullak.core.model.goal.ScheduledDebit
import com.shivangi.gullak.core.model.money.Money
import java.time.LocalDate

/** Why the plan engine chose a destination. Shown to the user from string resources. */
enum class DestinationReason {
    /** Under 12 months: gold carries 3% GST and its price can fall. */
    SHORT_GOAL,

    /** 12 months or more: gold's price can still move. */
    LONG_GOAL,
}

data class PlanOption(
    val frequency: Frequency,
    val amountPerDebit: Money,
    val numberOfDebits: Int,
    val firstDebit: LocalDate,
    val lastDebit: LocalDate,
) {
    /** What the option saves in total; can exceed the remaining amount because amounts round up. */
    val total: Money get() = amountPerDebit * numberOfDebits
}

data class Plan(
    val remaining: Money,
    val options: List<PlanOption>,
    val oneTimeOnly: Boolean,
    val destination: Destination,
    val destinationReason: DestinationReason,
    val monthsToGoal: Int,
) {
    /** Lump sum and savings already cover the target; no recurring plan is needed. */
    val isFullyFunded: Boolean get() = !remaining.isPositive

    fun option(frequency: Frequency): PlanOption? = options.firstOrNull { it.frequency == frequency }
}

sealed interface GoalStatus {
    data object OnTrack : GoalStatus

    data class Behind(val by: Money) : GoalStatus

    data object Reached : GoalStatus
}

sealed interface ReplanChoice {
    /** Two one-off debits later in the failed debit's month (monthly schedules only). */
    data class CatchUp(val debits: List<ScheduledDebit>) : ReplanChoice

    /** Raise every remaining scheduled debit to [newAmountPerDebit]. */
    data class Spread(
        val newAmountPerDebit: Money,
        val remainingDebits: Int,
    ) : ReplanChoice

    /** Move the target date one period later; the failed date moves to the end of the schedule. */
    data class Extend(val newTargetDate: LocalDate) : ReplanChoice
}
