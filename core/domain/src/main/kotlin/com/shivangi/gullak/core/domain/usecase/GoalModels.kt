package com.shivangi.gullak.core.domain.usecase

import com.shivangi.gullak.core.domain.plan.PlanEngine
import com.shivangi.gullak.core.model.ai.GoalLanguage
import com.shivangi.gullak.core.model.goal.Debit
import com.shivangi.gullak.core.model.goal.Frequency
import com.shivangi.gullak.core.model.goal.Goal
import com.shivangi.gullak.core.model.goal.ScheduledDebit
import com.shivangi.gullak.core.model.money.Money
import com.shivangi.gullak.core.model.plan.GoalStatus
import com.shivangi.gullak.core.model.plan.ReplanChoice
import java.time.LocalDate

/** What the user confirmed on the plan screen. [frequency] is null only for one-time or already funded goals. */
data class NewGoalRequest(
    val name: String,
    val target: Money,
    val targetDate: LocalDate,
    val dateIsFixed: Boolean,
    val frequency: Frequency?,
    val lumpSum: Money = Money.ZERO,
    val language: GoalLanguage = GoalLanguage.HINGLISH,
)

data class GoalSummary(
    val goal: Goal,
    val saved: Money,
    val status: GoalStatus,
    val nextDebit: ScheduledDebit?,
)

data class GoalsOverview(
    val goals: List<GoalSummary>,
    val savedInGold: Money,
    val savedInFund: Money,
) {
    val totalSaved: Money get() = savedInGold + savedInFund
}

/** A goal with its debit history, newest first. */
data class GoalDetail(
    val summary: GoalSummary,
    val debits: List<Debit>,
)

data class ReplanOffer(
    val goal: Goal,
    val failedDebit: Debit,
    val status: GoalStatus,
    val choices: List<ReplanChoice>,
)

internal fun PlanEngine.summarize(
    goal: Goal,
    debits: List<Debit>,
    today: LocalDate,
): GoalSummary = GoalSummary(
    goal = goal,
    saved = saved(debits),
    status = status(goal, debits, today),
    nextDebit = upcomingDebits(goal, debits).firstOrNull(),
)
