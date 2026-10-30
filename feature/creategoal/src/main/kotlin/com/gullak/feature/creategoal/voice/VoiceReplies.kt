package com.gullak.feature.creategoal.voice

import com.gullak.core.model.goal.Frequency
import com.gullak.core.model.plan.PlanOption
import com.gullak.feature.creategoal.DemoBankAccount

/** The plan option a reply names: "mahine wala", "weekly", "pehla". Null if it names none on offer. */
internal fun optionFrom(
    text: String,
    options: List<PlanOption>,
): PlanOption? {
    val words = words(text)
    val named = FREQUENCY_WORDS.entries.firstOrNull { (_, names) -> words.any { it in names } }?.key
    named?.let { frequency -> return options.firstOrNull { it.frequency == frequency } }
    val position = POSITION_WORDS.entries.firstOrNull { (_, names) -> words.any { it in names } }?.key
    return when (position) {
        null -> null
        LAST -> options.lastOrNull()
        else -> options.getOrNull(position)
    }
}

/** The bank a reply names: "HDFC se", "state bank". */
internal fun bankFrom(text: String): DemoBankAccount? {
    val words = words(text)
    return when {
        words.any { it in SBI_WORDS } -> DemoBankAccount.SBI_SAVINGS
        words.any { it in HDFC_WORDS } -> DemoBankAccount.HDFC_SAVINGS
        words.any { it in ICICI_WORDS } -> DemoBankAccount.ICICI_SAVINGS
        else -> null
    }
}

/** "Sone mein daal doon?", "paisa kahan jayega?": a question about gold, a fund or where the money sits. */
internal fun asksWhereMoneyGoes(text: String): Boolean = words(text).any { it in MONEY_HOME_WORDS }

/** After a Gullak has started: does the user want another one? */
internal fun wantsAnother(text: String): Boolean = words(text).any { it in ANOTHER_WORDS }

private fun words(text: String): List<String> =
    text.lowercase().split(Regex("""[^\p{L}\p{M}\d]+""")).filter { it.isNotEmpty() }

private const val LAST = -1

private val MONEY_HOME_WORDS = setOf(
    "gold", "sona", "sone", "fund", "kahan", "kaha", "where", "सोना", "सोने", "गोल्ड", "फ़ंड", "फंड", "कहाँ", "कहां",
)

private val FREQUENCY_WORDS = mapOf(
    Frequency.DAILY to setOf("roz", "rozana", "daily", "day", "din", "रोज़", "रोज", "रोज़ाना", "रोजाना", "दिन", "डेली"),
    Frequency.WEEKLY to
        setOf("hafte", "hafta", "haftawar", "weekly", "week", "हफ़्ते", "हफ्ते", "हफ़्ता", "हफ्ता", "वीकली"),
    Frequency.MONTHLY to setOf("mahine", "mahina", "maheene", "monthly", "month", "महीने", "महीना", "मंथली"),
)

private val POSITION_WORDS = mapOf(
    0 to setOf("pehla", "pehle", "first", "पहला", "पहले"),
    1 to setOf("doosra", "dusra", "second", "दूसरा"),
    2 to setOf("teesra", "tisra", "third", "तीसरा"),
    LAST to setOf("aakhri", "akhri", "last", "आख़िरी", "आखिरी"),
)

private val SBI_WORDS = setOf("sbi", "state", "एसबीआई", "स्टेट")
private val HDFC_WORDS = setOf("hdfc", "एचडीएफसी")
private val ICICI_WORDS = setOf("icici", "आईसीआईसीआई")

private val ANOTHER_WORDS =
    setOf("aur", "another", "new", "naya", "nayi", "one", "ek", "और", "नया", "नई", "एक", "haan", "yes", "हाँ", "हां")
