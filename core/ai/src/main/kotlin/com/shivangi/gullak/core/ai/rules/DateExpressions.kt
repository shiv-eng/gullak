package com.shivangi.gullak.core.ai.rules

import java.time.LocalDate
import java.time.YearMonth

/** A date found in text and where it was, so the rest of the parser can ignore those words. */
internal data class DateMention(
    val date: LocalDate,
    val range: IntRange,
)

/**
 * Target dates in English, Hinglish and Devanagari, relative to [today]:
 * "6 mahine baad", "2 saal mein", "agle mahine", "agle saal", "agle December", "15 January",
 * "June 2027", "March", "15 tarikh", "15/03/2027". A month on its own is the last day of the next such month
 * after today.
 */
internal class DateExpressions(private val today: LocalDate) {

    /**
     * The first pattern that matches decides. If its date is impossible ("31 February"), there is no date,
     * so the user is asked rather than given a nearby date they did not say.
     */
    fun find(text: String): DateMention? {
        val lower = text.lowercase()
        for ((pattern, toDate) in matchers) {
            val match = pattern.find(lower) ?: continue
            return toDate(match)?.let { DateMention(it, match.range) }
        }
        return null
    }

    private val matchers: List<Pair<Regex, (MatchResult) -> LocalDate?>> = listOf(
        // "15/03/2027", "15-3-27", "15/03": day first, as written in India.
        regex("(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{4}|\\d{2}))?") to { m ->
            val (day, month, year) = m.destructured
            numericDate(day.toInt(), month.toInt(), year)
        },
        // "March ki 15" / "मार्च की 15"
        regex("($MONTH_NAMES)\\s+(?:ki|की)\\s+(\\d{1,2})(?:\\s*(?:$DATE_WORDS))?") to { m ->
            dayOfMonth(m.groupValues[2].toInt(), m.groupValues[1], "")
        },
        // "15 tarikh", "15 tarikh ko March"
        regex("(\\d{1,2})\\s*(?:$DATE_WORDS)(?:\\s+(?:ko\\s+|को\\s+)?($MONTH_NAMES))?") to { m ->
            val (day, month) = m.destructured
            if (month.isNotEmpty()) dayOfMonth(day.toInt(), month, "") else nextDayOfMonth(day.toInt())
        },
        regex("($COUNT)\\s*(?:$MONTH_UNITS)(?:\\s*(?:$AFTER))?") to { m ->
            count(m.groupValues[1])?.let { today.plusMonths(it) }
        },
        regex("($COUNT)\\s*(?:$YEAR_UNITS)(?:\\s*(?:$AFTER))?") to { m ->
            count(m.groupValues[1])?.let { today.plusYears(it) }
        },
        // "agle saal May" / "अगले साल मई": that month next calendar year.
        regex("(?:$NEXT)\\s+(?:$YEAR_WORDS)\\s+(?:ke\\s+|की\\s+)?($MONTH_NAMES)") to { m ->
            month(m.groupValues[1])?.let { YearMonth.of(today.year + 1, it).atEndOfMonth() }
        },
        // "agle December" / "next March": that month next calendar year (agreed rule).
        regex("(?:$NEXT)\\s+($MONTH_NAMES)") to { m ->
            month(m.groupValues[1])?.let { YearMonth.of(today.year + 1, it).atEndOfMonth() }
        },
        regex("(?:$NEXT)\\s+(?:$MONTH_WORDS)") to { _ -> YearMonth.from(today).plusMonths(1).atEndOfMonth() },
        regex("(?:$NEXT)\\s+(?:$YEAR_WORDS)") to { _ -> today.plusYears(1) },
        // "15 January", "15th Jan 2028"
        regex("(\\d{1,2})(?:st|nd|rd|th)?\\s+($MONTH_NAMES)(?:\\s*,?\\s*(\\d{4}))?") to { m ->
            dayOfMonth(m.groupValues[1].toInt(), m.groupValues[2], m.groupValues[3])
        },
        // "January 15", "June 2027", "March"
        regex("($MONTH_NAMES)(?:\\s+(\\d{4})|\\s+(\\d{1,2})(?:st|nd|rd|th)?)?") to { m ->
            val (name, year, day) = m.destructured
            when {
                day.isNotEmpty() -> dayOfMonth(day.toInt(), name, "")
                year.isNotEmpty() -> month(name)?.let { YearMonth.of(year.toInt(), it).atEndOfMonth() }
                else -> month(name)?.let(::nextMonthEnd)
            }
        },
    )

