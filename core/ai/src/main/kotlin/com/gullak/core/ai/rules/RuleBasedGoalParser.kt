package com.gullak.core.ai.rules

import com.gullak.core.ai.text.IndianNumbers
import com.gullak.core.model.ai.GoalLanguage
import com.gullak.core.model.ai.ParsedGoal
import com.gullak.core.model.money.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject

/**
 * The offline fallback: reads name, amount, date, festival and language with plain rules. Anything it cannot
 * read stays null, and `UnderstandGoalUseCase` turns that into a follow-up question.
 */
class RuleBasedGoalParser @Inject constructor() {

    fun parse(
        text: String,
        today: LocalDate,
    ): ParsedGoal {
        val date = DateExpressions(today).find(text)
        val amount = findAmount(text, date?.range)
        val festival = findFestival(text)
        val name = findName(text, listOfNotNull(date?.range, amount?.second))
        return ParsedGoal(
            goalName = name,
            targetAmount = amount?.first,
            targetDate = date?.date,
            dateHint = festival.takeIf { date == null },
            language = detectLanguage(text),
            followUp = null,
        )
    }

    /**
     * A conversation, one reply at a time: the first name stands, and a later amount or date replaces an earlier one,
     * so "nahi, 20 hazaar" corrects the amount without touching the rest.
     */
    fun parse(
        replies: List<String>,
        today: LocalDate,
    ): ParsedGoal {
        val parsed = replies.map { parse(it, today) }
        val date = parsed.asReversed().firstNotNullOfOrNull { it.targetDate }
        return ParsedGoal(
            goalName = parsed.firstNotNullOfOrNull { it.goalName },
            targetAmount = parsed.asReversed().firstNotNullOfOrNull { it.targetAmount },
            targetDate = date,
            dateHint = parsed.asReversed().firstNotNullOfOrNull { it.dateHint }.takeIf { date == null },
            language = detectLanguage(replies.joinToString(" ")),
            followUp = null,
        )
    }

    /** The largest number that reads as money: has ₹ or a scale word, or is at least ₹100. */
    private fun findAmount(
        text: String,
        dateRange: IntRange?,
    ): Pair<Money, IntRange>? = IndianNumbers.find(text)
        .filter { dateRange == null || (it.range.last < dateRange.first || it.range.first > dateRange.last) }
        .filter { it.hasScaleWord || it.hasRupeeSign || it.value >= MIN_PLAIN_AMOUNT }
        .maxByOrNull { it.value }
        ?.let { Money.fromRupees(it.value.setScale(0, RoundingMode.HALF_UP).toLong()) to it.range }

    private fun findFestival(text: String): String? {
        val lower = text.lowercase()
        return FESTIVALS.entries.firstOrNull { (_, aliases) ->
            aliases.any { Regex("$B${Regex.escape(it)}$E").containsMatchIn(lower) }
        }?.key
    }

    /**
     * The first comma-separated part that still has words once the date and amount are removed, trimmed of
     * filler at both ends: "Diwali ke liye 20 hazaar" → "Diwali", "Mujhe naya phone lena hai" → "naya phone".
     */
    private fun findName(
        text: String,
        consumed: List<IntRange>,
    ): String? {
        val masked = StringBuilder(text).apply {
            consumed.forEach { range -> for (i in range) setCharAt(i, ',') }
        }.toString()
        return masked.split(',', '.', '!', '?', '।')
            .asSequence()
            .map { trimFiller(it.trim().split(Regex("\\s+")).filter { w -> w.isNotEmpty() }) }
            .firstOrNull { it.isNotEmpty() }
            ?.joinToString(" ")
            ?.replaceFirstChar { it.uppercase() }
    }

    private fun trimFiller(words: List<String>): List<String> {
        var start = 0
        var end = words.size
        while (start < end && isFiller(words[start], LEADING_FILLER)) start++
        while (end > start && isFiller(words[end - 1], TRAILING_FILLER)) end--
        val kept = words.subList(start, end)
        return if (kept.all { isFiller(it, LEADING_FILLER + TRAILING_FILLER) }) emptyList() else kept
    }

    private fun isFiller(
        word: String,
        filler: Set<String>,
    ): Boolean = word.lowercase().trim('₹', '.', ':').let { it.isEmpty() || it in filler }

    private fun detectLanguage(text: String): GoalLanguage {
        if (text.any { it in DEVANAGARI }) return GoalLanguage.HINDI
        val words = text.lowercase().split(Regex("[^\\p{L}]+")).toSet()
        return if (words.any { it in HINGLISH_MARKERS }) GoalLanguage.HINGLISH else GoalLanguage.ENGLISH
    }

    private companion object {
        const val B = """(?<![\p{L}\p{M}\d])"""
        const val E = """(?![\p{L}\p{M}\d])"""
        val MIN_PLAIN_AMOUNT = BigDecimal(100)
        val DEVANAGARI = 'ऀ'..'ॿ'

        val FESTIVALS: Map<String, List<String>> = mapOf(
            "Diwali" to listOf("diwali", "deepavali", "divali", "दिवाली", "दीवाली", "दीपावली"),
            "Holi" to listOf("holi", "होली"),
            "Dussehra" to listOf("dussehra", "dussera", "dasara", "dashehra", "दशहरा"),
            "Eid" to listOf("eid", "id-ul-fitr", "ईद"),
            "Christmas" to listOf("christmas", "xmas", "क्रिसमस"),
            "Raksha Bandhan" to listOf("rakhi", "raksha bandhan", "rakshabandhan", "राखी", "रक्षाबंधन"),
        )

        val LEADING_FILLER = setOf(
            "mujhe", "mujhko", "humein", "hame", "i", "want", "to", "need", "my", "for", "a", "an", "the",
            "मुझे", "हमें", "kuch", "kuchh", "paise", "paisa", "money", "save", "बचाने", "कुछ", "पैसे",
            "rs", "rupee", "rupees", "rupaye", "rupay", "rupiya", "रुपये", "रुपए", "of", "on",
        )

        val TRAILING_FILLER = setOf(
            "ke", "liye", "ki", "ka", "tak", "by", "in", "on", "for", "mein", "me", "hai", "hain", "h",
            "chahiye", "lena", "leni", "lene", "jaana", "jana", "karna", "banana", "bachana", "bachane",
            "save", "saving", "paise", "paisa", "rupee", "rupees", "rupaye", "rupay", "rupiya", "rs", "baad", "kuch",
            "dene", "dena", "deni", "ko", "and", "aur", "देने", "देना", "को", "रुपए", "और",
            "के", "लिए", "की", "का", "तक", "में", "है", "हैं", "बचाने", "बचाना", "लेना", "पैसे", "रुपये",
        )

        val HINGLISH_MARKERS = setOf(
            "hai", "hain", "ki", "ke", "ka", "tak", "mein", "liye", "mujhe", "agle", "agla", "hazaar", "hazar",
            "lakh", "saal", "mahine", "baad", "bhai", "beti", "beta", "shaadi", "bachana", "bachane", "lena",
            "jaana", "paise", "kuch", "dedh", "dhai", "naya", "nayi", "maa", "papa",
        )
    }
}
