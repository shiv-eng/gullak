package com.shivangi.gullak.core.domain.usecase

import com.shivangi.gullak.core.domain.analytics.AnalyticsEvent
import com.shivangi.gullak.core.domain.plan.PlanInput
import com.shivangi.gullak.core.domain.testing.DomainTestEnvironment
import com.shivangi.gullak.core.domain.testing.TestData
import com.shivangi.gullak.core.domain.testing.TestData.rupees
import com.shivangi.gullak.core.model.goal.DebitKind
import com.shivangi.gullak.core.model.goal.DebitStatus
import com.shivangi.gullak.core.model.goal.Destination
import com.shivangi.gullak.core.model.goal.Frequency
import com.shivangi.gullak.core.model.result.AppError
import com.shivangi.gullak.core.model.result.AppResult
import com.shivangi.gullak.core.model.result.PlanInputError
import com.shivangi.gullak.core.model.result.getOrNull
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateGoalUseCaseTest {

    private val env = DomainTestEnvironment()

    @Test
    fun createsTheLaptopGoalWithAMonthlyMandate() = runTest {
        val goal = env.createGoal(TestData.laptopRequest).getOrNull()

        requireNotNull(goal)
        val schedule = requireNotNull(goal.schedule)
        assertEquals(Frequency.MONTHLY, schedule.frequency)
        assertEquals(rupees(4_500), schedule.amountPerDebit)
        assertEquals(rupees(4_500), schedule.baseAmountPerDebit)
        assertEquals(TestData.FIRST_DEBIT, schedule.startDate)
        assertEquals(TestData.LAPTOP_DATE, schedule.endDate)
        assertEquals(Destination.PAYTM_MONEY_FUND, goal.destination)
        assertEquals(TestData.TODAY, goal.createdOn)
        assertTrue(goal.dateIsFixed)

        assertEquals(listOf(goal), env.goals.goals)
        assertTrue(env.debits.debits.isEmpty())
        assertEquals(rupees(4_500), env.autoPay.mandates.single().amount)
        assertEquals(schedule.mandateId, env.autoPay.mandates.single().id)
        assertEquals(Destination.PAYTM_MONEY_FUND to goal.id, env.destinations.opened.single())
        assertEquals(
            listOf(
                AnalyticsEvent.GoalCreated(Destination.PAYTM_MONEY_FUND),
                AnalyticsEvent.PlanStarted(Frequency.MONTHLY),
            ),
            env.analytics.events,
        )
    }

    @Test
    fun lumpSumIsRecordedAndLowersTheMonthlyAmount() = runTest {
        val goal = env.createGoal(TestData.laptopRequest.copy(lumpSum = rupees(6_000))).getOrNull()

        assertEquals(rupees(3_750), goal?.schedule?.amountPerDebit)
        val lump = env.debits.debits.single()
        assertEquals(rupees(6_000), lump.amount)
        assertEquals(DebitKind.LUMP_SUM, lump.kind)
        assertEquals(DebitStatus.SUCCEEDED, lump.status)
        assertEquals(TestData.TODAY, lump.date)
    }

    @Test
    fun nothingIsSavedWhenTheMandateFails() = runTest {
        env.autoPay.createMandateError = AppError.Gateway("AutoPay unavailable")

        val result = env.createGoal(TestData.laptopRequest)

        assertEquals(AppResult.Failure(AppError.Gateway("AutoPay unavailable")), result)
        assertTrue(env.goals.goals.isEmpty())
        assertTrue(env.analytics.events.isEmpty())
    }

    @Test
    fun nothingIsSavedWhenTheDestinationCannotBeOpened() = runTest {
        env.destinations.error = AppError.Network

        assertEquals(AppResult.Failure(AppError.Network), env.createGoal(TestData.laptopRequest))
        assertTrue(env.goals.goals.isEmpty())
        assertTrue(env.autoPay.mandates.isEmpty())
    }

    @Test
    fun recurringPlanNeedsAFrequency() = runTest {
        val result = env.createGoal(TestData.laptopRequest.copy(frequency = null))

        assertEquals(AppResult.Failure(AppError.InvalidPlanInput(PlanInputError.FREQUENCY_NOT_AVAILABLE)), result)
    }

    @Test
    fun oneTimeGoalIsSavedWithoutSchedule() = runTest {
        val request = TestData.laptopRequest.copy(
            target = rupees(2_000),
            targetDate = TestData.TODAY.plusDays(3),
            frequency = null,
            lumpSum = rupees(2_000),
        )

        val goal = env.createGoal(request).getOrNull()

        assertNull(goal?.schedule)
        assertTrue(env.autoPay.mandates.isEmpty())
        assertEquals(rupees(2_000), env.debits.debits.single().amount)
    }

    @Test
    fun invalidPlanInputIsReturned() = runTest {
        val result = env.createGoal(TestData.laptopRequest.copy(targetDate = TestData.TODAY))

        assertEquals(AppResult.Failure(AppError.InvalidPlanInput(PlanInputError.TARGET_DATE_NOT_IN_FUTURE)), result)
    }

    @Test
    fun buildPlanLogsTheDestinationShown() {
        val plan = env.buildPlan(PlanInput(rupees(50_000), TestData.WEDDING_DATE)).getOrNull()

        assertEquals(Destination.PAYTM_GOLD, plan?.destination)
        assertEquals(listOf(AnalyticsEvent.DestinationShown(Destination.PAYTM_GOLD)), env.analytics.events)
    }
}
