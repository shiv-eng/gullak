package com.gullak.core.data.db

import com.gullak.core.model.goal.DebitKind
import com.gullak.core.model.goal.ScheduledDebit
import com.gullak.core.model.money.Money
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun datesRoundTripAsIsoStrings() {
        val date = LocalDate.of(2028, 2, 29)

        assertEquals("2028-02-29", converters.dateToString(date))
        assertEquals(date, converters.stringToDate("2028-02-29"))
        assertNull(converters.stringToDate(null))
    }

    @Test
    fun excusedDatesRoundTripSorted() {
        val dates = setOf(LocalDate.of(2027, 4, 5), LocalDate.of(2027, 3, 5))

        val stored = converters.datesToString(dates)

        assertEquals("2027-03-05;2027-04-05", stored)
        assertEquals(dates, converters.stringToDates(stored))
        assertEquals(emptySet<LocalDate>(), converters.stringToDates(converters.datesToString(emptySet())))
    }

    @Test
    fun extraDebitsRoundTrip() {
        val debits = listOf(
            ScheduledDebit(LocalDate.of(2027, 3, 15), Money.fromRupees(2_250), DebitKind.CATCH_UP),
            ScheduledDebit(LocalDate.of(2027, 3, 25), Money(225_050), DebitKind.CATCH_UP),
        )

        val stored = converters.debitsToString(debits)

        assertEquals("2027-03-15:225000:CATCH_UP;2027-03-25:225050:CATCH_UP", stored)
        assertEquals(debits, converters.stringToDebits(stored))
        assertEquals(emptyList<ScheduledDebit>(), converters.stringToDebits(converters.debitsToString(emptyList())))
    }
}
