package com.shivangi.gullak.core.domain.plan

import com.shivangi.gullak.core.domain.testing.MutableClock
import com.shivangi.gullak.core.domain.testing.TestData
import com.shivangi.gullak.core.domain.testing.TestData.LAPTOP_DATE
import com.shivangi.gullak.core.domain.testing.TestData.TODAY
import com.shivangi.gullak.core.domain.testing.TestData.debit
import com.shivangi.gullak.core.domain.testing.TestData.laptopGoal
import com.shivangi.gullak.core.domain.testing.TestData.monthlyDebits
import com.shivangi.gullak.core.domain.testing.TestData.rupees
import com.shivangi.gullak.core.domain.testing.TestData.weddingGoal
import com.shivangi.gullak.core.model.goal.Debit
import com.shivangi.gullak.core.model.goal.DebitKind
import com.shivangi.gullak.core.model.goal.DebitStatus
import com.shivangi.gullak.core.model.goal.Frequency
import com.shivangi.gullak.core.model.goal.Goal
import com.shivangi.gullak.core.model.goal.ScheduledDebit
import com.shivangi.gullak.core.model.plan.GoalStatus
import com.shivangi.gullak.core.model.plan.ReplanChoice
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Section 5 rules 7 and 8: status and replanning, using the laptop example. Debits on 5 Nov, 5 Dec, 5 Jan and
 * 5 Feb succeed; the 5 Mar 2027 debit fails.
 */
class PlanEngineReplanTest {

    private val clock = MutableClock(TODAY)
    private val engine = PlanEngine(clock)

    private val march5: LocalDate = LocalDate.of(2027, 3, 5)
    private val march15: LocalDate = LocalDate.of(2027, 3, 15)
    private val march25: LocalDate = LocalDate.of(2027, 3, 25)

    private val laptop = laptopGoal()
    private val laptopFailed = debit("laptop", march5, rupees(4_500), DebitStatus.FAILED)
    private val laptopUntilFailure: List<Debit> = monthlyDebits("laptop", rupees(4_500), 4) + laptopFailed

    private fun statusOn(
        date: LocalDate,
        goal: Goal,
        debits: List<Debit>,
    ): GoalStatus = engine.status(goal, debits, date)

    @Test
    fun newGoalWithNoDebitsDueIsOnTrack() {
        assertEquals(GoalStatus.OnTrack, statusOn(TODAY, laptop, emptyList()))
    }

    @Test
    fun aDueDebitThatHasNotPostedCountsAsBehind() {
        assertEquals(GoalStatus.Behind(rupees(4_500)), statusOn(TestData.FIRST_DEBIT, laptop, emptyList()))
    }

    @Test
    fun failedMarchDebitLeavesTheLaptopBehindByOneDebit() {
        assertEquals(rupees(18_000), engine.saved(laptopUntilFailure))
        assertEquals(GoalStatus.Behind(rupees(4_500)), statusOn(march5, laptop, laptopUntilFailure))
    }

    @Test
    fun laptopReplanOffersCatchUpAndSpreadButNotExtend() {
        clock.date = march5

        val choices = engine.replanOptions(laptop, laptopFailed)

        assertEquals(
            listOf(
                ReplanChoice.CatchUp(
                    listOf(
                        ScheduledDebit(march15, rupees(2_250), DebitKind.CATCH_UP),
                        ScheduledDebit(march25, rupees(2_250), DebitKind.CATCH_UP),
                    ),
                ),
                ReplanChoice.Spread(newAmountPerDebit = rupees(6_000), remainingDebits = 3),
            ),
            choices,
        )
    }

    @Test
    fun catchUpClosesTheGapOverTwoDebits() {
        clock.date = march5
        val catchUp = engine.replanOptions(laptop, laptopFailed).filterIsInstance<ReplanChoice.CatchUp>().single()
        val goal = engine.applyReplan(laptop, catchUp, laptopFailed)

        assertEquals(GoalStatus.CatchingUp(rupees(4_500)), statusOn(march5, goal, laptopUntilFailure))

        val afterFirst = laptopUntilFailure + debit("laptop", march15, rupees(2_250), kind = DebitKind.CATCH_UP)
        assertEquals(GoalStatus.CatchingUp(rupees(2_250)), statusOn(march15, goal, afterFirst))

        val afterSecond = afterFirst + debit("laptop", march25, rupees(2_250), kind = DebitKind.CATCH_UP)
        assertEquals(rupees(22_500), engine.saved(afterSecond))
        assertEquals(GoalStatus.OnTrack, statusOn(march25, goal, afterSecond))
    }

