package com.gullak.feature.creategoal.voice

import com.gullak.core.domain.ai.FollowUpReason
import com.gullak.core.domain.ai.SpeechError
import com.gullak.core.domain.ai.SpeechEvent
import com.gullak.core.domain.testing.DomainTestEnvironment
import com.gullak.core.domain.testing.TestData
import com.gullak.core.domain.testing.TestData.rupees
import com.gullak.core.model.ai.GoalLanguage
import com.gullak.core.model.ai.ParsedGoal
import com.gullak.core.model.goal.Frequency
import com.gullak.core.model.money.Money
import com.gullak.core.model.plan.PlanOption
import com.gullak.core.model.result.AppError
import com.gullak.core.model.result.AppResult
import com.gullak.core.testing.MainDispatcherRule
import com.gullak.feature.creategoal.DemoBankAccount
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

/** The whole flow by voice alone, with fake speech. */
class VoiceGoalViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val env = DomainTestEnvironment()
    private val laptop = ParsedGoal("Laptop", rupees(36_000), TestData.LAPTOP_DATE, null, GoalLanguage.HINGLISH, null)

    private fun viewModel() = VoiceGoalViewModel(
        understandGoal = env.understandGoal,
        buildPlan = env.buildPlan,
        createGoal = env.createGoal,
        explainer = env.explanationService,
        voice = env.voice,
        speech = env.speech,
        settings = env.settings,
        lines = FakeLines,
    )

    /** Everything the user says, in order. */
    private fun says(vararg replies: String) {
        replies.forEach { env.speech.heard += listOf(SpeechEvent.Final(it)) }
    }

    @Test
    fun aGoalIsStartedFromGreetingToDoneByTalkingAlone() = runTest {
        env.understandingService.result = AppResult.Success(laptop)
        says("laptop ke liye 36 hazaar june tak", "haan", "mahine wala", "haan kar do")
        val vm = viewModel()

        vm.onEvent(VoiceGoalEvent.MicReady)
        advanceUntilIdle()

        assertEquals(
            listOf(
                "greeting",
                "readback Laptop",
                "plan DAILY, WEEKLY, MONTHLY",
                "Explanation autopay MONTHLY SBI_SAVINGS",
                "started Laptop",
            ),
            env.voice.spoken,
        )
        val goal = env.goals.goals.single()
        assertEquals("Laptop", goal.name)
        assertEquals(rupees(4_500), goal.schedule?.amountPerDebit)
        assertEquals(TalkStep.DONE, vm.uiState.value.step)
    }

    @Test
    fun aMissingDetailIsAskedAndTheAnswerContinuesTheSameGoal() = runTest {
        env.understandingService.result = AppResult.Success(
            ParsedGoal("Naya phone", null, null, null, GoalLanguage.HINGLISH, "Kitne paise aur kab tak?"),
        )
        says("mujhe naya phone lena hai")
        val vm = viewModel()

        vm.onEvent(VoiceGoalEvent.MicReady)
        advanceUntilIdle()

        assertTrue("Kitne paise aur kab tak?" in env.voice.spoken)
        assertEquals("Naya phone", vm.uiState.value.name)
        assertEquals(TalkStep.GOAL, vm.uiState.value.step)
    }

    @Test
    fun noAtTheReadBackAsksWhatToChangeAndACorrectionIsReadBackAgain() = runTest {
        env.understandingService.result = AppResult.Success(laptop)
        says("laptop", "nahi", "40 hazaar")
        val vm = viewModel()

        vm.onEvent(VoiceGoalEvent.MicReady)
        advanceUntilIdle()

        assertEquals(
            listOf("greeting", "readback Laptop", "follow WHAT_TO_CHANGE", "readback Laptop"),
            env.voice.spoken.take(4),
        )
        assertEquals(2, env.understandingService.requests.size)
    }

    @Test
    fun aBankNamedInTheReplyIsUsedAndCountsAsAYes() = runTest {
        env.understandingService.result = AppResult.Success(laptop)
        says("laptop", "haan", "mahine", "HDFC se")
        val vm = viewModel()

        vm.onEvent(VoiceGoalEvent.MicReady)
        advanceUntilIdle()

        assertEquals(DemoBankAccount.HDFC_SAVINGS, vm.uiState.value.bank)
        assertEquals(1, env.goals.goals.size)
    }

    @Test
    fun anUnclearReplyAtAutoPayNeverStartsAGoal() = runTest {
        env.understandingService.result = AppResult.Success(laptop)
        says("laptop", "haan", "mahine", "kya matlab")
        val vm = viewModel()

        vm.onEvent(VoiceGoalEvent.MicReady)
        advanceUntilIdle()

        assertTrue(env.goals.goals.isEmpty())
        assertTrue("yes or no" in env.voice.spoken)
    }

    @Test
    fun aNoAtAutoPayNeverStartsAGoal() = runTest {
        env.understandingService.result = AppResult.Success(laptop)
        says("laptop", "haan", "mahine", "nahi")
        val vm = viewModel()

        vm.onEvent(VoiceGoalEvent.MicReady)
        advanceUntilIdle()

        assertTrue(env.goals.goals.isEmpty())
    }

    @Test
    fun aDateTooCloseIsExplainedAndAskedAgain() = runTest {
        env.understandingService.result = AppResult.Success(laptop.copy(targetDate = TestData.TODAY.plusDays(3)))
        says("laptop", "haan")
        val vm = viewModel()

        vm.onEvent(VoiceGoalEvent.MicReady)
        advanceUntilIdle()

        assertTrue("too close" in env.voice.spoken)
        assertNull(vm.uiState.value.date)
    }

    @Test
    fun silenceGetsOneNudgeThenGullakWaitsForATap() = runTest {
        val vm = viewModel()

        vm.onEvent(VoiceGoalEvent.MicReady)
        advanceUntilIdle()

        assertEquals(listOf("greeting", "not heard"), env.voice.spoken)
        assertEquals(TalkPhase.WAITING, vm.uiState.value.phase)

        says("laptop")
        env.understandingService.result = AppResult.Success(laptop)
        vm.onEvent(VoiceGoalEvent.OrbTapped)
        advanceUntilIdle()
        assertEquals("Laptop", vm.uiState.value.name)
    }

    @Test
    fun aFailureToUnderstandIsSaidAloudAndTheTalkGoesOn() = runTest {
        env.understandingService.result = AppResult.Failure(AppError.Timeout)
        says("laptop")
        val vm = viewModel()

        vm.onEvent(VoiceGoalEvent.MicReady)
        advanceUntilIdle()

        assertTrue("did not understand" in env.voice.spoken)
    }

    @Test
    fun withVoiceRepliesOffGullakStaysSilentButStillListens() = runTest {
        env.settings.setVoiceReplies(false)
        env.understandingService.result = AppResult.Success(laptop)
        says("laptop")
        val vm = viewModel()

        vm.onEvent(VoiceGoalEvent.MicReady)
        advanceUntilIdle()

        assertTrue(env.voice.spoken.isEmpty())
        assertEquals("Laptop", vm.uiState.value.name)
    }

    @Test
    fun aDeniedMicIsShownAndNothingIsSaid() = runTest {
        val vm = viewModel()

        vm.onEvent(VoiceGoalEvent.MicDenied)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.micDenied)
        assertTrue(env.voice.spoken.isEmpty())
    }

    @Test
    fun aPhoneThatCannotRecogniseInsideTheAppUsesItsOwnScreen() = runTest {
        env.speech.heard += listOf(SpeechEvent.Error(SpeechError.UNAVAILABLE))
        env.understandingService.result = AppResult.Success(laptop)
        val vm = viewModel()

        vm.onEvent(VoiceGoalEvent.MicReady)
        advanceUntilIdle()
        assertEquals(VoiceGoalEffect.OpenPhoneSpeech, vm.effect.first())

        vm.onEvent(VoiceGoalEvent.PhoneSpeechHeard("laptop"))
        advanceUntilIdle()
        assertEquals("Laptop", vm.uiState.value.name)
    }

    @Test
    fun repliesNamingAPlanOrBankAreRead() {
        val options = listOf(option(Frequency.DAILY), option(Frequency.WEEKLY), option(Frequency.MONTHLY))

        assertEquals(Frequency.MONTHLY, optionFrom("mahine wala theek hai", options)?.frequency)
        assertEquals(Frequency.WEEKLY, optionFrom("हफ़्ते वाला", options)?.frequency)
        assertEquals(Frequency.DAILY, optionFrom("pehla", options)?.frequency)
        assertEquals(Frequency.MONTHLY, optionFrom("last wala", options)?.frequency)
        assertNull(optionFrom("kya matlab", options))
        assertNull(optionFrom("roz", listOf(option(Frequency.WEEKLY))))

        assertEquals(DemoBankAccount.ICICI_SAVINGS, bankFrom("ICICI se karo"))
        assertEquals(DemoBankAccount.SBI_SAVINGS, bankFrom("state bank"))
        assertNull(bankFrom("haan"))

        assertTrue(wantsAnother("ek aur banana hai"))
        assertFalse(wantsAnother("bas"))
    }

    private fun option(frequency: Frequency) =
        PlanOption(frequency, rupees(100), 1, TestData.TODAY.plusDays(1), TestData.TODAY.plusDays(1))
}

