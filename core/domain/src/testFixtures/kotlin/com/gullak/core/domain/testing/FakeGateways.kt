package com.gullak.core.domain.testing

import com.gullak.core.domain.ai.GoalUnderstandingService
import com.gullak.core.domain.ai.PlanExplanationService
import com.gullak.core.domain.analytics.Analytics
import com.gullak.core.domain.analytics.AnalyticsEvent
import com.gullak.core.domain.gateway.AutoPayGateway
import com.gullak.core.domain.gateway.DebitResult
import com.gullak.core.domain.gateway.DemoSeedSource
import com.gullak.core.domain.gateway.DestinationAccount
import com.gullak.core.domain.gateway.FestivalCalendar
import com.gullak.core.domain.gateway.IdGenerator
import com.gullak.core.domain.gateway.Mandate
import com.gullak.core.domain.gateway.SavingsDestinationGateway
import com.gullak.core.domain.usecase.NewGoalRequest
import com.gullak.core.model.ai.ConversationTurn
import com.gullak.core.model.ai.GoalLanguage
import com.gullak.core.model.ai.ParsedGoal
import com.gullak.core.model.goal.DebitStatus
import com.gullak.core.model.goal.Destination
import com.gullak.core.model.goal.Frequency
import com.gullak.core.model.money.Money
import com.gullak.core.model.plan.Plan
import com.gullak.core.model.plan.PlanOption
import com.gullak.core.model.plan.ReplanChoice
import com.gullak.core.model.result.AppError
import com.gullak.core.model.result.AppResult
import java.time.LocalDate

class FakeAutoPayGateway : AutoPayGateway {
    /** When set, the next `createMandate` call fails with this error. */
    var createMandateError: AppError? = null
    val mandates = mutableListOf<Mandate>()
    val debitCalls = mutableListOf<Pair<LocalDate, Boolean>>()

    override suspend fun createMandate(
        amount: Money,
        frequency: Frequency,
        startDate: LocalDate,
        payeeName: String,
    ): AppResult<Mandate> {
        createMandateError?.let {
            createMandateError = null
            return AppResult.Failure(it)
        }
        val mandate = Mandate("mandate-${mandates.size + 1}", amount, frequency, startDate)
        mandates += mandate
        return AppResult.Success(mandate)
    }

    override suspend fun debit(
        mandateId: String,
        amount: Money,
        date: LocalDate,
        forceFail: Boolean,
    ): AppResult<DebitResult> {
        debitCalls += date to forceFail
        val status = if (forceFail) DebitStatus.FAILED else DebitStatus.SUCCEEDED
        val reason = if (forceFail) "Low balance" else null
        return AppResult.Success(DebitResult("txn-${debitCalls.size}", status, reason))
    }
}

class FakeSavingsDestinationGateway : SavingsDestinationGateway {
    var error: AppError? = null
    val opened = mutableListOf<Pair<Destination, String>>()

    override suspend fun open(
        destination: Destination,
        goalId: String,
    ): AppResult<DestinationAccount> {
        error?.let { return AppResult.Failure(it) }
        opened += destination to goalId
        return AppResult.Success(DestinationAccount("account-$goalId", destination, destination.name))
    }
}

class SequentialIdGenerator(private val prefix: String = "id") : IdGenerator {
    private var next = 1

    override fun newId(): String = "$prefix-${next++}"
}

class RecordingAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()

    override fun log(event: AnalyticsEvent) {
        events += event
    }
}

class FakeFestivalCalendar(private val dates: Map<String, LocalDate> = emptyMap()) : FestivalCalendar {
    override suspend fun nextOccurrence(
        festival: String,
        after: LocalDate,
    ): LocalDate? = dates.entries.firstOrNull { it.key.equals(festival, ignoreCase = true) }?.value
}

/** Returns [result] and records every conversation it was asked about. */
class FakeGoalUnderstandingService(var result: AppResult<ParsedGoal> = AppResult.Failure(AppError.Network)) :
    GoalUnderstandingService {
    val requests = mutableListOf<List<ConversationTurn>>()

    override suspend fun understand(
        conversation: List<ConversationTurn>,
        today: LocalDate,
    ): AppResult<ParsedGoal> {
        requests += conversation
        return result
    }
}

class FakePlanExplanationService(var reply: String = "Explanation") : PlanExplanationService {
    override suspend fun explain(
        goalName: String,
        plan: Plan,
        chosen: PlanOption,
        language: GoalLanguage,
    ): String = reply

    override suspend fun explainReplan(
        goalName: String,
        choice: ReplanChoice,
        missed: Money,
        language: GoalLanguage,
    ): String = reply
}

class FakeDemoSeedSource(private val requests: List<NewGoalRequest>) : DemoSeedSource {
    override fun seedGoals(): List<NewGoalRequest> = requests
}

class FakeAiInfo(
    override val modelId: String = "gemini-test",
    override val isLiveModelConfigured: Boolean = true,
) : com.gullak.core.domain.ai.AiInfo
