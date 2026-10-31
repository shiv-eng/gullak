package com.shivangi.gullak.core.domain.analytics

import com.shivangi.gullak.core.model.ai.AiPath
import com.shivangi.gullak.core.model.goal.DebitKind
import com.shivangi.gullak.core.model.goal.Destination
import com.shivangi.gullak.core.model.goal.Frequency

interface Analytics {
    fun log(event: AnalyticsEvent)
}

sealed class AnalyticsEvent(
    val name: String,
    val params: Map<String, String> = emptyMap(),
) {
    data class GoalCreated(val destination: Destination) :
        AnalyticsEvent("goal_created", mapOf("destination" to destination.name))

    data class PlanStarted(val frequency: Frequency) :
        AnalyticsEvent("plan_started", mapOf("frequency" to frequency.name))

    data class DebitSucceeded(val kind: DebitKind) :
        AnalyticsEvent("debit_succeeded", mapOf("kind" to kind.name))

    data class DebitFailed(val kind: DebitKind) : AnalyticsEvent("debit_failed", mapOf("kind" to kind.name))

    data class ReplanShown(val choices: Int) :
        AnalyticsEvent("replan_shown", mapOf("choices" to choices.toString()))

    data class ReplanApplied(val choice: String) : AnalyticsEvent("replan_applied", mapOf("choice" to choice))

    data object GoalReached : AnalyticsEvent("goal_reached")

    data object GoalPaidOut : AnalyticsEvent("goal_paid_out")

    data object TopUpAsked : AnalyticsEvent("top_up_asked")

    data object VoiceUsed : AnalyticsEvent("voice_used")

    data object FollowUpAsked : AnalyticsEvent("follow_up_asked")

    data class AiFallbackUsed(val reason: String) :
        AnalyticsEvent("ai_fallback_used", mapOf("reason" to reason))

    data class AiPathUsed(val path: AiPath) : AnalyticsEvent("ai_path", mapOf("path" to path.name.lowercase()))

    data class DestinationShown(val destination: Destination) :
        AnalyticsEvent("destination_shown", mapOf("destination" to destination.name))
}
