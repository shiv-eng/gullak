package com.gullak.core.ai

import com.gullak.core.ai.demo.DemoAiResponses
import com.gullak.core.ai.rules.RuleBasedGoalParser
import com.gullak.core.ai.understanding.GeminiGoalUnderstandingService
import com.gullak.core.domain.ai.GoalUnderstandingService
import com.gullak.core.domain.analytics.Analytics
import com.gullak.core.domain.analytics.AnalyticsEvent
import com.gullak.core.domain.repository.SettingsRepository
import com.gullak.core.model.ai.AiPath
import com.gullak.core.model.ai.ConversationTurn
import com.gullak.core.model.ai.ParsedGoal
import com.gullak.core.model.ai.Speaker
import com.gullak.core.model.result.AppError
import com.gullak.core.model.result.AppResult
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * The understanding chain, so the demo never stalls:
 * demo mode → saved answers (700 ms simulated latency); otherwise Gemini; on any Gemini failure, or a
 * transcript with no saved answer, the offline rule-based parser. It always returns a result, and records
 * which path answered.
 */
internal class FallbackGoalUnderstandingService @Inject constructor(
    private val settings: SettingsRepository,
    private val demo: DemoAiResponses,
    private val gemini: GeminiGoalUnderstandingService,
    private val rules: RuleBasedGoalParser,
    private val analytics: Analytics,
) : GoalUnderstandingService {

    override suspend fun understand(
        conversation: List<ConversationTurn>,
        today: LocalDate,
    ): AppResult<ParsedGoal> {
        val transcript = conversation.filter { it.speaker == Speaker.USER }.joinToString(", ") { it.text.trim() }

        if (settings.settings.first().demoMode) {
            val saved = demo.match(transcript)
            if (saved != null) {
                delay(DEMO_LATENCY_MS)
                return answered(AiPath.DEMO, saved)
            }
            return answered(AiPath.RULES, rules.parse(transcript, today))
        }

        return when (val result = gemini.understand(conversation, today)) {
            is AppResult.Success -> answered(AiPath.GEMINI, result.value)

            is AppResult.Failure -> {
                analytics.log(AnalyticsEvent.AiFallbackUsed(result.error.reason()))
                answered(AiPath.RULES, rules.parse(transcript, today))
            }
        }
    }

    private suspend fun answered(
        path: AiPath,
        goal: ParsedGoal,
    ): AppResult<ParsedGoal> {
        settings.setLastAiPath(path)
        analytics.log(AnalyticsEvent.AiPathUsed(path))
        return AppResult.Success(goal)
    }

    private companion object {
        const val DEMO_LATENCY_MS = 700L
    }
}

/** Short analytics label for why the model's answer was not used. */
internal fun AppError.reason(): String = when (this) {
    AppError.Network -> "network"
    AppError.Timeout -> "timeout"
    AppError.NotConfigured -> "not_configured"
    is AppError.Http -> "http_$code"
    is AppError.InvalidResponse -> "invalid_response"
    else -> "other"
}
