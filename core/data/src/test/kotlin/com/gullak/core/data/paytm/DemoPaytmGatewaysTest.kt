package com.gullak.core.data.paytm

import com.gullak.core.data.demo.SeedData
import com.gullak.core.domain.testing.DomainTestEnvironment
import com.gullak.core.domain.testing.SequentialIdGenerator
import com.gullak.core.domain.testing.TestData
import com.gullak.core.domain.testing.TestData.rupees
import com.gullak.core.model.goal.DebitStatus
import com.gullak.core.model.goal.Destination
import com.gullak.core.model.goal.Frequency
import com.gullak.core.model.result.getOrNull
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoPaytmGatewaysTest {

    private val ids = SequentialIdGenerator()

    @Test
    fun destinationOpensAfterASimulatedDelay() = runTest {
        val account = DemoSavingsDestinationGateway(ids).open(Destination.PAYTM_GOLD, "goal").getOrNull()

        assertEquals("Paytm Gold SIP", account?.displayName)
        assertEquals(Destination.PAYTM_GOLD, account?.destination)
        assertEquals(600L, currentTime)
    }

    @Test
    fun mandateIsCreatedAndDebitsSucceedUnlessForcedToFail() = runTest {
        val gateway = DemoAutoPayGateway(ids)

        val mandate = gateway.createMandate(
            rupees(4_500),
            Frequency.MONTHLY,
            TestData.FIRST_DEBIT,
            "Laptop",
        ).getOrNull()
        val ok = gateway.debit("m", rupees(4_500), TestData.FIRST_DEBIT, forceFail = false).getOrNull()
        val failed = gateway.debit("m", rupees(4_500), TestData.FIRST_DEBIT, forceFail = true).getOrNull()

        assertEquals(rupees(4_500), mandate?.amount)
        assertTrue(mandate?.id.orEmpty().startsWith("DEMO-MANDATE-"))
        assertEquals(DebitStatus.SUCCEEDED, ok?.status)
        assertEquals(DebitStatus.FAILED, failed?.status)
        assertTrue(failed?.failureReason.orEmpty().contains("demo"))
    }

    @Test
    fun seedDataCreatesSunitasGoalsWithTheEnginesAmounts() = runTest {
        val env = DomainTestEnvironment(seedRequests = SeedData().seedGoals())

        env.resetDemoData(seed = true)

        val (wedding, laptop) = env.goals.goals
        assertEquals("Bhai ki shaadi" to Destination.PAYTM_GOLD, wedding.name to wedding.destination)
        assertEquals(rupees(3_600), wedding.schedule?.amountPerDebit)
        assertEquals(false, wedding.dateIsFixed)
        assertEquals("Laptop" to Destination.PAYTM_MONEY_FUND, laptop.name to laptop.destination)
        assertEquals(rupees(4_500), laptop.schedule?.amountPerDebit)
        assertEquals(true, laptop.dateIsFixed)
        assertTrue(env.debits.debits.isEmpty())
    }
}
