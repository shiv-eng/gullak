package com.shivangi.gullak.feature.creategoal

/** Autocomplete for the goal name: matches that start with the text first; the common goals when nothing is typed. */
internal fun goalSuggestions(
    typed: String,
    all: List<String>,
): List<String> {
    val text = typed.trim()
    if (text.isEmpty()) return all.take(MAX_SUGGESTIONS)
    return all
        .filter { it.contains(text, ignoreCase = true) && !it.equals(text, ignoreCase = true) }
        .sortedBy { !it.startsWith(text, ignoreCase = true) }
        .take(MAX_SUGGESTIONS)
}

private const val MAX_SUGGESTIONS = 16