    @Test
    fun catchUpDebitsComeBeforeTheNextScheduledDebit() {
        clock.date = march5
        val catchUp = engine.replanOptions(laptop, laptopFailed).filterIsInstance<ReplanChoice.CatchUp>().single()
        val goal = engine.applyReplan(laptop, catchUp, laptopFailed)

        val upcoming = engine.upcomingDebits(goal, laptopUntilFailure).take(3)

        assertEquals(
            listOf(
                ScheduledDebit(march15, rupees(2_250), DebitKind.CATCH_UP),
                ScheduledDebit(march25, rupees(2_250), DebitKind.CATCH_UP),
                ScheduledDebit(LocalDate.of(2027, 4, 5), rupees(4_500), DebitKind.SCHEDULED),
            ),
            upcoming,
        )
    }

    @Test
    fun spreadShrinksTheGapEachMonthUntilTheGoalIsReached() {
        val spread = ReplanChoice.Spread(rupees(6_000), 3)
        val goal = engine.applyReplan(laptop, spread, laptopFailed)
        assertEquals(rupees(4_500), goal.schedule?.baseAmountPerDebit)
        assertEquals(rupees(6_000), goal.schedule?.amountPerDebit)

        val april = laptopUntilFailure + debit("laptop", LocalDate.of(2027, 4, 5), rupees(6_000))
        assertEquals(GoalStatus.CatchingUp(rupees(3_000)), statusOn(LocalDate.of(2027, 4, 5), goal, april))

        val may = april + debit("laptop", LocalDate.of(2027, 5, 5), rupees(6_000))
        assertEquals(GoalStatus.CatchingUp(rupees(1_500)), statusOn(LocalDate.of(2027, 5, 5), goal, may))

        val june = may + debit("laptop", LocalDate.of(2027, 6, 5), rupees(6_000))
        assertEquals(rupees(36_000), engine.saved(june))
        assertEquals(GoalStatus.Reached, statusOn(LocalDate.of(2027, 6, 5), goal, june))
    }

    @Test
    fun weddingDateCanMoveSoExtendIsOffered() {
        clock.date = march5
        val wedding = weddingGoal()
        val failed = debit("wedding", march5, rupees(3_600), DebitStatus.FAILED)

        val choices = engine.replanOptions(wedding, failed)

        assertEquals(
            listOf(
                ReplanChoice.CatchUp(
                    listOf(
                        ScheduledDebit(march15, rupees(1_800), DebitKind.CATCH_UP),
                        ScheduledDebit(march25, rupees(1_800), DebitKind.CATCH_UP),
                    ),
                ),
                // 9 debits left (Apr to Dec): ₹3,600 / 9 = ₹400 more each.
                ReplanChoice.Spread(rupees(4_000), 9),
                ReplanChoice.Extend(LocalDate.of(2028, 1, 31)),
            ),
            choices,
        )
    }

    @Test
    fun extendExcusesTheFailedDateAndAddsADebitAtTheEnd() {
        val wedding = weddingGoal()
        val failed = debit("wedding", march5, rupees(3_600), DebitStatus.FAILED)
        val debits = monthlyDebits("wedding", rupees(3_600), 4) + failed

        val extended = engine.applyReplan(wedding, ReplanChoice.Extend(LocalDate.of(2028, 1, 31)), failed)

        assertEquals(LocalDate.of(2028, 1, 31), extended.targetDate)
        assertEquals(setOf(march5), extended.schedule?.excusedDates)
        assertEquals(GoalStatus.OnTrack, statusOn(march5, extended, debits))
        assertEquals(LocalDate.of(2028, 1, 5), engine.upcomingDebits(extended, debits).last().date)
        assertEquals(10, engine.upcomingDebits(extended, debits).size)
    }

