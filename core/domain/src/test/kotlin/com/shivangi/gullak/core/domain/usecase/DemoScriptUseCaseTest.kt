package com.shivangi.gullak.core.domain.usecase

import com.shivangi.gullak.core.domain.analytics.AnalyticsEvent
import com.shivangi.gullak.core.domain.testing.DomainTestEnvironment
import com.shivangi.gullak.core.domain.testing.TestData
import com.shivangi.gullak.core.domain.testing.TestData.rupees
import com.shivangi.gullak.core.model.goal.DebitKind
import com.shivangi.gullak.core.model.goal.DebitStatus
import com.shivangi.gullak.core.model.goal.Goal
import com.shivangi.gullak.core.model.money.Money
import com.shivangi.gullak.core.model.plan.GoalStatus
import com.shivangi.gullak.core.model.plan.ReplanChoice
import com.shivangi.gullak.core.model.result.AppError
import com.shivangi.gullak.core.model.result.AppResult
import com.shivangi.gullak.core.model.result.getOrNull
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Section 14's demo script, driven through the use cases with fakes. */
class DemoScriptUseCaseTest {

    private val env = DomainTestEnvironment()

    private suspend fun createSunitasGoals(): Pair<Goal, Goal> {
        val wedding = requireNotNull(env.createGoal(TestData.weddingRequest).getOrNull())
        val laptop = requireNotNull(env.createGoal(TestData.laptopRequest).getOrNull())
        return wedding to laptop
    }

    private suspend fun summary(goalId: String): GoalSummary =
        env.observeGoals().first().goals.single { it.goal.id == goalId }

    @Test
    fun newGoalsStartOnTrackWithNothingSaved() = runTest {
        val (wedding, laptop) = createSunitasGoals()

        val overview = env.observeGoals().first()

        assertEquals(listOf(wedding.id, laptop.id), overview.goals.map { it.goal.id })
        assertTrue(overview.goals.all { it.status == GoalStatus.OnTrack && it.saved == Money.ZERO })
        assertEquals(TestData.FIRST_DEBIT, summary(laptop.id).nextDebit?.date)
        assertEquals(rupees(4_500), summary(laptop.id).nextDebit?.amount)
    }

    @Test
    fun fastForwardToFebruaryFillsBothJars() = runTest {
        val (wedding, laptop) = createSunitasGoals()

        val date = env.fastForward(YearMonth.of(2027, 2))

        assertEquals(AppResult.Success(LocalDate.of(2027, 2, 28)), date)
        assertEquals(LocalDate.of(2027, 2, 28), env.engine.today())
        val overview = env.observeGoals().first()
        assertEquals(rupees(18_000), summary(laptop.id).saved)
        assertEquals(rupees(14_400), summary(wedding.id).saved)
        assertEquals(rupees(14_400), overview.savedInGold)
        assertEquals(rupees(18_000), overview.savedInFund)
        assertEquals(rupees(32_400), overview.totalSaved)
        assertTrue(overview.goals.all { it.status == GoalStatus.OnTrack })
        assertEquals(LocalDate.of(2027, 3, 5), summary(laptop.id).nextDebit?.date)
    }

