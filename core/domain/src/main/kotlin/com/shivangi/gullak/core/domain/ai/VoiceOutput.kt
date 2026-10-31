package com.shivangi.gullak.core.domain.ai

import com.shivangi.gullak.core.model.ai.GoalLanguage

/** Reads Gullak's replies aloud. */
interface VoiceOutput {
    /** Speaks [text], replacing anything being spoken, and returns when it has finished. */
    suspend fun speak(
        text: String,
        language: GoalLanguage,
    )

    fun stop()

    /** Whether the phone has a voice for [language]. */
    suspend fun isVoiceAvailable(language: GoalLanguage): Boolean
}
