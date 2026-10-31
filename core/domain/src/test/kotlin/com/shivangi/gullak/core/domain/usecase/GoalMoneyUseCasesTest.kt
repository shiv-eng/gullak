package com.shivangi.gullak.core.domain.usecase

import com.shivangi.gullak.core.domain.testing.DomainTestEnvironment
import com.shivangi.gullak.core.domain.testing.TestData
import com.shivangi.gullak.core.domain.testing.TestData.rupees
import com.shivangi.gullak.core.model.goal.DebitKind
import com.shivangi.gullak.core.model.goal.Goal
import com.shivangi.gullak.core.model.money.Money
import com.shivangi.gullak.core.model.plan.GoalStatus
import com.shivangi.gullak.core.model.result.AppError
import com.shivangi.gullak.core.model.result.AppResult
import com.shivangi.gullak.core.model.result.errorOrNull
import com.shivangi.gullak.core.model.result.getOrNull
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalMoneyUseCasesTest {

    private val env = DomainTestEnvironment()

    private suspend fun laptop(): Goal = requireNotNull(env.createGoal(TestData.laptopRequest).getOrNull())

    private suspend fun saved(goal: Goal): Money = env.engine.saved(env.debits.getDebits(goal.id))

    @Test
    fun moneyCanBeAddedAtAnyTimeAndKeepsTheGoalOnTrack() = runTest {
        val goal = laptop()
        env.fastForward(YearMonth.of(2027, 2))

        val debit = env.addMoney(goal.id, rupees(5_000)).getOrNull()

        assertEquals(DebitKind.LUMP_SUM, debit?.kind)
        assertEquals(rupees(23_000), saved(goal))
        assertEquals(GoalStatus.OnTrack, env.engine.status(goal, env.debits.getDebits(goal.id)))
    }

    @Test
    fun addedMoneyCannotGoPastTheGoalOrBeZero() = runTest {
        val goal = laptop()

        assertTrue(env.addMoney(goal.id, rupees(36_001)) is AppResult.Failure)
        assertTrue(env.addMoney(goal.id, Money.ZERO) is AppResult.Failure)
        assertEquals(Money.ZERO, saved(goal))
    }

    @Test
    fun aRefusedAmountSaysWhy() = runTest {
        val goal = laptop()
        env.addMoney(goal.id, rupees(30_000))

        assertEquals(
            AppError.AmountOverGoal(rupees(6_000)),
            env.addMoney(goal.id, rupees(6_001)).errorOrNull(),
        )
        assertEquals(AppError.AmountNotPositive, env.addMoney(goal.id, Money.ZERO).errorOrNull())

        env.addMoney(goal.id, rupees(6_000))
        assertEquals(AppError.GoalFull, env.addMoney(goal.id, rupees(1)).errorOrNull())

        env.payOut(goal.id)
        assertEquals(AppError.GoalPaidOut, env.addMoney(goal.id, rupees(1)).errorOrNull())
    }

    @Test
    fun aGoalFilledEarlyStopsTheRemainingPayments() = runTest {
        val goal = laptop()

        env.addMoney(goal.id, rupees(36_000))

        assertEquals(GoalStatus.Reached, env.engine.status(goal, env.debits.getDebits(goal.id)))
        assertTrue(env.engine.upcomingDebits(goal, env.debits.getDebits(goal.id)).isEmpty())
    }

    @Test
    fun aPayOutIsOnlyPossibleOnceTheGoalIsReachedOrItsDayHasCome() = runTest {
        val goal = laptop()
        env.addMoney(goal.id, rupees(1_000))
        assertFalse(env.payOut.canPayOut(goal, env.debits.getDebits(goal.id)))
        assertTrue(env.payOut(goal.id) is AppResult.Failure)

        env.clock.date = goal.targetDate

        assertTrue(env.payOut.canPayOut(goal, env.debits.getDebits(goal.id)))
        assertEquals(rupees(1_000), env.payOut(goal.id).getOrNull()?.amount)
    }

    @Test
    fun aPayOutHappensOnceAndClosesTheGoal() = runTest {
        val goal = laptop()
        env.addMoney(goal.id, rupees(36_000))

        val paid = env.payOut(goal.id).getOrNull()

        assertEquals(rupees(36_000), paid?.amount)
        assertEquals(DebitKind.PAYOUT, paid?.kind)
        assertEquals(listOf(rupees(36_000)), env.destinations.paidOut.map { it.second })
        assertEquals(rupees(36_000), saved(goal))
        assertTrue(env.payOut(goal.id) is AppResult.Failure)
        assertTrue(env.addMoney(goal.id, rupees(500)) is AppResult.Failure)
        assertEquals(Money.ZERO, env.observeGoals().first().totalSaved)
    }

    @Test
    fun aFailedPayOutRecordsNothing() = runTest {
        val goal = laptop()
        env.addMoney(goal.id, rupees(36_000))
        env.destinations.error = com.shivangi.gullak.core.model.result.AppError.Network

        assertTrue(env.payOut(goal.id) is AppResult.Failure)
        assertTrue(env.payOut.canPayOut(goal, env.debits.getDebits(goal.id)))
    }

    @Test
    fun aTopUpIsOnlyForTheGapOfAProvenSaver() = runTest {
        val goal = laptop()
        assertEquals(TopUpAnswer.NoGap, env.checkTopUp(goal.id).getOrNull())

        // A failed payment that is never made up leaves a gap.
        env.postNextDebit(goal.id, false)
        assertEquals(TopUpAnswer.NotYetProven(rupees(4_500)), env.checkTopUp(goal.id).getOrNull())

        repeat(3) { env.postNextDebit(goal.id, true) }
        assertEquals(TopUpAnswer.Offer(rupees(4_500)), env.checkTopUp(goal.id).getOrNull())
    }

    @Test
    fun askingAboutATopUpChangesNothing() = runTest {
        val goal = laptop()
        val before = env.debits.getDebits(goal.id)

        env.checkTopUp(goal.id)

        assertEquals(before, env.debits.getDebits(goal.id))
    }
}
