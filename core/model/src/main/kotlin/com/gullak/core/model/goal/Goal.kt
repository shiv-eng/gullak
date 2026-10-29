package com.gullak.core.model.goal

import com.gullak.core.model.money.Money
import java.time.LocalDate

enum class Frequency { DAILY, WEEKLY, MONTHLY }

enum class Destination { PAYTM_GOLD, PAYTM_MONEY_FUND }

enum class DebitStatus { SUCCEEDED, FAILED }

enum class DebitKind { SCHEDULED, LUMP_SUM, CATCH_UP }

/** A savings goal. [schedule] is null for goals funded once (too close for a recurring plan). */
data class Goal(
    val id: String,
    val name: String,
    val target: Money,
    val targetDate: LocalDate,
    val dateIsFixed: Boolean,
    val createdOn: LocalDate,
    val destination: Destination,
    val schedule: SavingsSchedule?,
    val languageTag: String,
)

/**
 * A recurring AutoPay plan. Scheduled dates run from [startDate] every frequency period while on or before
 * [endDate]. [baseAmountPerDebit] is the original plan's amount and never changes; a Spread replan raises
 * [amountPerDebit] only. [extraDebits] are one-off catch-up debits; [excusedDates] are failed dates moved
 * to the end of the schedule by an Extend replan.
 */
data class SavingsSchedule(
    val frequency: Frequency,
    val baseAmountPerDebit: Money,
    val amountPerDebit: Money,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val mandateId: String,
    val extraDebits: List<ScheduledDebit> = emptyList(),
    val excusedDates: Set<LocalDate> = emptySet(),
)

data class ScheduledDebit(
    val date: LocalDate,
    val amount: Money,
    val kind: DebitKind = DebitKind.CATCH_UP,
)

/** A debit that has been attempted (posted), whatever its outcome. */
data class Debit(
    val id: String,
    val goalId: String,
    val date: LocalDate,
    val amount: Money,
    val status: DebitStatus,
    val kind: DebitKind,
)