    @Test
    fun catchUpSkipsAPastFifteenthAndUsesTheNextDay() {
        clock.date = LocalDate.of(2027, 3, 20)

        val catchUp = engine.replanOptions(laptop, laptopFailed).filterIsInstance<ReplanChoice.CatchUp>().single()

        assertEquals(listOf(LocalDate.of(2027, 3, 21), march25), catchUp.debits.map { it.date })
    }

    @Test
    fun catchUpUsesTheNextTwoDaysWhenBothDatesArePast() {
        clock.date = LocalDate.of(2027, 3, 26)

        val catchUp = engine.replanOptions(laptop, laptopFailed).filterIsInstance<ReplanChoice.CatchUp>().single()

        assertEquals(listOf(LocalDate.of(2027, 3, 27), LocalDate.of(2027, 3, 28)), catchUp.debits.map { it.date })
    }

    @Test
    fun weeklySchedulesGetNoCatchUp() {
        val weekly = laptop.copy(schedule = TestData.monthlySchedule(rupees(1_060), LAPTOP_DATE, Frequency.WEEKLY))
        val failed = debit("laptop", LocalDate.of(2026, 11, 12), rupees(1_060), DebitStatus.FAILED)
        clock.date = failed.date

        val choices = engine.replanOptions(weekly, failed)

        // 32 weekly debits remain: ₹1,060 / 32 = ₹33.13 → ₹40 more each.
        assertEquals(listOf(ReplanChoice.Spread(rupees(1_100), 32)), choices)
    }

    @Test
    fun aDailySpreadAddsOnlyWhatTheMissedPaymentNeeds() {
        val daily = laptop.copy(schedule = TestData.monthlySchedule(rupees(1_670), LAPTOP_DATE, Frequency.DAILY))
        val failed = debit("laptop", TestData.FIRST_DEBIT, rupees(1_670), DebitStatus.FAILED)
        clock.date = failed.date

        // 237 daily debits remain: ₹1,670 / 237 = ₹7.05 → ₹10 more each, not ₹50.
        assertEquals(listOf(ReplanChoice.Spread(rupees(1_680), 237)), engine.replanOptions(daily, failed))
    }

    @Test
    fun anotherFailureDuringARecoveryIsBehindAgain() {
        val goal = engine.applyReplan(laptop, ReplanChoice.Spread(rupees(6_000), 3), laptopFailed)
        val april = LocalDate.of(2027, 4, 5)
        val failedAgain = laptopUntilFailure + debit("laptop", april, rupees(6_000), DebitStatus.FAILED)

        assertEquals(GoalStatus.Behind(rupees(9_000)), statusOn(april, goal, failedAgain))
    }

    @Test
    fun spreadIsNotOfferedWhenTheLastDebitFails() {
        val failedLast = debit("laptop", LocalDate.of(2027, 6, 5), rupees(4_500), DebitStatus.FAILED)
        clock.date = failedLast.date

        val choices = engine.replanOptions(laptop, failedLast)

        assertTrue(choices.single() is ReplanChoice.CatchUp)
    }

    @Test
    fun goalWithoutScheduleHasNoReplanOrUpcomingDebits() {
        val oneTime = laptop.copy(schedule = null)

        assertTrue(engine.replanOptions(oneTime, laptopFailed).isEmpty())
        assertTrue(engine.upcomingDebits(oneTime, emptyList()).isEmpty())
        assertEquals(oneTime, engine.applyReplan(oneTime, ReplanChoice.Spread(rupees(1), 1), laptopFailed))
    }

    @Test
    fun lumpSumCountsTowardsExpectedAndSaved() {
        val lump = debit("laptop", TODAY, rupees(6_000), kind = DebitKind.LUMP_SUM)
        val goal = laptop.copy(schedule = TestData.monthlySchedule(rupees(3_750), LAPTOP_DATE))

        assertEquals(GoalStatus.OnTrack, statusOn(TODAY, goal, listOf(lump)))
        assertEquals(GoalStatus.Behind(rupees(3_750)), statusOn(TestData.FIRST_DEBIT, goal, listOf(lump)))
    }
}
