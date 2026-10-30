package com.gullak.core.model.settings

import com.gullak.core.model.ai.AiPath

enum class SpeechLanguage(val tag: String) {
    HINDI_INDIA("hi-IN"),
    ENGLISH_INDIA("en-IN"),
}

data class AppSettings(
    val demoMode: Boolean = true,
    val voiceReplies: Boolean = true,
    /** English (India) writes Hinglish in Latin script, which the goal reader handles best. */
    val speechLanguage: SpeechLanguage = SpeechLanguage.ENGLISH_INDIA,
    val lastAiPath: AiPath? = null,
)
