package com.gullak.core.domain.ai

import com.gullak.core.model.ai.GoalLanguage

/** Reads Gullak's replies aloud. */
interface VoiceOutput {
    /** Speaks [text], replacing anything being spoken, and returns when it has finished. */
    suspend fun speak(
        text: String,
        language: GoalLanguage,
    )

    fun stop()
}
