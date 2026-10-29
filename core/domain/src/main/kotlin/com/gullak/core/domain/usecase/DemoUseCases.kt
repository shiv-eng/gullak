package com.gullak.core.domain.usecase

import com.gullak.core.domain.gateway.DemoClockController
import com.gullak.core.domain.gateway.DemoSeedSource
import com.gullak.core.domain.plan.PlanEngine
import com.gullak.core.domain.repository.DebitRepository
import com.gullak.core.domain.repository.GoalRepository
import com.gullak.core.model.result.AppError
import com.gullak.core.model.result.AppResult
import com.gullak.core.model.result.getOrElse
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/**
 * Demo control: moves the clock to the last day of [toMonth] and posts every goal's due debits as
 * SUCCEEDED along the way.
 */
class FastForwardUseCase @Inject constructor(
    private val engine: PlanEngine,
    private val clock: DemoClockController,
    private val poster: DebitPoster,
) {
    suspend operator fun invoke(toMonth: YearMonth): AppResult<LocalDate> {
        val date = toMonth.atEndOfMonth()
        if (date <= engine.today()) return AppResult.Failure(AppError.DateNotInFuture)
        poster.postAllDue(upTo = date).getOrElse { return it }
        clock.setDate(date)
        return AppResult.Success(date)
    }
}

/** Demo control: clears every goal and debit, resets the clock and, if [seed], creates Sunita's goals. */
class ResetDemoDataUseCase @Inject constructor(
    private val goals: GoalRepository,
    private val debits: DebitRepository,
    private val clock: DemoClockController,
    private val seedSource: DemoSeedSource,
    private val createGoal: CreateGoalUseCase,
) {
    suspend operator fun invoke(seed: Boolean): AppResult<Unit> {
        debits.deleteAll()
        goals.deleteAll()
        clock.reset()
        if (seed) {
            for (request in seedSource.seedGoals()) {
                createGoal(request).getOrElse { return it }
            }
        }
        return AppResult.Success(Unit)
    }
}
