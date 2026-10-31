package com.shivangi.gullak.core.domain.plan

import com.shivangi.gullak.core.domain.testing.MutableClock
import com.shivangi.gullak.core.domain.testing.TestData
import com.shivangi.gullak.core.domain.testing.TestData.LAPTOP_DATE
import com.shivangi.gullak.core.domain.testing.TestData.TODAY
import com.shivangi.gullak.core.domain.testing.TestData.WEDDING_DATE
import com.shivangi.gullak.core.domain.testing.TestData.rupees
import com.shivangi.gullak.core.model.goal.Destination
import com.shivangi.gullak.core.model.goal.Frequency
import com.shivangi.gullak.core.model.money.Money
import com.shivangi.gullak.core.model.plan.DestinationReason
import com.shivangi.gullak.core.model.plan.Plan
import com.shivangi.gullak.core.model.plan.PlanOption
import com.shivangi.gullak.core.model.result.AppError
import com.shivangi.gullak.core.model.result.AppResult
import com.shivangi.gullak.core.model.result.PlanInputError
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Section 5: the worked examples (today = 31 Oct 2026, start = 5 Nov 2026) and every edge case. */
class PlanEngineTest {

    private val clock = MutableClock(TODAY)
    private val engine = PlanEngine(clock)

    private fun plan(input: PlanInput): Plan = when (val result = engine.plan(input)) {
        is AppResult.Success -> result.value
        is AppResult.Failure -> throw AssertionError("Expected a plan, got ${result.error}")
    }

    private fun planError(input: PlanInput): AppError = when (val result = engine.plan(input)) {
        is AppResult.Success -> throw AssertionError("Expected an error, got ${result.value}")
        is AppResult.Failure -> result.error
    }

    private fun assertOption(
        option: PlanOption?,
        amount: Long,
        debits: Int,
        first: LocalDate,
        last: LocalDate,
    ) {
        requireNotNull(option) { "option missing" }
        assertEquals("amount for ${option.frequency}", rupees(amount), option.amountPerDebit)
        assertEquals("debits for ${option.frequency}", debits, option.numberOfDebits)
        assertEquals("first debit for ${option.frequency}", first, option.firstDebit)
        assertEquals("last debit for ${option.frequency}", last, option.lastDebit)
    }

    @Test
    fun defaultStartIsTheNextFifthOnOrAfterTomorrow() {
        assertEquals(TestData.FIRST_DEBIT, engine.defaultStartDate(LAPTOP_DATE))
        // On the 4th, tomorrow is the 5th itself.
        assertEquals(LocalDate.of(2026, 11, 5), engine.defaultStartDate(LAPTOP_DATE, LocalDate.of(2026, 11, 4)))
        // On the 5th, the next one is a month away.
        assertEquals(LocalDate.of(2026, 12, 5), engine.defaultStartDate(LAPTOP_DATE, LocalDate.of(2026, 11, 5)))
    }

    @Test
    fun laptopWorkedExample() {
        val plan = plan(PlanInput(rupees(36_000), LAPTOP_DATE))

        assertEquals(rupees(36_000), plan.remaining)
        assertFalse(plan.oneTimeOnly)
        assertOption(plan.option(Frequency.DAILY), 155, 238, TestData.FIRST_DEBIT, LAPTOP_DATE)
        assertOption(plan.option(Frequency.WEEKLY), 1_060, 34, TestData.FIRST_DEBIT, LocalDate.of(2027, 6, 24))
        assertOption(plan.option(Frequency.MONTHLY), 4_500, 8, TestData.FIRST_DEBIT, LocalDate.of(2027, 6, 5))
        assertEquals(Destination.PAYTM_MONEY_FUND, plan.destination)
        assertEquals(DestinationReason.SHORT_GOAL, plan.destinationReason)
        assertEquals(7, plan.monthsToGoal)
    }

    @Test
    fun weddingWorkedExample() {
        val plan = plan(PlanInput(rupees(50_000), WEDDING_DATE))

        assertOption(plan.option(Frequency.DAILY), 120, 422, TestData.FIRST_DEBIT, WEDDING_DATE)
        assertOption(plan.option(Frequency.WEEKLY), 820, 61, TestData.FIRST_DEBIT, LocalDate.of(2027, 12, 30))
        assertOption(plan.option(Frequency.MONTHLY), 3_600, 14, TestData.FIRST_DEBIT, LocalDate.of(2027, 12, 5))
        assertEquals(Destination.PAYTM_GOLD, plan.destination)
        assertEquals(DestinationReason.LONG_GOAL, plan.destinationReason)
        assertEquals(14, plan.monthsToGoal)
        // Rounding up shows the overshoot rather than trimming the last debit.
        assertEquals(rupees(50_400), plan.option(Frequency.MONTHLY)?.total)
    }

    @Test
    fun lumpSumRecalculatesEveryOption() {
        val plan = plan(PlanInput(rupees(36_000), LAPTOP_DATE, lumpSum = rupees(6_000)))

        assertEquals(rupees(30_000), plan.remaining)
        assertEquals(rupees(130), plan.option(Frequency.DAILY)?.amountPerDebit)
        assertEquals(rupees(890), plan.option(Frequency.WEEKLY)?.amountPerDebit)
        assertEquals(rupees(3_750), plan.option(Frequency.MONTHLY)?.amountPerDebit)
    }

    @Test
    fun amountAlreadySavedCountsLikeALumpSum() {
        val plan = plan(PlanInput(rupees(36_000), LAPTOP_DATE, alreadySaved = rupees(4_000), lumpSum = rupees(2_000)))

        assertEquals(rupees(30_000), plan.remaining)
        assertEquals(rupees(3_750), plan.option(Frequency.MONTHLY)?.amountPerDebit)
    }

