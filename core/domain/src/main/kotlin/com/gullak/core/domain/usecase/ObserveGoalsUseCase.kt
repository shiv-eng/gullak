package com.gullak.core.domain.usecase

import com.gullak.core.domain.gateway.DemoClockController
import com.gullak.core.domain.plan.PlanEngine
import com.gullak.core.domain.repository.DebitRepository
import com.gullak.core.domain.repository.GoalRepository
import com.gullak.core.model.goal.Destination
import com.gullak.core.model.money.sum
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Every goal with its status, recalculated whenever goals, debits or the demo date change. */
class ObserveGoalsUseCase @Inject constructor(
    private val goals: GoalRepository,
    private val debits: DebitRepository,
    private val clock: DemoClockController,
    private val engine: PlanEngine,
) {
    operator fun invoke(): Flow<GoalsOverview> = combine(
        goals.observeGoals(),
        debits.observeAllDebits(),
        clock.today,
    ) { allGoals, allDebits, today ->
        val debitsByGoal = allDebits.groupBy { it.goalId }
        val summaries = allGoals.map { engine.summarize(it, debitsByGoal[it.id].orEmpty(), today) }
        // Money already paid out has left Gullak.
        val held = summaries.filterNot { engine.isPaidOut(debitsByGoal[it.goal.id].orEmpty()) }
        GoalsOverview(
            goals = summaries,
            savedInGold = held.filter { it.goal.destination == Destination.PAYTM_GOLD }.map { it.saved }.sum(),
            savedInFund = held
                .filter { it.goal.destination == Destination.PAYTM_MONEY_FUND }
                .map { it.saved }
                .sum(),
        )
    }
}