private object FakeLines : VoiceLines {
    override fun defaultLanguage() = GoalLanguage.HINGLISH

    override fun greeting(language: GoalLanguage) = "greeting"

    override fun greetingAgain(language: GoalLanguage) = "again"

    override fun moment(language: GoalLanguage) = "moment"

    override fun didNotHear(language: GoalLanguage) = "not heard"

    override fun didNotUnderstand(language: GoalLanguage) = "did not understand"

    override fun didNotCatchChoice(language: GoalLanguage) = "which"

    override fun didNotCatchYes(language: GoalLanguage) = "yes or no"

    override fun followUp(
        language: GoalLanguage,
        reason: FollowUpReason,
    ) = "follow $reason"

    override fun readback(
        language: GoalLanguage,
        name: String,
        target: Money,
        date: LocalDate,
    ) = "readback $name"

    override fun plan(
        language: GoalLanguage,
        name: String,
        options: List<PlanOption>,
    ) = "plan ${options.joinToString { it.frequency.name }}"

    override fun dateTooClose(language: GoalLanguage) = "too close"

    override fun autoPay(
        language: GoalLanguage,
        option: PlanOption,
        bank: DemoBankAccount,
    ) = "autopay ${option.frequency} $bank"

    override fun started(
        language: GoalLanguage,
        name: String,
        firstDebit: LocalDate?,
    ) = "started $name"

    override fun couldNotStart(language: GoalLanguage) = "failed"

    override fun goodbye(language: GoalLanguage) = "bye"
}
