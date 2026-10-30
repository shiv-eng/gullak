package com.gullak.core.domain.plan

import com.gullak.core.model.goal.Debit
import com.gullak.core.model.goal.DebitKind
import com.gullak.core.model.goal.DebitStatus
import com.gullak.core.model.goal.Destination
import com.gullak.core.model.goal.Frequency
import com.gullak.core.model.goal.Goal
import com.gullak.core.model.goal.ScheduledDebit
import com.gullak.core.model.money.Money
import com.gullak.core.model.money.sum
import com.gullak.core.model.plan.DestinationReason
import com.gullak.core.model.plan.GoalStatus
import com.gullak.core.model.plan.Plan
import com.gullak.core.model.plan.PlanOption
import com.gullak.core.model.plan.ReplanChoice
import com.gullak.core.model.result.AppError
import com.gullak.core.model.result.AppResult
import com.gullak.core.model.result.PlanInputError
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Every amount, date and product choice Gullak shows comes from here. Pure functions over the injected
 * [clock]; no Android, no I/O, no AI. The rules are section 5 of the brief.
 */
class PlanEngine @Inject constructor(private val clock: Clock) {

    fun today(): LocalDate = LocalDate.now(clock)

    /** The next 5th on or after tomorrow; tomorrow if that 5th falls after [targetDate]. */
    fun defaultStartDate(
        targetDate: LocalDate,
        today: LocalDate = today(),
    ): LocalDate {
        val tomorrow = today.plusDays(1)
        val nextFifth = ScheduleCalendar.nextMonthlyDebitDay(tomorrow)
        return if (nextFifth <= targetDate) nextFifth else tomorrow
    }

    fun plan(input: PlanInput): AppResult<Plan> {
        val today = today()
        val error = when {
            !input.target.isPositive -> PlanInputError.TARGET_NOT_POSITIVE
            input.targetDate <= today -> PlanInputError.TARGET_DATE_NOT_IN_FUTURE
            input.startDate != null && input.startDate > input.targetDate -> PlanInputError.START_AFTER_TARGET
            else -> null
        }
        if (error != null) return AppResult.Failure(AppError.InvalidPlanInput(error))

        val start = input.startDate ?: defaultStartDate(input.targetDate, today)
        val remaining = maxOf(input.target - input.lumpSum - input.alreadySaved, Money.ZERO)
        val monthsToGoal = ChronoUnit.MONTHS.between(today, input.targetDate).toInt()
        val oneTimeOnly = ChronoUnit.DAYS.between(today, input.targetDate) < MIN_DAYS_FOR_RECURRING
        val options = if (oneTimeOnly || !remaining.isPositive) {
            emptyList()
        } else {
            listOfNotNull(
                dailyOption(remaining, start, input.targetDate),
                weeklyOption(remaining, start, input.targetDate),
                monthlyOption(remaining, start, input.targetDate),
            )
        }
        val (destination, reason) = destinationFor(monthsToGoal)
        return AppResult.Success(
            Plan(
                remaining = remaining,
                options = options,
                oneTimeOnly = oneTimeOnly,
                destination = destination,
                destinationReason = reason,
                monthsToGoal = monthsToGoal,
            ),
        )
    }

    /** Sum of every successful debit: scheduled, lump sum and catch-up. */
    fun saved(debits: List<Debit>): Money = debits.filter { it.status == DebitStatus.SUCCEEDED }.map { it.amount }.sum()

    /**
     * Expected = lump sum + base amount × scheduled dates on or before [today] that are not excused, capped at
     * the target. Catch-up debits never raise "expected"; they close the gap.
     */
    fun status(
        goal: Goal,
        debits: List<Debit>,
        today: LocalDate = today(),
    ): GoalStatus {
        val saved = saved(debits)
        if (saved >= goal.target) return GoalStatus.Reached

        val lumpSum = debits
            .filter { it.kind == DebitKind.LUMP_SUM && it.status == DebitStatus.SUCCEEDED }
            .map { it.amount }
            .sum()
        val schedule = goal.schedule
        val dueDebits = schedule
            ?.let { s -> ScheduleCalendar.scheduledDates(s).count { it <= today && it !in s.excusedDates } }
            ?: 0
        val base = schedule?.baseAmountPerDebit ?: Money.ZERO
        val expected = minOf(lumpSum + base * dueDebits, goal.target)
        return if (saved >= expected) GoalStatus.OnTrack else GoalStatus.Behind(expected - saved)
    }

    /** Debits not yet attempted, earliest first: scheduled dates (at the current amount) and catch-ups. */
    fun upcomingDebits(
        goal: Goal,
        debits: List<Debit>,
    ): List<ScheduledDebit> {
        val schedule = goal.schedule ?: return emptyList()
        val postedScheduled = debits.filter { it.kind == DebitKind.SCHEDULED }.map { it.date }.toSet()
        val postedCatchUps = debits.filter { it.kind == DebitKind.CATCH_UP }.map { it.date }.toSet()
        val scheduled = ScheduleCalendar.scheduledDates(schedule)
            .filter { it !in postedScheduled && it !in schedule.excusedDates }
            .map { ScheduledDebit(it, schedule.amountPerDebit, DebitKind.SCHEDULED) }
        val catchUps = schedule.extraDebits.filter { it.date !in postedCatchUps }
        return (scheduled + catchUps).sortedWith(compareBy<ScheduledDebit> { it.date }.thenBy { it.kind })
    }