    @Test
    fun remainingAtOrBelowZeroMeansAlreadyFunded() {
        val exact = plan(PlanInput(rupees(36_000), LAPTOP_DATE, lumpSum = rupees(36_000)))
        val over = plan(PlanInput(rupees(36_000), LAPTOP_DATE, lumpSum = rupees(40_000)))

        for (plan in listOf(exact, over)) {
            assertTrue(plan.isFullyFunded)
            assertEquals(Money.ZERO, plan.remaining)
            assertTrue(plan.options.isEmpty())
            assertEquals(Destination.PAYTM_MONEY_FUND, plan.destination)
        }
    }

    @Test
    fun targetInThreeDaysIsOneTimeOnly() {
        val plan = plan(PlanInput(rupees(5_000), TODAY.plusDays(3)))

        assertTrue(plan.oneTimeOnly)
        assertTrue(plan.options.isEmpty())
        assertEquals(rupees(5_000), plan.remaining)
        assertEquals(Destination.PAYTM_MONEY_FUND, plan.destination)
    }

    @Test
    fun targetSevenDaysAwayGetsRecurringOptions() {
        val plan = plan(PlanInput(rupees(5_000), TODAY.plusDays(7)))

        assertFalse(plan.oneTimeOnly)
        assertOption(plan.option(Frequency.DAILY), 1_670, 3, TestData.FIRST_DEBIT, TODAY.plusDays(7))
    }

    @Test
    fun dailyNeverGoesBelowTwentyOneRupees() {
        val plan = plan(PlanInput(rupees(1_000), LAPTOP_DATE))

        // ₹1,000 / 238 = ₹4.20 → ₹5 after rounding → raised to Paytm Gold's ₹21 minimum.
        assertEquals(rupees(21), plan.option(Frequency.DAILY)?.amountPerDebit)
        assertEquals(rupees(30), plan.option(Frequency.WEEKLY)?.amountPerDebit)
        assertEquals(rupees(150), plan.option(Frequency.MONTHLY)?.amountPerDebit)
    }

    @Test
    fun leapYearTargetOnTwentyNinthFebruary() {
        val target = LocalDate.of(2028, 2, 29)
        val plan = plan(PlanInput(rupees(40_000), target))

        assertOption(plan.option(Frequency.DAILY), 85, 482, TestData.FIRST_DEBIT, target)
        assertOption(plan.option(Frequency.WEEKLY), 580, 69, TestData.FIRST_DEBIT, LocalDate.of(2028, 2, 24))
        assertOption(plan.option(Frequency.MONTHLY), 2_500, 16, TestData.FIRST_DEBIT, LocalDate.of(2028, 2, 5))
        assertEquals(15, plan.monthsToGoal)
        assertEquals(Destination.PAYTM_GOLD, plan.destination)
    }

    @Test
    fun monthlyExcludesAFifthAfterTheTargetDate() {
        val plan = plan(PlanInput(rupees(36_000), LocalDate.of(2027, 6, 4)))

        assertOption(plan.option(Frequency.MONTHLY), 5_150, 7, TestData.FIRST_DEBIT, LocalDate.of(2027, 5, 5))
    }

    @Test
    fun explicitStartMidMonthMovesMonthlyToTheNextFifth() {
        val start = LocalDate.of(2026, 11, 20)
        val plan = plan(PlanInput(rupees(36_000), LAPTOP_DATE, startDate = start))

        assertEquals(start, plan.option(Frequency.DAILY)?.firstDebit)
        assertOption(plan.option(Frequency.MONTHLY), 5_150, 7, LocalDate.of(2026, 12, 5), LocalDate.of(2027, 6, 5))
    }

    @Test
    fun whenTheNextFifthIsAfterTheTargetStartTomorrowWithoutMonthly() {
        clock.date = LocalDate.of(2026, 10, 6)
        val target = LocalDate.of(2026, 10, 30)
        val plan = plan(PlanInput(rupees(10_000), target))

        assertOption(plan.option(Frequency.DAILY), 420, 24, LocalDate.of(2026, 10, 7), target)
        assertOption(plan.option(Frequency.WEEKLY), 2_500, 4, LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 28))
        assertNull(plan.option(Frequency.MONTHLY))
    }

    @Test
    fun destinationSwitchesToGoldAtTwelveWholeMonths() {
        val elevenMonths = plan(PlanInput(rupees(20_000), LocalDate.of(2027, 10, 30)))
        val twelveMonths = plan(PlanInput(rupees(20_000), LocalDate.of(2027, 10, 31)))

        assertEquals(11, elevenMonths.monthsToGoal)
        assertEquals(Destination.PAYTM_MONEY_FUND, elevenMonths.destination)
        assertEquals(12, twelveMonths.monthsToGoal)
        assertEquals(Destination.PAYTM_GOLD, twelveMonths.destination)
    }

    @Test
    fun startDateAfterTargetDateIsAClearError() {
        val error = planError(PlanInput(rupees(36_000), LAPTOP_DATE, startDate = LAPTOP_DATE.plusDays(1)))

        assertEquals(AppError.InvalidPlanInput(PlanInputError.START_AFTER_TARGET), error)
    }

    @Test
    fun targetDateMustBeInTheFutureAndAmountPositive() {
        assertEquals(
            AppError.InvalidPlanInput(PlanInputError.TARGET_DATE_NOT_IN_FUTURE),
            planError(PlanInput(rupees(1_000), TODAY)),
        )
        assertEquals(
            AppError.InvalidPlanInput(PlanInputError.TARGET_NOT_POSITIVE),
            planError(PlanInput(Money.ZERO, LAPTOP_DATE)),
        )
    }
}
