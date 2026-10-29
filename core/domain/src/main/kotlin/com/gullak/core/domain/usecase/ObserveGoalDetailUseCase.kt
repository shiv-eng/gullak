package com.gullak.core.domain.usecase

import com.gullak.core.domain.gateway.DemoClockController
import com.gullak.core.domain.plan.PlanEngine
import com.gullak.core.domain.repository.DebitRepository
import com.gullak.core.domain.repository.GoalRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** One goal with its debits, status and next debit; null once the goal no longer exists. */
class ObserveGoalDetailUseCase @Inject constructor(
    private val goals: GoalRepository,
    private val debits: DebitRepository,
    private val clock: DemoClockController,
    private val engine: PlanEngine,
) {
    operator fun invoke(goalId: String): Flow<GoalDetail?> = combine(
        goals.observeGoal(goalId),
        debits.observeDebits(goalId),
        clock.today,
    ) { goal, goalDebits, today ->
        goal?.let {
            GoalDetail(
                summary = engine.summarize(it, goalDebits, today),
                debits = goalDebits.sortedByDescending { d -> d.date },
            )
        }
    }
}
