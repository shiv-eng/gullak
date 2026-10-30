package com.gullak.feature.settings

import app.cash.turbine.test
import com.gullak.core.domain.testing.DomainTestEnvironment
import com.gullak.core.domain.testing.TestData
import com.gullak.core.domain.testing.TestData.rupees
import com.gullak.core.model.settings.SpeechLanguage
import com.gullak.core.testing.MainDispatcherRule
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val env = DomainTestEnvironment()

    private fun viewModel() = SettingsViewModel(
        settings = env.settings,
        demoClock = env.demoClock,
        fastForward = env.fastForward,
        resetDemoData = env.resetDemoData,
        aiInfo = env.aiInfo,
    )

    @Test
    fun showsTheDemoDateModelAndNextTwelveMonths() = runTest {
        val state = viewModel().uiState.value

        assertEquals(TestData.TODAY, state.today)
        assertEquals("gemini-test", state.modelId)
        assertTrue(state.liveModelConfigured)
        assertEquals(YearMonth.of(2026, 11), state.fastForwardMonths.first())
        assertEquals(12, state.fastForwardMonths.size)
    }

    @Test
    fun togglesDemoModeAndSpeechLanguage() = runTest {
        val vm = viewModel()

        vm.onEvent(SettingsEvent.DemoModeChanged(false))
        vm.onEvent(SettingsEvent.SpeechLanguageChanged(SpeechLanguage.HINDI_INDIA))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.settings.demoMode)
        assertEquals(SpeechLanguage.HINDI_INDIA, vm.uiState.value.settings.speechLanguage)
    }

    @Test
    fun fastForwardPostsDueSipsAndMovesTheDate() = runTest {
        env.createGoal(TestData.laptopRequest)
        val vm = viewModel()

        vm.effect.test {
            vm.onEvent(SettingsEvent.FastForward(YearMonth.of(2027, 2)))
            advanceUntilIdle()
            assertEquals(SettingsEffect.FastForwarded(LocalDate.of(2027, 2, 28)), awaitItem())
        }
        assertEquals(LocalDate.of(2027, 2, 28), vm.uiState.value.today)
        assertEquals(rupees(18_000), env.observeGoals().first().totalSaved)
    }

    @Test
    fun backToTodayResetsTheDemoDate() = runTest {
        val vm = viewModel()
        vm.onEvent(SettingsEvent.FastForward(YearMonth.of(2027, 1)))
        advanceUntilIdle()

        vm.onEvent(SettingsEvent.BackToToday)
        advanceUntilIdle()

        assertEquals(TestData.TODAY, vm.uiState.value.today)
    }

    @Test
    fun resetCanSeedSunitasGoalsOrLeaveHomeEmpty() = runTest {
        env.createGoal(TestData.laptopRequest)
        val vm = viewModel()

        vm.effect.test {
            vm.onEvent(SettingsEvent.ResetDemo(seed = true))
            advanceUntilIdle()
            assertEquals(SettingsEffect.DemoReset(seeded = true), awaitItem())
            assertEquals(2, env.goals.goals.size)

            vm.onEvent(SettingsEvent.ResetDemo(seed = false))
            advanceUntilIdle()
            assertEquals(SettingsEffect.DemoReset(seeded = false), awaitItem())
            assertTrue(env.goals.goals.isEmpty())
        }
    }
}