    private fun dayOfMonth(
        day: Int,
        monthName: String,
        year: String,
    ): LocalDate? {
        val month = month(monthName) ?: return null
        val ym = if (year.isNotEmpty()) YearMonth.of(year.toInt(), month) else YearMonth.of(today.year, month)
        if (!ym.isValidDay(day)) return null
        val date = ym.atDay(day)
        return if (year.isEmpty() && date <= today) date.plusYears(1) else date
    }

    private fun numericDate(
        day: Int,
        month: Int,
        year: String,
    ): LocalDate? {
        if (month !in 1..MONTHS_IN_YEAR) return null
        val fullYear = when (year.length) {
            0 -> today.year
            SHORT_YEAR_LENGTH -> CENTURY + year.toInt()
            else -> year.toInt()
        }
        val ym = YearMonth.of(fullYear, month)
        if (!ym.isValidDay(day)) return null
        val date = ym.atDay(day)
        return if (year.isEmpty() && date <= today) date.plusYears(1) else date
    }

    /** The next time this day of the month comes round after today. */
    private fun nextDayOfMonth(day: Int): LocalDate? = generateSequence(YearMonth.from(today)) { it.plusMonths(1) }
        .take(MONTHS_IN_YEAR)
        .filter { it.isValidDay(day) }
        .map { it.atDay(day) }
        .firstOrNull { it > today }

    /** Last day of the next such month after today. */
    private fun nextMonthEnd(month: Int): LocalDate {
        val thisYear = YearMonth.of(today.year, month).atEndOfMonth()
        return if (thisYear > today) thisYear else YearMonth.of(today.year + 1, month).atEndOfMonth()
    }

    private fun month(name: String): Int? = MONTHS[name.lowercase()]

    private fun count(word: String): Long? = word.toLongOrNull() ?: COUNT_WORDS[word.lowercase()]

    private companion object {
        const val B = """(?<![\p{L}\p{M}\d])"""
        const val E = """(?![\p{L}\p{M}\d])"""

        fun regex(body: String) = Regex(B + body + E, RegexOption.IGNORE_CASE)

        val MONTHS: Map<String, Int> = buildMap {
            val names = listOf(
                listOf("january", "jan", "janvari", "january", "जनवरी"),
                listOf("february", "feb", "farvari", "farwari", "फ़रवरी", "फरवरी"),
                listOf("march", "मार्च"),
                listOf("april", "apr", "aprail", "अप्रैल"),
                listOf("may", "मई"),
                listOf("june", "jun", "जून"),
                listOf("july", "jul", "julai", "जुलाई"),
                listOf("august", "aug", "agast", "अगस्त"),
                listOf("september", "sept", "sep", "sitambar", "सितंबर", "सितम्बर"),
                listOf("october", "oct", "aktubar", "aktoobar", "अक्टूबर", "अक्तूबर"),
                listOf("november", "nov", "navambar", "नवंबर", "नवम्बर"),
                listOf("december", "dec", "disambar", "दिसंबर", "दिसम्बर"),
            )
            names.forEachIndexed { index, aliases -> aliases.forEach { put(it, index + 1) } }
        }

        val COUNT_WORDS: Map<String, Long> = mapOf(
            "ek" to 1, "एक" to 1, "do" to 2, "दो" to 2, "teen" to 3, "तीन" to 3,
            "char" to 4, "chaar" to 4, "चार" to 4, "paanch" to 5, "panch" to 5, "पांच" to 5, "पाँच" to 5,
            "chhe" to 6, "chhah" to 6, "छह" to 6, "saat" to 7, "सात" to 7, "aath" to 8, "आठ" to 8,
            "nau" to 9, "नौ" to 9, "das" to 10, "दस" to 10, "gyarah" to 11, "barah" to 12, "बारह" to 12,
        )

        fun alt(words: Collection<String>) = words.sortedByDescending {
            it.length
        }.joinToString("|") { Regex.escape(it) }

        val MONTH_NAMES = alt(MONTHS.keys)
        val COUNT = "\\d{1,2}|" + alt(COUNT_WORDS.keys)
        const val MONTH_UNITS = "mahine|mahina|mahino|maheene|months?|महीने|महीना|महीनों"
        const val YEAR_UNITS = "saal|sal|years?|साल|वर्ष"
        const val MONTH_WORDS = "mahine|mahina|month|महीने"
        const val YEAR_WORDS = "saal|sal|year|साल"
        const val AFTER = "baad|mein|me|later|बाद|में"
        const val NEXT = "agle|agla|next|अगले|अगला"
        const val DATE_WORDS = "tarikh|tareekh|tarik|तारीख़|तारीख"
        const val MONTHS_IN_YEAR = 12
        const val SHORT_YEAR_LENGTH = 2
        const val CENTURY = 2000
    }
}
