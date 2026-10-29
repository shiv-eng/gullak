package com.gullak.core.data.db

import androidx.room.TypeConverter
import com.gullak.core.model.goal.DebitKind
import com.gullak.core.model.goal.ScheduledDebit
import com.gullak.core.model.money.Money
import java.time.LocalDate

/**
 * Dates are stored as ISO strings (sortable, readable in the database inspector). The two small
 * collections on a schedule are stored as delimited text: `2027-03-15:225000:CATCH_UP;…`.
 */
internal class Converters {
    @TypeConverter
    fun dateToString(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun datesToString(dates: Set<LocalDate>?): String? = dates?.sorted()?.joinToString(LIST_SEPARATOR)

    @TypeConverter
    fun stringToDates(value: String?): Set<LocalDate>? =
        value?.split(LIST_SEPARATOR)?.filter { it.isNotEmpty() }?.map(LocalDate::parse)?.toSet()

    @TypeConverter
    fun debitsToString(debits: List<ScheduledDebit>?): String? = debits?.joinToString(LIST_SEPARATOR) {
        "${it.date}$FIELD_SEPARATOR${it.amount.paise}$FIELD_SEPARATOR${it.kind}"
    }

    @TypeConverter
    fun stringToDebits(value: String?): List<ScheduledDebit>? = value
        ?.split(LIST_SEPARATOR)
        ?.filter { it.isNotEmpty() }
        ?.map { entry ->
            val (date, paise, kind) = entry.split(FIELD_SEPARATOR)
            ScheduledDebit(LocalDate.parse(date), Money(paise.toLong()), DebitKind.valueOf(kind))
        }

    private companion object {
        const val LIST_SEPARATOR = ";"
        const val FIELD_SEPARATOR = ":"
    }
}
