package com.gullak.core.data.paytm

import com.gullak.core.domain.gateway.AutoPayGateway
import com.gullak.core.domain.gateway.DebitResult
import com.gullak.core.domain.gateway.DestinationAccount
import com.gullak.core.domain.gateway.IdGenerator
import com.gullak.core.domain.gateway.Mandate
import com.gullak.core.domain.gateway.SavingsDestinationGateway
import com.gullak.core.model.goal.DebitStatus
import com.gullak.core.model.goal.Destination
import com.gullak.core.model.goal.Frequency
import com.gullak.core.model.money.Money
import com.gullak.core.model.result.AppResult
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.delay

/*
 * Simulated Paytm integrations. No network, no money: each call waits a little so the UI's loading states are
 * visible, then succeeds. Swap these bindings in DataModule for real Paytm Gold / Paytm Money / UPI AutoPay
 * clients without touching the domain or the UI.
 */

internal class DemoSavingsDestinationGateway @Inject constructor(private val ids: IdGenerator) :
    SavingsDestinationGateway {
    override suspend fun open(
        destination: Destination,
        goalId: String,
    ): AppResult<DestinationAccount> {
        delay(OPEN_LATENCY_MS)
        val name = when (destination) {
            Destination.PAYTM_GOLD -> "Paytm Gold SIP"
            Destination.PAYTM_MONEY_FUND -> "Paytm Money · low-risk fund"
        }
        return AppResult.Success(DestinationAccount("DEMO-${ids.newId()}", destination, name))
    }

    private companion object {
        const val OPEN_LATENCY_MS = 600L
    }
}

internal class DemoAutoPayGateway @Inject constructor(private val ids: IdGenerator) : AutoPayGateway {
    override suspend fun createMandate(
        amount: Money,
        frequency: Frequency,
        startDate: LocalDate,
        payeeName: String,
    ): AppResult<Mandate> {
        delay(MANDATE_LATENCY_MS)
        return AppResult.Success(Mandate("DEMO-MANDATE-${ids.newId()}", amount, frequency, startDate))
    }

    override suspend fun debit(
        mandateId: String,
        amount: Money,
        date: LocalDate,
        forceFail: Boolean,
    ): AppResult<DebitResult> {
        delay(DEBIT_LATENCY_MS)
        val result = if (forceFail) {
            DebitResult("DEMO-TXN-${ids.newId()}", DebitStatus.FAILED, failureReason = "Insufficient balance (demo)")
        } else {
            DebitResult("DEMO-TXN-${ids.newId()}", DebitStatus.SUCCEEDED)
        }
        return AppResult.Success(result)
    }

    private companion object {
        const val MANDATE_LATENCY_MS = 600L
        const val DEBIT_LATENCY_MS = 300L
    }
}