    /** Recovery choices after [failedDebit]; see section 5, rule 8. */
    fun replanOptions(
        goal: Goal,
        failedDebit: Debit,
        today: LocalDate = today(),
    ): List<ReplanChoice> {
        val schedule = goal.schedule ?: return emptyList()
        val missed = failedDebit.amount
        val remainingDebits = ScheduleCalendar.scheduledDates(schedule)
            .count { it > failedDebit.date && it !in schedule.excusedDates }
        return buildList {
            if (schedule.frequency == Frequency.MONTHLY) {
                add(ReplanChoice.CatchUp(catchUpDebits(missed, failedDebit.date, today)))
            }
            if (remainingDebits > 0) {
                val extra = missed.splitRoundedUp(remainingDebits, MONTHLY_STEP_RUPEES)
                add(ReplanChoice.Spread(schedule.amountPerDebit + extra, remainingDebits))
            }
            if (!goal.dateIsFixed) {
                add(ReplanChoice.Extend(ScheduleCalendar.plusOnePeriod(goal.targetDate, schedule.frequency)))
            }
        }
    }

    /** The goal after the user applies [choice] for [failedDebit]. */
    fun applyReplan(
        goal: Goal,
        choice: ReplanChoice,
        failedDebit: Debit,
    ): Goal {
        val schedule = goal.schedule ?: return goal
        return when (choice) {
            is ReplanChoice.CatchUp -> goal.copy(
                schedule = schedule.copy(extraDebits = schedule.extraDebits + choice.debits),
            )

            is ReplanChoice.Spread -> goal.copy(
                schedule = schedule.copy(amountPerDebit = choice.newAmountPerDebit),
            )

            is ReplanChoice.Extend -> goal.copy(
                targetDate = choice.newTargetDate,
                schedule = schedule.copy(
                    endDate = choice.newTargetDate,
                    excusedDates = schedule.excusedDates + failedDebit.date,
                ),
            )
        }
    }

    private fun dailyOption(
        remaining: Money,
        start: LocalDate,
        target: LocalDate,
    ): PlanOption {
        val days = (ChronoUnit.DAYS.between(start, target) + 1).toInt()
        val amount = maxOf(remaining.splitRoundedUp(days, DAILY_STEP_RUPEES), MIN_DAILY_AMOUNT)
        return PlanOption(Frequency.DAILY, amount, days, start, target)
    }

    private fun weeklyOption(
        remaining: Money,
        start: LocalDate,
        target: LocalDate,
    ): PlanOption {
        val dates = ScheduleCalendar.dates(start, target, Frequency.WEEKLY)
        val amount = remaining.splitRoundedUp(dates.size, WEEKLY_STEP_RUPEES)
        return PlanOption(Frequency.WEEKLY, amount, dates.size, dates.first(), dates.last())
    }

    /** Debits on the 5th from [start]'s month; null when no 5th falls between [start] and [target]. */
    private fun monthlyOption(
        remaining: Money,
        start: LocalDate,
        target: LocalDate,
    ): PlanOption? {
        val dates = ScheduleCalendar.dates(ScheduleCalendar.nextMonthlyDebitDay(start), target, Frequency.MONTHLY)
        if (dates.isEmpty()) return null
        val amount = remaining.splitRoundedUp(dates.size, MONTHLY_STEP_RUPEES)
        return PlanOption(Frequency.MONTHLY, amount, dates.size, dates.first(), dates.last())
    }

    /**
     * Two debits of half the missed amount (rounded up to ₹50) on the 15th and 25th of the failed debit's
     * month. A date already past is replaced by the next free day after today.
     */
    private fun catchUpDebits(
        missed: Money,
        failedDate: LocalDate,
        today: LocalDate,
    ): List<ScheduledDebit> {
        val month = YearMonth.from(failedDate)
        val preferred = CATCH_UP_DAYS.map { month.atDay(it) }.filter { it > today }
        val fallback = generateSequence(today.plusDays(1)) { it.plusDays(1) }
            .filter { it !in preferred }
            .take(CATCH_UP_DAYS.size - preferred.size)
        val half = missed.splitRoundedUp(CATCH_UP_DAYS.size, MONTHLY_STEP_RUPEES)
        return (preferred + fallback).sorted().map { ScheduledDebit(it, half, DebitKind.CATCH_UP) }
    }

    private fun destinationFor(monthsToGoal: Int): Pair<Destination, DestinationReason> =
        if (monthsToGoal < GOLD_MIN_MONTHS) {
            Destination.PAYTM_MONEY_FUND to DestinationReason.SHORT_GOAL
        } else {
            Destination.PAYTM_GOLD to DestinationReason.LONG_GOAL
        }

    companion object {
        /** A target date closer than this gets no recurring plan. */
        const val MIN_DAYS_FOR_RECURRING = 7L

        private const val DAILY_STEP_RUPEES = 5L
        private const val WEEKLY_STEP_RUPEES = 10L
        private const val MONTHLY_STEP_RUPEES = 50L
        private const val GOLD_MIN_MONTHS = 12
        private val CATCH_UP_DAYS = listOf(15, 25)

        /** Paytm Gold's daily SIP minimum. */
        private val MIN_DAILY_AMOUNT = Money.fromRupees(21)
    }
}
