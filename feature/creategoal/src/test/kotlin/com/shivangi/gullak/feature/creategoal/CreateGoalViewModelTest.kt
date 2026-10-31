package com.shivangi.gullak.feature.creategoal

import androidx.lifecycle.SavedStateHandle
import com.shivangi.gullak.core.domain.ai.FollowUpQuestion
import com.shivangi.gullak.core.domain.ai.FollowUpReason
import com.shivangi.gullak.core.domain.ai.SpeechError
import com.shivangi.gullak.core.domain.ai.SpeechEvent
import com.shivangi.gullak.core.domain.analytics.AnalyticsEvent
import com.shivangi.gullak.core.domain.testing.DomainTestEnvironment
import com.shivangi.gullak.core.domain.testing.TestData
import com.shivangi.gullak.core.domain.testing.TestData.rupees
import com.shivangi.gullak.core.model.ai.GoalLanguage
import com.shivangi.gullak.core.model.ai.ParsedGoal
import com.shivangi.gullak.core.model.ai.Speaker
import com.shivangi.gullak.core.model.goal.Destination
import com.shivangi.gullak.core.model.goal.Frequency
import com.shivangi.gullak.core.model.result.AppError
import com.shivangi.gullak.core.model.result.AppResult
import com.shivangi.gullak.core.testing.MainDispatcherRule
import com.shivangi.gullak.feature.creategoal.voice.TalkIo
import java.time.LocalDate
import kotlinx.coroutines.flow.first
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

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) = CreateGoalViewModel(
        savedState = savedState,
        understandGoal = env.understandGoal,
        buildPlan = env.buildPlan,
        createGoal = env.createGoal,
        explainer = env.explanationService,
        analytics = env.analytics,
        io = TalkIo(env.voice, env.speech, env.settings, env.bargeIn, env.analytics),
        clock = env.clock,
    )

    private fun CreateGoalViewModel.typeLaptop() {
        onEvent(CreateGoalEvent.NameChanged("Laptop"))
        onEvent(CreateGoalEvent.AmountChanged("36000"))
        onEvent(CreateGoalEvent.DateSelected(TestData.LAPTOP_DATE))
    }

    @Test
    fun aTypedFormComesBackAfterAndroidClosesTheApp() = runTest {
        val saved = SavedStateHandle()
        viewModel(saved).typeLaptop()
        advanceUntilIdle()

        val state = viewModel(saved).uiState.value

        assertEquals("Laptop", state.name)
        assertEquals("36000", state.amountText)
        assertEquals(TestData.LAPTOP_DATE, state.targetDate)
        assertEquals(rupees(4_500), state.chosen?.amountPerDebit)
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
        vm.onEvent(CreateGoalEvent.BankSelected(DemoBankAccount.HDFC_SAVINGS))
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

    @Test
    fun lumpSumLowersTheSipAndIsRecordedWithTheGoal() = runTest {
        val vm = viewModel()
        vm.typeLaptop()

        vm.onEvent(CreateGoalEvent.LumpSumChanged("6000"))
        advanceUntilIdle()
        assertEquals(rupees(3_750), vm.uiState.value.chosen?.amountPerDebit)

        vm.onEvent(CreateGoalEvent.StartClicked)
        vm.onEvent(CreateGoalEvent.MandateConfirmed)
        vm.onEvent(CreateGoalEvent.BankProceed)
        repeat(CreateGoalUiState.APPROVAL_LENGTH) { vm.onEvent(CreateGoalEvent.ApprovalDigit) }
        advanceUntilIdle()

        assertEquals(rupees(3_750), env.goals.goals.single().schedule?.amountPerDebit)
        assertEquals(rupees(6_000), env.debits.debits.single().amount)
    }

    @Test
    fun lumpSumCoveringTheWholeGoalIsRejected() = runTest {
        val vm = viewModel()
        vm.typeLaptop()

        vm.onEvent(CreateGoalEvent.LumpSumChanged("36000"))

        assertEquals(InputProblem.LUMP_SUM_TOO_HIGH, vm.uiState.value.problem)
        assertFalse(vm.uiState.value.canStart)
    }

    @Test
    fun speakReadsTextAloudAndStartingAutoPayStopsIt() = runTest {
        val vm = viewModel()
        vm.typeLaptop()
        advanceUntilIdle()

        vm.onEvent(CreateGoalEvent.Speak("Why this plan"))
        advanceUntilIdle()
        assertEquals(listOf("Why this plan"), env.voice.spoken)

        vm.onEvent(CreateGoalEvent.StartClicked)
        assertEquals(1, env.voice.stops)
    }

    private val spokenGoal =
        ParsedGoal("Laptop", rupees(36_000), TestData.LAPTOP_DATE, null, GoalLanguage.HINGLISH, null)
    private val askAmount = ParsedGoal("Naya phone", null, null, null, GoalLanguage.HINGLISH, "Kitne paise?")

    @Test
    fun listeningShowsWhatIsHeardAndTheFinalTextFillsTheForm() = runTest {
        env.understandingService.result = AppResult.Success(spokenGoal)
        env.speech.heard += listOf(SpeechEvent.Listening, SpeechEvent.Partial("Lap"), SpeechEvent.Final("Laptop"))
        val vm = viewModel()

        vm.onEvent(CreateGoalEvent.ListenClicked)
        advanceUntilIdle()

        assertEquals("Laptop", vm.uiState.value.name)
        assertEquals("Laptop", vm.uiState.value.transcript)
        assertFalse(vm.uiState.value.isListening)
        assertNull(vm.uiState.value.partialTranscript)
    }

    @Test
    fun aFollowUpIsReadAloudOnceAndThenTheMicListensAgain() = runTest {
        env.understandingService.result = AppResult.Success(askAmount)
        val vm = viewModel()
        vm.onEvent(CreateGoalEvent.GoalSpoken("Mujhe naya phone lena hai"))
        advanceUntilIdle()

        repeat(2) { vm.onEvent(CreateGoalEvent.FollowUpShown("Kitne paise?")) }
        advanceUntilIdle()

        assertEquals(listOf("Kitne paise?"), env.voice.spoken)
        assertEquals(1, env.speech.listens)
    }

    @Test
    fun withVoiceRepliesOffAFollowUpIsNotReadButTheMicStillListens() = runTest {
        env.settings.setVoiceReplies(false)
        env.understandingService.result = AppResult.Success(askAmount)
        val vm = viewModel()
        vm.onEvent(CreateGoalEvent.GoalSpoken("Mujhe naya phone lena hai"))
        advanceUntilIdle()

        vm.onEvent(CreateGoalEvent.FollowUpShown("Kitne paise?"))
        advanceUntilIdle()

        assertTrue(env.voice.spoken.isEmpty())
        assertEquals(1, env.speech.listens)
    }

    @Test
    fun aSpokenGoalIsReadBackOnceAndYesReadsTheExplanation() = runTest {
        env.understandingService.result = AppResult.Success(spokenGoal)
        env.speech.heard += listOf(SpeechEvent.Final("haan theek hai"))
        val vm = viewModel()
        vm.onEvent(CreateGoalEvent.GoalSpoken("Laptop"))
        advanceUntilIdle()
        assertEquals(GoalReadback("Laptop", rupees(36_000), TestData.LAPTOP_DATE), vm.uiState.value.readback)

        repeat(2) { vm.onEvent(CreateGoalEvent.ReadbackShown("Sahi hai?")) }
        advanceUntilIdle()

        assertEquals(listOf("Sahi hai?", "Explanation"), env.voice.spoken)
        assertEquals(1, env.speech.listens)
        assertNull(vm.uiState.value.readback)

        vm.onEvent(CreateGoalEvent.AmountChanged("40000"))
        advanceUntilIdle()
        assertEquals(listOf("Sahi hai?", "Explanation"), env.voice.spoken)
    }

    @Test
    fun noAfterAReadBackAsksWhatToChangeAndKeepsTheGoal() = runTest {
        env.understandingService.result = AppResult.Success(spokenGoal)
        env.speech.heard += listOf(SpeechEvent.Final("nahi"))
        val vm = viewModel()
        vm.onEvent(CreateGoalEvent.GoalSpoken("Laptop"))
        advanceUntilIdle()

        vm.onEvent(CreateGoalEvent.ReadbackShown("Sahi hai?"))
        advanceUntilIdle()

        assertEquals(FollowUpQuestion.Standard(FollowUpReason.WHAT_TO_CHANGE), vm.uiState.value.followUp)
        assertEquals("Laptop", vm.uiState.value.name)
    }

    @Test
    fun aCorrectionAfterAReadBackIsUnderstoodWithTheGoalAndReadBackAgain() = runTest {
        env.understandingService.result = AppResult.Success(spokenGoal)
        env.speech.heard += listOf(SpeechEvent.Final("nahi 40 hazaar"))
        val vm = viewModel()
        vm.onEvent(CreateGoalEvent.GoalSpoken("Laptop"))
        advanceUntilIdle()

        env.understandingService.result = AppResult.Success(spokenGoal.copy(targetAmount = rupees(40_000)))
        vm.onEvent(CreateGoalEvent.ReadbackShown("Sahi hai?"))
        advanceUntilIdle()

        assertEquals("40000", vm.uiState.value.amountText)
        assertEquals(rupees(40_000), vm.uiState.value.readback?.target)
        val users = env.understandingService.requests.last().filter { it.speaker == Speaker.USER }
        assertEquals(listOf("Laptop", "nahi 40 hazaar"), users.map { it.text })
    }

    @Test
    fun withVoiceRepliesOffTheReadBackIsOnlyOnScreen() = runTest {
        env.settings.setVoiceReplies(false)
        env.understandingService.result = AppResult.Success(spokenGoal)
        val vm = viewModel()
        vm.onEvent(CreateGoalEvent.GoalSpoken("Laptop"))
        advanceUntilIdle()

        vm.onEvent(CreateGoalEvent.ReadbackShown("Sahi hai?"))
        advanceUntilIdle()

        assertTrue(env.voice.spoken.isEmpty())
        assertEquals(0, env.speech.listens)
    }

    @Test
    fun aNewSpokenGoalAfterAConfirmedOneStartsAFreshConversation() = runTest {
        env.understandingService.result = AppResult.Success(spokenGoal)
        val vm = viewModel()
        vm.onEvent(CreateGoalEvent.GoalSpoken("Laptop"))
        vm.onEvent(CreateGoalEvent.GoalSpoken("haan"))
        vm.onEvent(CreateGoalEvent.GoalSpoken("Goa trip"))
        advanceUntilIdle()

        assertEquals(listOf("Goa trip"), env.understandingService.requests.last().map { it.text })
    }

    @Test
    fun silenceAndADeniedMicAreShownAsProblems() = runTest {
        env.speech.heard += listOf(SpeechEvent.Error(SpeechError.NO_SPEECH))
        val vm = viewModel()

        vm.onEvent(CreateGoalEvent.ListenClicked)
        advanceUntilIdle()
        assertEquals(VoiceProblem.NOT_HEARD, vm.uiState.value.voiceProblem)

        vm.onEvent(CreateGoalEvent.MicDenied)
        assertEquals(VoiceProblem.MIC_DENIED, vm.uiState.value.voiceProblem)
    }

    @Test
    fun aPhoneThatCannotRecogniseInsideTheAppUsesItsOwnSpeechScreen() = runTest {
        env.speech.heard += listOf(SpeechEvent.Error(SpeechError.UNAVAILABLE))
        val vm = viewModel()

        vm.onEvent(CreateGoalEvent.ListenClicked)
        advanceUntilIdle()

        assertEquals(CreateGoalEffect.OpenPhoneSpeech, vm.effect.first())
    }

    @Test
    fun aMissingHindiVoiceShowsTheHintOnlyOnce() = runTest {
        env.voice.voiceAvailable = false
        env.understandingService.result = AppResult.Success(spokenGoal)
        val vm = viewModel()
        vm.onEvent(CreateGoalEvent.GoalSpoken("Laptop"))
        advanceUntilIdle()
        vm.onEvent(CreateGoalEvent.ReadbackShown("Sahi hai?"))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showVoiceHint)
        assertTrue(env.settings.settings.value.voiceHintShown)

        vm.onEvent(CreateGoalEvent.VoiceHintDismissed)
        vm.onEvent(CreateGoalEvent.Speak("Phir se"))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showVoiceHint)
    }
}
