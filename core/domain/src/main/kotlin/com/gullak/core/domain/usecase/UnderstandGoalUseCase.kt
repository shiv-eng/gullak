package com.gullak.core.domain.usecase

import com.gullak.core.domain.ai.FestivalMatch
import com.gullak.core.domain.ai.FollowUpQuestion
import com.gullak.core.domain.ai.FollowUpReason
import com.gullak.core.domain.ai.GoalUnderstandingService
import com.gullak.core.domain.ai.Understanding
import com.gullak.core.domain.analytics.Analytics
import com.gullak.core.domain.analytics.AnalyticsEvent
import com.gullak.core.domain.gateway.FestivalCalendar
import com.gullak.core.model.ai.ConversationTurn
import com.gullak.core.model.ai.ParsedGoal
import com.gullak.core.model.money.Money
import com.gullak.core.model.result.AppResult
import com.gullak.core.model.result.getOrElse
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Asks the understanding service to read the conversation, then validates in code: the amount must be
 * ₹500 to ₹50,00,000 and the date after today and within 10 years. A rejected value becomes a follow-up
 * question rather than reaching the plan.
 */
class UnderstandGoalUseCase @Inject constructor(
    private val service: GoalUnderstandingService,
    private val festivals: FestivalCalendar,
    private val analytics: Analytics,
    private val clock: Clock,
) {
    suspend operator fun invoke(conversation: List<ConversationTurn>): AppResult<Understanding> {
        val today = LocalDate.now(clock)
        val parsed = service.understand(conversation, today).getOrElse { return it }
        val understanding = interpret(parsed, today)
        if (understanding is Understanding.NeedsFollowUp) analytics.log(AnalyticsEvent.FollowUpAsked)
        return AppResult.Success(understanding)
    }

    /** The same checks, for a goal already read from the conversation. */
    suspend fun check(parsed: ParsedGoal): Understanding {
        val understanding = interpret(parsed, LocalDate.now(clock))
        if (understanding is Understanding.NeedsFollowUp) analytics.log(AnalyticsEvent.FollowUpAsked)
        return understanding
    }

    private suspend fun interpret(
        parsed: ParsedGoal,
        today: LocalDate,
    ): Understanding {
        val amountProblem = parsed.targetAmount
            ?.takeUnless { it in MIN_AMOUNT..MAX_AMOUNT }
            ?.let { FollowUpReason.AMOUNT_OUT_OF_RANGE }
        val dateProblem = parsed.targetDate?.let { dateProblem(it, today) }
        val amount = parsed.targetAmount.takeIf { amountProblem == null }
        val date = parsed.targetDate.takeIf { dateProblem == null }
        val name = parsed.goalName?.trim()?.takeIf { it.isNotEmpty() }
        val festival = if (date == null && dateProblem == null) lookUpFestival(parsed.dateHint, today) else null
        val cleaned = parsed.copy(goalName = name, targetAmount = amount, targetDate = date)
        val confirmedDate = date ?: festival?.date

        val problem = amountProblem ?: dateProblem
        return when {
            problem != null -> Understanding.NeedsFollowUp(cleaned, FollowUpQuestion.Standard(problem), festival)

            name != null && amount != null && confirmedDate != null -> Understanding.ReadyToConfirm(
                parsed = cleaned,
                goalName = name,
                target = amount,
                targetDate = confirmedDate,
                language = parsed.language,
                festival = festival,
            )

            else -> Understanding.NeedsFollowUp(cleaned, missingQuestion(parsed, name, amount), festival)
        }
    }

    private fun dateProblem(
        date: LocalDate,
        today: LocalDate,
    ): FollowUpReason? = when {
        date <= today -> FollowUpReason.DATE_NOT_IN_FUTURE
        date > today.plusYears(MAX_YEARS_AHEAD) -> FollowUpReason.DATE_TOO_FAR
        else -> null
    }

    private suspend fun lookUpFestival(
        hint: String?,
        today: LocalDate,
    ): FestivalMatch? {
        val festival = hint?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return festivals.nextOccurrence(festival, today)?.let { FestivalMatch(festival, it) }
    }

    /** Prefer the model's own question (in the user's language); otherwise ask for the first missing field. */
    private fun missingQuestion(
        parsed: ParsedGoal,
        name: String?,
        amount: Money?,
    ): FollowUpQuestion {
        val modelQuestion = parsed.followUp?.trim()?.takeIf { it.isNotEmpty() }
        if (modelQuestion != null) return FollowUpQuestion.FromModel(modelQuestion)
        val reason = when {
            name == null -> FollowUpReason.MISSING_NAME
            amount == null -> FollowUpReason.MISSING_AMOUNT
            else -> FollowUpReason.MISSING_DATE
        }
        return FollowUpQuestion.Standard(reason)
    }

    private companion object {
        val MIN_AMOUNT = Money.fromRupees(500)
        val MAX_AMOUNT = Money.fromRupees(50_00_000)
        const val MAX_YEARS_AHEAD = 10L
    }
}
