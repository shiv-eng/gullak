package com.shivangi.gullak.core.ai.demo

import com.shivangi.gullak.core.model.ai.GoalLanguage
import com.shivangi.gullak.core.model.ai.GoalLanguage.ENGLISH
import com.shivangi.gullak.core.model.ai.GoalLanguage.HINDI
import com.shivangi.gullak.core.model.ai.GoalLanguage.HINGLISH
import com.shivangi.gullak.core.model.ai.ParsedGoal
import com.shivangi.gullak.core.model.money.Money
import java.time.LocalDate
import javax.inject.Inject

/**
 * Saved answers for the brief's 12 test sentences and the demo script, as Gemini would give them on
 * 31 Oct 2026 (the demo clock's start date). Matching tolerates punctuation, case and common Hinglish
 * spellings ("hazar", "shadi"), so a slightly different transcript still matches, but never one with a different
 * amount or date: a sentence that says other numbers or months gets a real reading instead.
 */
class DemoAiResponses @Inject constructor() {

    fun match(transcript: String): ParsedGoal? {
        val tokens = tokens(transcript)
        if (tokens.isEmpty()) return null
        return entries
            .map { it to similarity(tokens, it.tokens) }
            .filter { (entry, score) -> score >= MIN_SIMILARITY && facts(tokens) == facts(entry.tokens) }
            .maxByOrNull { (_, score) -> score }
            ?.first
            ?.goal
    }

    private class Entry(
        sentence: String,
        val goal: ParsedGoal,
    ) {
        val tokens: Set<String> = tokens(sentence)
    }

    private val entries = listOf(
        entry("Bhai ki shaadi, agle December tak, 50 hazaar", "Bhai ki shaadi", 50_000, "2027-12-31", HINGLISH),
        entry("Goa trip in March, 30,000", "Goa trip", 30_000, "2027-03-31", ENGLISH),
        entry("Beti ki school fees, April mein, 45 hazaar", "Beti ki school fees", 45_000, "2027-04-30", HINGLISH),
        entry(
            "Mujhe naya phone lena hai",
            "Naya phone",
            null,
            null,
            HINGLISH,
            followUp = "Phone ke liye kitne paise chahiye, aur kab tak?",
        ),
        entry("Laptop for my son, 36000, by June", "Laptop for my son", 36_000, "2027-06-30", ENGLISH),
        entry(
            "Diwali ke liye 20 hazaar",
            "Diwali",
            20_000,
            null,
            HINGLISH,
            dateHint = "Diwali",
        ),
        entry("6 mahine baad Goa jaana hai, 30 hazaar", "Goa", 30_000, "2027-04-30", HINGLISH),
        entry("Bike ke liye dedh lakh, 2 saal mein", "Bike", 1_50_000, "2028-10-31", HINGLISH),
        entry(
            "Emergency fund banana hai, 1 lakh",
            "Emergency fund",
            1_00_000,
            null,
            HINGLISH,
            followUp = "Emergency fund kab tak banana hai?",
        ),
        entry(
            "मुझे अगले साल मई तक 25000 बचाने हैं",
            null,
            25_000,
            "2027-05-31",
            HINDI,
            followUp = "आप किस चीज़ के लिए बचत करना चाहते हैं?",
        ),
        entry(
            "Kuch paise bachane hain",
            null,
            null,
            null,
            HINGLISH,
            followUp = "Kis cheez ke liye bachana hai?",
        ),
        entry("Maa ka birthday gift, 5000, 15 January", "Maa ka birthday gift", 5_000, "2027-01-15", HINGLISH),
        // Demo script (section 14) and the pitch's opening line.
        entry("Laptop ke liye 36 hazaar, June tak", "Laptop", 36_000, "2027-06-30", HINGLISH),
        entry(
            "Bhai ki shaadi ke liye kuch bachana hai",
            "Bhai ki shaadi",
            null,
            null,
            HINGLISH,
            followUp = "Kab hai, aur kitna dena chahti hain?",
        ),
    )

    @Suppress("LongParameterList")
    private fun entry(
        sentence: String,
        name: String?,
        rupees: Long?,
        date: String?,
        language: GoalLanguage,
        dateHint: String? = null,
        followUp: String? = null,
    ) = Entry(
        sentence,
        ParsedGoal(
            goalName = name,
            targetAmount = rupees?.let(Money::fromRupees),
            targetDate = date?.let(LocalDate::parse),
            dateHint = dateHint,
            language = language,
            followUp = followUp,
        ),
    )

    private companion object {
        const val MIN_SIMILARITY = 0.75

        /** Common transliteration variants mapped to one spelling. */
        val VARIANTS = mapOf(
            "hazar" to "hazaar", "hajar" to "hazaar", "hajaar" to "hazaar", "thousand" to "hazaar",
            "shadi" to "shaadi", "shaadee" to "shaadi", "lac" to "lakh", "laakh" to "lakh",
            "disambar" to "december", "mahina" to "mahine", "me" to "mein", "mai" to "mein",
            "jana" to "jaana", "bachani" to "bachana", "bachane" to "bachana",
            // hi-IN speech recognition writes Devanagari: map the demo script's words to their Hinglish form.
            "भाई" to "bhai", "की" to "ki", "शादी" to "shaadi", "अगले" to "agle", "दिसंबर" to "december",
            "दिसम्बर" to "december", "तक" to "tak", "हज़ार" to "hazaar", "हजार" to "hazaar", "लैपटॉप" to "laptop",
            "के" to "ke", "लिए" to "liye", "जून" to "june",
        )

        val FACT_WORDS = setOf(
            "january", "february", "march", "april", "may", "june", "july", "august", "september", "october",
            "november", "december", "hazaar", "lakh", "dedh", "mahine", "saal",
        )

        /** The words that change what the goal is: numbers, months and scale words. */
        fun facts(tokens: Set<String>): Set<String> =
            tokens.filter { word -> word in FACT_WORDS || word.any(Char::isDigit) }.toSet()

        fun tokens(text: String): Set<String> = text.lowercase()
            .replace(Regex("""(?<=\d),(?=\d)"""), "")
            .split(Regex("""[^\p{L}\p{M}\d]+"""))
            .filter { it.isNotEmpty() }
            .map { VARIANTS[it] ?: it }
            .toSet()

        fun similarity(
            a: Set<String>,
            b: Set<String>,
        ): Double = (a intersect b).size.toDouble() / (a union b).size
    }
}