    @Test
    fun failedAutoPayThenCatchUpReturnsTheLaptopToOnTrack() = runTest {
        val (wedding, laptop) = createSunitasGoals()
        env.fastForward(YearMonth.of(2027, 2))

        // Simulate failed AutoPay on 5 March: the clock moves and only the laptop debit fails.
        val failed = requireNotNull(env.postNextDebit(laptop.id, succeed = false).getOrNull())
        assertEquals(LocalDate.of(2027, 3, 5), failed.date)
        assertEquals(DebitStatus.FAILED, failed.status)
        assertEquals(LocalDate.of(2027, 3, 5), env.engine.today())
        assertEquals(GoalStatus.Behind(rupees(4_500)), summary(laptop.id).status)
        assertEquals(GoalStatus.OnTrack, summary(wedding.id).status)
        assertEquals(rupees(18_000), summary(wedding.id).saved)

        val offer = requireNotNull(env.getReplanOptions(laptop.id, failed.id).getOrNull())
        assertEquals(GoalStatus.Behind(rupees(4_500)), offer.status)
        val catchUp = offer.choices.filterIsInstance<ReplanChoice.CatchUp>().single()
        assertEquals(listOf(rupees(2_250), rupees(2_250)), catchUp.debits.map { it.amount })
        assertEquals(rupees(6_000), offer.choices.filterIsInstance<ReplanChoice.Spread>().single().newAmountPerDebit)
        assertTrue(offer.choices.none { it is ReplanChoice.Extend })
        assertTrue(AnalyticsEvent.ReplanShown(2) in env.analytics.events)

        val status = env.applyReplan(laptop.id, failed.id, catchUp)
        assertEquals(AppResult.Success(GoalStatus.CatchingUp(rupees(4_500))), status)
        assertTrue(AnalyticsEvent.ReplanApplied("catch_up") in env.analytics.events)

        val first = requireNotNull(env.postNextDebit(laptop.id, succeed = true).getOrNull())
        val second = requireNotNull(env.postNextDebit(laptop.id, succeed = true).getOrNull())

        assertEquals(LocalDate.of(2027, 3, 15) to DebitKind.CATCH_UP, first.date to first.kind)
        assertEquals(LocalDate.of(2027, 3, 25) to DebitKind.CATCH_UP, second.date to second.kind)
        assertEquals(LocalDate.of(2027, 3, 25), env.engine.today())
        assertEquals(rupees(22_500), summary(laptop.id).saved)
        assertEquals(GoalStatus.OnTrack, summary(laptop.id).status)
        assertEquals(GoalStatus.OnTrack, summary(wedding.id).status)
    }

    @Test
    fun goalDetailListsDebitsNewestFirst() = runTest {
        val (_, laptop) = createSunitasGoals()
        env.fastForward(YearMonth.of(2026, 12))

        val detail = requireNotNull(env.observeGoalDetail(laptop.id).first())

        assertEquals(listOf(LocalDate.of(2026, 12, 5), TestData.FIRST_DEBIT), detail.debits.map { it.date })
        assertEquals(rupees(9_000), detail.summary.saved)
        assertEquals(LocalDate.of(2027, 1, 5), detail.summary.nextDebit?.date)
    }

    @Test
    fun reachingTheTargetIsLoggedOnce() = runTest {
        val (_, laptop) = createSunitasGoals()

        env.fastForward(YearMonth.of(2027, 6))

        assertEquals(GoalStatus.Reached, summary(laptop.id).status)
        assertEquals(1, env.analytics.events.count { it == AnalyticsEvent.GoalReached })
    }

    @Test
    fun demoControlsRejectImpossibleRequests() = runTest {
        assertEquals(AppResult.Failure(AppError.DateNotInFuture), env.fastForward(YearMonth.of(2026, 9)))
        assertTrue(env.postNextDebit("missing", succeed = true) is AppResult.Failure)
        assertTrue(env.getReplanOptions("missing", "missing") is AppResult.Failure)

        val (_, laptop) = createSunitasGoals()
        val succeeded = requireNotNull(env.postNextDebit(laptop.id, succeed = true).getOrNull())
        // Replan only applies to a failed debit.
        assertTrue(env.getReplanOptions(laptop.id, succeeded.id) is AppResult.Failure)
    }

    @Test
    fun resetClearsEverythingAndSeedsSunitasGoals() = runTest {
        createSunitasGoals()
        env.fastForward(YearMonth.of(2027, 2))

        val result = env.resetDemoData(seed = true)

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(TestData.TODAY, env.engine.today())
        assertEquals(listOf("Bhai ki shaadi", "Laptop"), env.goals.goals.map { it.name })
        assertEquals(listOf(rupees(3_600), rupees(4_500)), env.goals.goals.map { it.schedule?.amountPerDebit })
        assertTrue(env.debits.debits.isEmpty())

        env.resetDemoData(seed = false)
        assertTrue(env.goals.goals.isEmpty())
    }
}
