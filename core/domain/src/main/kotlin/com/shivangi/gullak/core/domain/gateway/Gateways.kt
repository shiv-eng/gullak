package com.shivangi.gullak.core.domain.gateway

import com.shivangi.gullak.core.domain.usecase.NewGoalRequest
import com.shivangi.gullak.core.model.goal.DebitStatus
import com.shivangi.gullak.core.model.goal.Destination
import com.shivangi.gullak.core.model.goal.Frequency
import com.shivangi.gullak.core.model.money.Money
import com.shivangi.gullak.core.model.result.AppResult
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Paytm Gold SIP or Paytm Money fund account that receives a goal's savings. Mocked in the prototype. */
interface SavingsDestinationGateway {
    suspend fun open(
        destination: Destination,
        goalId: String,
    ): AppResult<DestinationAccount>

    /** Sells what the goal holds and sends the money to the user's bank. */
    suspend fun payOut(
        destination: Destination,
        goalId: String,
        amount: Money,
    ): AppResult<Unit>
}

data class DestinationAccount(
    val id: String,
    val destination: Destination,
    val displayName: String,
)

/** UPI AutoPay. Mocked in the prototype; the AI never calls it. */
interface AutoPayGateway {
    suspend fun createMandate(
        amount: Money,
        frequency: Frequency,
        startDate: LocalDate,
        payeeName: String,
    ): AppResult<Mandate>

    /** A declined debit is a [DebitResult] with status FAILED, not an error. [forceFail] drives the demo. */
    suspend fun debit(
        mandateId: String,
        amount: Money,
        date: LocalDate,
        forceFail: Boolean,
    ): AppResult<DebitResult>
}

data class Mandate(
    val id: String,
    val amount: Money,
    val frequency: Frequency,
    val startDate: LocalDate,
)

data class DebitResult(
    val transactionId: String,
    val status: DebitStatus,
    val failureReason: String? = null,
)

/** Moves the app's notion of "today" for the demo. The injected `Clock` reflects the same date. */
interface DemoClockController {
    val today: Flow<LocalDate>

    suspend fun setDate(date: LocalDate)

    /** Back to the demo start date (demo mode) or the real today. */
    suspend fun reset()
}

/** Festival dates from the bundled calendar; null when the festival or year is unknown. */
interface FestivalCalendar {
    suspend fun nextOccurrence(
        festival: String,
        after: LocalDate,
    ): LocalDate?
}

/** Sunita's demo goals, created through the normal goal-creation path. */
interface DemoSeedSource {
    fun seedGoals(): List<NewGoalRequest>
}

fun interface IdGenerator {
    fun newId(): String
}
