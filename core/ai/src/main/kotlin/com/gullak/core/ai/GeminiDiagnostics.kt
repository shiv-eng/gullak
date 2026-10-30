package com.gullak.core.ai

import com.gullak.core.ai.gemini.GeminiConfig
import com.gullak.core.ai.understanding.GeminiGoalUnderstandingService
import com.gullak.core.model.ai.ConversationTurn
import com.gullak.core.model.ai.Speaker
import com.gullak.core.model.result.AppResult
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Calls Gemini directly (no demo answers, no fallback) and reports the raw JSON and how it parsed.
 * Used by the debug-only check in `:app`; never in the user path.
 */
class GeminiDiagnostics @Inject internal constructor(
    private val gemini: GeminiGoalUnderstandingService,
    private val config: GeminiConfig,
    private val clock: Clock,
) {
    suspend fun check(sentence: String): String {
        if (!config.isConfigured) return "Gemini is not configured: set GEMINI_API_KEY and GEMINI_MODEL_ID."
        val today = LocalDate.now(clock)
        val conversation = listOf(ConversationTurn(Speaker.USER, sentence))
        return when (val raw = gemini.rawAnswer(conversation, today)) {
            is AppResult.Failure -> "model=${config.modelId} today=$today FAILED: ${raw.error}"

            is AppResult.Success ->
                "model=${config.modelId} today=$today\nraw=${raw.value}\n" +
                    "parsed=${gemini.parse(raw.value)}"
        }
    }
}
