package com.shivangi.gullak.feature.paytmhome

import app.cash.turbine.test
import com.shivangi.gullak.core.domain.testing.DomainTestEnvironment
import com.shivangi.gullak.core.domain.testing.TestData
import com.shivangi.gullak.core.model.money.Money
import com.shivangi.gullak.core.model.plan.GoalStatus
import com.shivangi.gullak.core.testing.MainDispatcherRule
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PaytmHomeViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val env = DomainTestEnvironment()

    @Test
    fun showsNoGoalsBeforeAnyAreCreated() = runTest {
        val viewModel = PaytmHomeViewModel(env.observeGoals)

        viewModel.uiState.test {
            val loaded = awaitItem().takeIf { !it.isLoading } ?: awaitItem()
            assertTrue(loaded.goals.isEmpty())
            assertEquals(Money.ZERO, loaded.totalSaved)
        }
    }

    @Test
    fun showsGoalsWithSavedAmountsAndStatus() = runTest {
        env.createGoal(TestData.weddingRequest)
        env.createGoal(TestData.laptopRequest)
        env.fastForward(YearMonth.of(2027, 2))
        val viewModel = PaytmHomeViewModel(env.observeGoals)

        viewModel.uiState.test {
            val loaded = awaitItem().takeIf { !it.isLoading } ?: awaitItem()
            assertEquals(listOf("Bhai ki shaadi", "Laptop"), loaded.goals.map { it.name })
            val laptop = loaded.goals.last()
            assertEquals(Money.fromRupees(18_000), laptop.saved)
            assertEquals(0.5f, laptop.progress)
            assertEquals(GoalStatus.OnTrack, laptop.status)
            assertEquals(Money.fromRupees(32_400), loaded.totalSaved)
        }
    }

    @Test
    fun gullakEntryPointsOpenGullakAndOtherServicesAreDemoOnly() = runTest {
        val viewModel = PaytmHomeViewModel(env.observeGoals)

        viewModel.effect.test {
            viewModel.onEvent(PaytmHomeEvent.GullakClicked)
            assertEquals(PaytmHomeEffect.OpenGullak, awaitItem())
            viewModel.onEvent(PaytmHomeEvent.NewGoalClicked)
            assertEquals(PaytmHomeEffect.OpenNewGoal, awaitItem())
            viewModel.onEvent(PaytmHomeEvent.GoalClicked("laptop"))
            assertEquals(PaytmHomeEffect.OpenGoal("laptop"), awaitItem())
            viewModel.onEvent(PaytmHomeEvent.DemoServiceClicked)
            assertEquals(PaytmHomeEffect.ShowDemoOnly, awaitItem())
        }
    }
}
