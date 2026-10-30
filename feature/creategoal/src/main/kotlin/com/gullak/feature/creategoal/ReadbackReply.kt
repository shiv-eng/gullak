package com.gullak.feature.creategoal

/** What the user said after Gullak read a goal back to them. */
internal enum class ReadbackReply { YES, NO, CORRECTION }

/** Plain yes or no only; anything else ("nahi, 20 hazaar") is read as a correction of the goal. */
internal fun readbackReply(text: String): ReadbackReply {
    val words = text.lowercase().split(Regex("""[^\p{L}\p{M}]+""")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> ReadbackReply.CORRECTION
        words.all { it in YES_WORDS } -> ReadbackReply.YES
        words.all { it in NO_WORDS } -> ReadbackReply.NO
        else -> ReadbackReply.CORRECTION
    }
}

private val YES_WORDS = setOf(
    "yes", "yeah", "yep", "ok", "okay", "right", "correct", "perfect", "confirm", "done", "is",
    "that", "it", "haan", "han", "ha", "haa", "ji", "theek", "thik", "sahi", "bilkul", "hai", "h",
    "हाँ", "हां", "जी", "ठीक", "सही", "बिल्कुल", "है",
    "karo", "kar", "kardo", "do", "shuru", "start", "chalo", "zaroor", "please", "go", "ahead", "na",
    "करो", "कर", "दो", "शुरू", "चलो", "ज़रूर", "जरूर",
)

private val NO_WORDS = setOf(
    "no", "nope", "not", "wrong", "incorrect", "nahi", "nahin", "galat", "hai", "h", "नहीं", "नही", "ग़लत", "गलत", "है",
)
