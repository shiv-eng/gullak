package com.shivangi.gullak.core.domain.testing

import com.shivangi.gullak.core.domain.usecase.NewGoalRequest
import com.shivangi.gullak.core.model.ai.GoalLanguage
import com.shivangi.gullak.core.model.goal.Debit
import com.shivangi.gullak.core.model.goal.DebitKind
import com.shivangi.gullak.core.model.goal.DebitStatus
import com.shivangi.gullak.core.model.goal.Destination
import com.shivangi.gullak.core.model.goal.Frequency
import com.shivangi.gullak.core.model.goal.Goal
import com.shivangi.gullak.core.model.goal.SavingsSchedule
import com.shivangi.gullak.core.model.money.Money
import java.time.LocalDate

/** Sunita's demo goals from the brief, with today = 31 Oct 2026. */
object TestData {
    val TODAY: LocalDate = LocalDate.of(2026, 10, 31)
    val FIRST_DEBIT: LocalDate = LocalDate.of(2026, 11, 5)

    val LAPTOP_DATE: LocalDate = LocalDate.of(2027, 6, 30)
    val WEDDING_DATE: LocalDate = LocalDate.of(2027, 12, 31)

    fun rupees(amount: Long): Money = Money.fromRupees(amount)

    val laptopRequest = NewGoalRequest(
        name = "Laptop",
        target = rupees(36_000),
        targetDate = LAPTOP_DATE,
        dateIsFixed = true,
        frequency = Frequency.MONTHLY,
        language = GoalLanguage.HINGLISH,
    )

    val weddingRequest = NewGoalRequest(
        name = "Bhai ki shaadi",
        target = rupees(50_000),
        targetDate = WEDDING_DATE,
        dateIsFixed = false,
        frequency = Frequency.MONTHLY,
        language = GoalLanguage.HINGLISH,
    )

    /** Laptop on ₹4,500 a month from 5 Nov 2026, date fixed, to Paytm Money. */
    fun laptopGoal(
        id: String = "laptop",
        schedule: SavingsSchedule = monthlySchedule(rupees(4_500), LAPTOP_DATE),
    ) = Goal(
        id = id,
        name = "Laptop",
        target = rupees(36_000),
        targetDate = LAPTOP_DATE,
        dateIsFixed = true,
        createdOn = TODAY,
        destination = Destination.PAYTM_MONEY_FUND,
        schedule = schedule,
        languageTag = GoalLanguage.HINGLISH.tag,
    )

    /** Wedding on ₹3,600 a month from 5 Nov 2026, date can move, to Paytm Gold. */
    fun weddingGoal(
        id: String = "wedding",
        schedule: SavingsSchedule = monthlySchedule(rupees(3_600), WEDDING_DATE),
    ) = Goal(
        id = id,
        name = "Bhai ki shaadi",
        target = rupees(50_000),
        targetDate = WEDDING_DATE,
        dateIsFixed = false,
        createdOn = TODAY,
        destination = Destination.PAYTM_GOLD,
        schedule = schedule,
        languageTag = GoalLanguage.HINGLISH.tag,
    )

    fun monthlySchedule(
        amount: Money,
        endDate: LocalDate,
        frequency: Frequency = Frequency.MONTHLY,
    ) = SavingsSchedule(
        frequency = frequency,
        baseAmountPerDebit = amount,
        amountPerDebit = amount,
        startDate = FIRST_DEBIT,
        endDate = endDate,
        mandateId = "mandate",
    )

    fun debit(
        goalId: String,
        date: LocalDate,
        amount: Money,
        status: DebitStatus = DebitStatus.SUCCEEDED,
        kind: DebitKind = DebitKind.SCHEDULED,
        id: String = "$goalId-$kind-$date",
    ) = Debit(id, goalId, date, amount, status, kind)

    /** Successful monthly debits on the 5th from Nov 2026 for [months] months. */
    fun monthlyDebits(
        goalId: String,
        amount: Money,
        months: Int,
    ): List<Debit> = (0 until months).map { debit(goalId, FIRST_DEBIT.plusMonths(it.toLong()), amount) }
}
