package com.shivangi.gullak.core.domain.plan

import com.shivangi.gullak.core.model.goal.Frequency
import com.shivangi.gullak.core.model.goal.SavingsSchedule
import java.time.LocalDate

/** Date arithmetic for AutoPay schedules. Monthly debits fall on the 5th. */
internal object ScheduleCalendar {
    const val MONTHLY_DEBIT_DAY = 5
    private const val DAYS_PER_WEEK = 7L

    /** The 5th of [date]'s month if not yet past, otherwise the 5th of the next month. */
    fun nextMonthlyDebitDay(date: LocalDate): LocalDate = if (date.dayOfMonth <= MONTHLY_DEBIT_DAY) {
        date.withDayOfMonth(MONTHLY_DEBIT_DAY)
    } else {
        date.plusMonths(1).withDayOfMonth(MONTHLY_DEBIT_DAY)
    }

    /** The k-th debit date (k = 0 is [start]). Monthly dates count from [start] so days never drift. */
    fun nthDate(
        start: LocalDate,
        frequency: Frequency,
        k: Long,
    ): LocalDate = when (frequency) {
        Frequency.DAILY -> start.plusDays(k)
        Frequency.WEEKLY -> start.plusDays(k * DAYS_PER_WEEK)
        Frequency.MONTHLY -> start.plusMonths(k)
    }

    /** All dates from [start] every period while on or before [end]. */
    fun dates(
        start: LocalDate,
        end: LocalDate,
        frequency: Frequency,
    ): List<LocalDate> = generateSequence(0L) { it + 1 }
        .map { nthDate(start, frequency, it) }
        .takeWhile { it <= end }
        .toList()

    fun scheduledDates(schedule: SavingsSchedule): List<LocalDate> =
        dates(schedule.startDate, schedule.endDate, schedule.frequency)

    fun plusOnePeriod(
        date: LocalDate,
        frequency: Frequency,
    ): LocalDate = nthDate(date, frequency, 1)
}
