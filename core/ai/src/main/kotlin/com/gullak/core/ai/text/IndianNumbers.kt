package com.gullak.core.ai.text

import java.math.BigDecimal

/** A number found in text, with its value after Indian scale words ("50 hazaar" = 50000). */
internal data class NumberMention(
    val value: BigDecimal,
    val range: IntRange,
    /** Followed by hazaar / lakh / crore, or a fraction word such as "dedh". */
    val hasScaleWord: Boolean,
    /** Preceded by ₹ or "Rs". */
    val hasRupeeSign: Boolean,
)

/**
 * Reads numbers the way Indian users write and say them: "₹1,50,000", "50,000", "1.5 lakh", "dedh lakh",
 * "sava 2 lakh", "50 हज़ार", Devanagari digits. Shared by [com.gullak.core.ai.guard.NumberGuard] and the
 * rule-based parser so both read amounts the same way.
 */
internal object IndianNumbers {

    private const val DEVANAGARI_ZERO = '०'

    private val SCALES: Map<String, BigDecimal> = buildMap {
        listOf("hazaar", "hazar", "hajar", "hajaar", "hazaar", "thousand", "हज़ार", "हजार")
            .forEach { put(it, BigDecimal(1_000)) }
        listOf("lakh", "lakhs", "lac", "lacs", "laakh", "लाख").forEach { put(it, BigDecimal(1_00_000)) }
        listOf("crore", "crores", "करोड़", "करोड").forEach { put(it, BigDecimal(1_00_00_000)) }
    }

    /** Words that stand for a number on their own, used only before a scale word ("dedh lakh"). */
    private val FRACTION_WORDS: Map<String, BigDecimal> = mapOf(
        "dedh" to BigDecimal("1.5"),
        "derh" to BigDecimal("1.5"),
        "डेढ़" to BigDecimal("1.5"),
        "डेढ" to BigDecimal("1.5"),
        "dhai" to BigDecimal("2.5"),
        "adhai" to BigDecimal("2.5"),
        "ढाई" to BigDecimal("2.5"),
    )

    /** Words that adjust the number after them: "sava 2" = 2.25, "saadhe 3" = 3.5, "paune 2" = 1.75. */
    private val MODIFIERS: Map<String, BigDecimal> = mapOf(
        "sava" to BigDecimal("0.25"),
        "sawa" to BigDecimal("0.25"),
        "सवा" to BigDecimal("0.25"),
        "saadhe" to BigDecimal("0.5"),
        "sadhe" to BigDecimal("0.5"),
        "saade" to BigDecimal("0.5"),
        "साढ़े" to BigDecimal("0.5"),
        "paune" to BigDecimal("-0.25"),
        "पौने" to BigDecimal("-0.25"),
    )

    private const val NOT_WORD_BEFORE = """(?<![\p{L}\p{M}\d])"""
    private const val NOT_WORD_AFTER = """(?![\p{L}\p{M}\d])"""

    private fun alternation(words: Collection<String>) =
        words.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

    private val NUMBER_PATTERN = Regex(
        NOT_WORD_BEFORE +
            "(?:(?<mod>${alternation(MODIFIERS.keys)})\\s+)?" +
            "(?:(?<num>\\d{1,3}(?:,\\d{2,3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?)|(?<frac>${alternation(
                FRACTION_WORDS.keys,
            )}))" +
            "(?:\\s*(?<scale>${alternation(SCALES.keys)})$NOT_WORD_AFTER)?",
        RegexOption.IGNORE_CASE,
    )

    private val RUPEE_BEFORE = Regex("""(₹|\brs\.?|\binr)\s*$""", RegexOption.IGNORE_CASE)

    /** Replaces Devanagari digits with ASCII ones (same length, so ranges stay valid). */
    fun normalizeDigits(text: String): String = buildString(text.length) {
        for (c in text) append(if (c in DEVANAGARI_ZERO..'९') '0' + (c - DEVANAGARI_ZERO) else c)
    }

    fun find(text: String): List<NumberMention> {
        val normalized = normalizeDigits(text)
        return NUMBER_PATTERN.findAll(normalized).mapNotNull { match ->
            val groups = match.groups
            val scale = groups["scale"]?.value?.lowercase()?.let { SCALES[it] }
            val fraction = groups["frac"]?.value?.lowercase()?.let { FRACTION_WORDS[it] }
            // A fraction word without a scale ("dedh saal") is not an amount.
            if (fraction != null && scale == null) return@mapNotNull null
            val base = fraction ?: groups["num"]?.value?.replace(",", "")?.let(::BigDecimal) ?: return@mapNotNull null
            val modifier = groups["mod"]?.value?.lowercase()?.let { MODIFIERS[it] } ?: BigDecimal.ZERO
            val value = (base + modifier) * (scale ?: BigDecimal.ONE)
            NumberMention(
                value = value.normalized(),
                range = match.range,
                hasScaleWord = scale != null || fraction != null,
                hasRupeeSign = RUPEE_BEFORE.containsMatchIn(normalized.substring(0, match.range.first)),
            )
        }.toList()
    }
}

/** One representation per value, so 4500, 4500.0 and 4.5 × 1000 compare equal: 4500 keeps scale 0, 2.5 keeps 1. */
private fun BigDecimal.normalized(): BigDecimal = stripTrailingZeros().let {
    if (it.scale() <
        0
    ) {
        it.setScale(0)
    } else {
        it
    }
}
