package com.gullak.feature.creategoal

import com.gullak.core.domain.ai.FollowUpQuestion
import com.gullak.core.domain.analytics.AnalyticsEvent
import com.gullak.core.domain.testing.DomainTestEnvironment
import com.gullak.core.domain.testing.TestData
import com.gullak.core.domain.testing.TestData.rupees
import com.gullak.core.model.ai.GoalLanguage
import com.gullak.core.model.ai.ParsedGoal
import com.gullak.core.model.goal.Destination
import com.gullak.core.model.goal.Frequency
import com.gullak.core.model.result.AppError
import com.gullak.core.model.result.AppResult
import com.gullak.core.testing.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateGoalViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val env = DomainTestEnvironment()

    private fun viewModel() = CreateGoalViewModel(
        understandGoal = env.understandGoal,
        buildPlan = env.buildPlan,
        createGoal = env.createGoal,
        explainer = env.explanationService,
        analytics = env.analytics,
        settings = env.settings,
        clock = env.clock,
    )

    private fun CreateGoalViewModel.typeLaptop() {
        onEvent(CreateGoalEvent.NameChanged("Laptop"))
        onEvent(CreateGoalEvent.AmountChanged("36000"))
        onEvent(CreateGoalEvent.DateSelected(TestData.LAPTOP_DATE))
    }

    @Test
    fun typedGoalShowsTheEnginesMonthlyPlanAndExplanation() = runTest {
        val vm = viewModel()

        vm.typeLaptop()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(Frequency.MONTHLY, state.frequency)
        assertEquals(rupees(4_500), state.chosen?.amountPerDebit)
        assertEquals(8, state.chosen?.numberOfDebits)
        assertEquals(Destination.PAYTM_MONEY_FUND, state.plan?.destination)
        assertEquals("Explanation", state.explanation)
        assertTrue(state.canStart)
    }

    @Test
    fun choosingDailyOrWeeklyShowsThoseAmounts() = runTest {
        val vm = viewModel()
        vm.typeLaptop()

        vm.onEvent(CreateGoalEvent.FrequencySelected(Frequency.DAILY))
        assertEquals(rupees(155), vm.uiState.value.chosen?.amountPerDebit)

        vm.onEvent(CreateGoalEvent.FrequencySelected(Frequency.WEEKLY))
        assertEquals(rupees(1_060), vm.uiState.value.chosen?.amountPerDebit)
    }

    @Test
    fun amountBelowMinimumBlocksThePlan() = runTest {
        val vm = viewModel()
        vm.typeLaptop()

        vm.onEvent(CreateGoalEvent.AmountChanged("400"))

        assertEquals(InputProblem.AMOUNT_TOO_LOW, vm.uiState.value.problem)
        assertNull(vm.uiState.value.plan)
        assertFalse(vm.uiState.value.canStart)
    }

    @Test
    fun amountFieldKeepsOnlyDigits() = runTest {
        val vm = viewModel()

        vm.onEvent(CreateGoalEvent.AmountChanged("₹36,000"))

        assertEquals("36000", vm.uiState.value.amountText)
    }

    @Test
    fun spokenGoalFillsTheForm() = runTest {
        env.understandingService.result = AppResult.Success(
            ParsedGoal("Bhai ki shaadi", rupees(50_000), TestData.WEDDING_DATE, null, GoalLanguage.HINGLISH, null),
        )
        val vm = viewModel()

        vm.onEvent(CreateGoalEvent.GoalSpoken("Bhai ki shaadi, agle December tak, 50 hazaar"))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Bhai ki shaadi", state.name)
        assertEquals("50000", state.amountText)
        assertEquals(TestData.WEDDING_DATE, state.targetDate)
        assertEquals(rupees(3_600), state.chosen?.amountPerDebit)
        assertEquals(Destination.PAYTM_GOLD, state.plan?.destination)
        assertTrue(AnalyticsEvent.VoiceUsed in env.analytics.events)
    }

    @Test
    fun incompleteSpeechAsksAFollowUpAndKeepsWhatWasUnderstood() = runTest {
        env.understandingService.result = AppResult.Success(
            ParsedGoal("Naya phone", null, null, null, GoalLanguage.HINGLISH, "Kitne paise aur kab tak?"),
        )
        val vm = viewModel()

        vm.onEvent(CreateGoalEvent.GoalSpoken("Mujhe naya phone lena hai"))
        advanceUntilIdle()

        assertEquals("Naya phone", vm.uiState.value.name)
        assertEquals(FollowUpQuestion.FromModel("Kitne paise aur kab tak?"), vm.uiState.value.followUp)
        assertNull(vm.uiState.value.plan)
    }

    @Test
    fun understandingFailureIsShownAndTheFormStillWorks() = runTest {
        env.understandingService.result = AppResult.Failure(AppError.Timeout)
        val vm = viewModel()

        vm.onEvent(CreateGoalEvent.GoalSpoken("kuch bhi"))
        advanceUntilIdle()
        vm.typeLaptop()

        assertTrue(vm.uiState.value.voiceFailed)
        assertTrue(vm.uiState.value.canStart)
    }

    @Test
    fun confirmingAutoPayCreatesTheGoal() = runTest {
        val vm = viewModel()
        vm.typeLaptop()
        vm.onEvent(CreateGoalEvent.DateFixedChanged(true))
        advanceUntilIdle()

        vm.onEvent(CreateGoalEvent.StartClicked)
        assertTrue(vm.uiState.value.showMandate)
        vm.onEvent(CreateGoalEvent.MandateConfirmed)
        assertEquals(Stage.BANK, vm.uiState.value.stage)
        vm.onEvent(CreateGoalEvent.BankSelected(DemoBankAccount.SAMPLE_BANK_SAVINGS))
        vm.onEvent(CreateGoalEvent.BankProceed)
        assertEquals(Stage.APPROVAL, vm.uiState.value.stage)
        repeat(CreateGoalUiState.APPROVAL_LENGTH) { vm.onEvent(CreateGoalEvent.ApprovalDigit) }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(Stage.DONE, state.stage)
        assertEquals(0, state.approvalDigits)
        val goal = env.goals.goals.single()
        assertEquals(goal, state.createdGoal)
        assertEquals(rupees(4_500), goal.schedule?.amountPerDebit)
        assertTrue(goal.dateIsFixed)
        assertEquals(rupees(4_500), env.autoPay.mandates.single().amount)
    }

    @Test
    fun failedAutoPaySetupReturnsToTheForm() = runTest {
        env.autoPay.createMandateError = AppError.Gateway("down")
        val vm = viewModel()
        vm.typeLaptop()
        advanceUntilIdle()

        vm.onEvent(CreateGoalEvent.StartClicked)
        vm.onEvent(CreateGoalEvent.MandateConfirmed)
        vm.onEvent(CreateGoalEvent.BankProceed)
        repeat(CreateGoalUiState.APPROVAL_LENGTH) { vm.onEvent(CreateGoalEvent.ApprovalDigit) }
        advanceUntilIdle()

        assertEquals(Stage.EDITING, vm.uiState.value.stage)
        assertTrue(vm.uiState.value.createFailed)
        assertTrue(env.goals.goals.isEmpty())
    }

    @Test
    fun targetTooCloseCannotStartASip() = runTest {
        val vm = viewModel()
        vm.onEvent(CreateGoalEvent.NameChanged("Gift"))
        vm.onEvent(CreateGoalEvent.AmountChanged("5000"))
        vm.onEvent(CreateGoalEvent.DateSelected(LocalDate.of(2026, 11, 3)))

        assertEquals(InputProblem.TOO_CLOSE_FOR_SIP, vm.uiState.value.problem)
        assertFalse(vm.uiState.value.canStart)
    }

    @Test
    fun approvalStepCountsDigitsAndCanGoBack() = runTest {
        val vm = viewModel()
        vm.typeLaptop()
        advanceUntilIdle()
        vm.onEvent(CreateGoalEvent.StartClicked)
        vm.onEvent(CreateGoalEvent.MandateConfirmed)
        vm.onEvent(CreateGoalEvent.BankProceed)

        vm.onEvent(CreateGoalEvent.ApprovalDigit)
        vm.onEvent(CreateGoalEvent.ApprovalDigit)
        vm.onEvent(CreateGoalEvent.ApprovalDelete)
        assertEquals(1, vm.uiState.value.approvalDigits)

        vm.onEvent(CreateGoalEvent.StepBack)
        assertEquals(Stage.BANK, vm.uiState.value.stage)
        assertEquals(0, vm.uiState.value.approvalDigits)
        vm.onEvent(CreateGoalEvent.StepBack)
        assertEquals(Stage.EDITING, vm.uiState.value.stage)
        assertTrue(env.goals.goals.isEmpty())
    }
}
