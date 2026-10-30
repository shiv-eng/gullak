package com.gullak.feature.goaldetail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.gullak.core.domain.testing.DomainTestEnvironment
import com.gullak.core.domain.testing.TestData
import com.gullak.core.domain.testing.TestData.rupees
import com.gullak.core.model.goal.DebitKind
import com.gullak.core.model.goal.DebitStatus
import com.gullak.core.model.goal.Goal
import com.gullak.core.model.plan.GoalStatus
import com.gullak.core.model.plan.ReplanChoice
import com.gullak.core.model.result.getOrNull
import com.gullak.core.model.settings.AppSettings
import com.gullak.core.testing.MainDispatcherRule
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GoalDetailViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val env = DomainTestEnvironment()

    private fun viewModel(goal: Goal) = GoalDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(GoalDetailViewModel.GOAL_ID_KEY to goal.id)),
        observeDetail = env.observeGoalDetail,
        settings = env.settings,
        postNextDebit = env.postNextDebit,
        getReplanOptions = env.getReplanOptions,
        applyReplan = env.applyReplan,
        explainer = env.explanationService,
    )

    private suspend fun laptopUpToFebruary(): Goal {
        val laptop = requireNotNull(env.createGoal(TestData.laptopRequest).getOrNull())
        env.fastForward(YearMonth.of(2027, 2))
        return laptop
    }

    @Test
    fun showsSavedAmountStatusAndHistory() = runTest {
        val laptop = laptopUpToFebruary()

        val state = viewModel(laptop).uiState.value

        assertEquals(rupees(18_000), state.detail?.summary?.saved)
        assertEquals(GoalStatus.OnTrack, state.detail?.summary?.status)
        assertEquals(4, state.detail?.debits?.size)
        assertEquals(LocalDate.of(2027, 3, 5), state.detail?.summary?.nextDebit?.date)
        assertTrue(state.demoControls)
        assertNull(state.unresolvedFailure)
    }

    @Test
    fun demoControlsFollowDemoMode() = runTest {
        val laptop = laptopUpToFebruary()
        val vm = viewModel(laptop)

        env.settings.setDemoMode(false)

        assertFalse(vm.uiState.value.demoControls)
        assertEquals(AppSettings(demoMode = false), env.settings.settings.value)
    }

    @Test
    fun failedAutoPayOpensTheReplanSheetAndCatchUpReturnsToOnTrack() = runTest {
        val laptop = laptopUpToFebruary()
        val vm = viewModel(laptop)

        vm.effect.test {
            vm.onEvent(GoalDetailEvent.SimulateFailedDebit)
            advanceUntilIdle()
            val posted = awaitItem() as GoalDetailEffect.DebitPosted
            assertEquals(DebitStatus.FAILED, posted.debit.status)
            assertEquals(LocalDate.of(2027, 3, 5), posted.debit.date)

            val replan = requireNotNull(vm.uiState.value.replan)
            assertEquals(GoalStatus.Behind(rupees(4_500)), replan.offer.status)
            assertEquals(2, replan.offer.choices.size)
            assertTrue(replan.offer.choices[0] is ReplanChoice.CatchUp)
            assertEquals("Explanation", replan.explanation)
            assertEquals(posted.debit, vm.uiState.value.unresolvedFailure)

            vm.onEvent(GoalDetailEvent.ApplyReplan)
            advanceUntilIdle()
            assertEquals(GoalDetailEffect.PlanUpdated, awaitItem())
            assertNull(vm.uiState.value.replan)

            vm.onEvent(GoalDetailEvent.SimulateNextDebit)
            advanceUntilIdle()
            val first = awaitItem() as GoalDetailEffect.DebitPosted
            vm.onEvent(GoalDetailEvent.SimulateNextDebit)
            advanceUntilIdle()
            val second = awaitItem() as GoalDetailEffect.DebitPosted

            assertEquals(DebitKind.CATCH_UP to LocalDate.of(2027, 3, 15), first.debit.kind to first.debit.date)
            assertEquals(DebitKind.CATCH_UP to LocalDate.of(2027, 3, 25), second.debit.kind to second.debit.date)
        }
        assertEquals(rupees(22_500), vm.uiState.value.detail?.summary?.saved)
        assertEquals(GoalStatus.OnTrack, vm.uiState.value.detail?.summary?.status)
        assertNull(vm.uiState.value.unresolvedFailure)
    }

    @Test
    fun spreadRaisesTheNextPayments() = runTest {
        val laptop = laptopUpToFebruary()
        val vm = viewModel(laptop)
        vm.onEvent(GoalDetailEvent.SimulateFailedDebit)
        advanceUntilIdle()

        vm.onEvent(GoalDetailEvent.ChoiceSelected(1))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.replan?.offer?.choices?.get(1) is ReplanChoice.Spread)
        vm.onEvent(GoalDetailEvent.ApplyReplan)
        advanceUntilIdle()

        assertEquals(rupees(6_000), vm.uiState.value.detail?.summary?.nextDebit?.amount)
        assertEquals(LocalDate.of(2027, 4, 5), vm.uiState.value.detail?.summary?.nextDebit?.date)
    }

    @Test
    fun weddingDateCanMoveSoExtendIsOffered() = runTest {
        val wedding = requireNotNull(env.createGoal(TestData.weddingRequest).getOrNull())
        env.fastForward(YearMonth.of(2027, 2))
        val vm = viewModel(wedding)

        vm.onEvent(GoalDetailEvent.SimulateFailedDebit)
        advanceUntilIdle()

        val choices = vm.uiState.value.replan?.offer?.choices.orEmpty()
        assertEquals(3, choices.size)
        assertEquals(ReplanChoice.Extend(LocalDate.of(2028, 1, 31)), choices.last())
    }

    @Test
    fun dismissingTheSheetKeepsTheFixPlanBanner() = runTest {
        val laptop = laptopUpToFebruary()
        val vm = viewModel(laptop)
        vm.onEvent(GoalDetailEvent.SimulateFailedDebit)
        advanceUntilIdle()

        vm.onEvent(GoalDetailEvent.DismissReplan)

        assertNull(vm.uiState.value.replan)
        val failed = requireNotNull(vm.uiState.value.unresolvedFailure)
        vm.onEvent(GoalDetailEvent.OpenReplan(failed.id))
        advanceUntilIdle()
        assertEquals(failed, vm.uiState.value.replan?.offer?.failedDebit)
    }

    @Test
    fun simulatingPastTheLastPaymentSaysNothingIsDue() = runTest {
        val laptop = requireNotNull(env.createGoal(TestData.laptopRequest).getOrNull())
        env.fastForward(YearMonth.of(2027, 6))
        val vm = viewModel(laptop)

        vm.effect.test {
            vm.onEvent(GoalDetailEvent.SimulateNextDebit)
            advanceUntilIdle()
            assertEquals(GoalDetailEffect.NothingDue, awaitItem())
        }
        assertEquals(GoalStatus.Reached, vm.uiState.value.detail?.summary?.status)
    }
}
