package com.gullak.core.domain.usecase

import com.gullak.core.domain.analytics.Analytics
import com.gullak.core.domain.analytics.AnalyticsEvent
import com.gullak.core.domain.plan.PlanEngine
import com.gullak.core.domain.plan.PlanInput
import com.gullak.core.model.plan.Plan
import com.gullak.core.model.result.AppResult
import javax.inject.Inject

class BuildPlanUseCase @Inject constructor(
    private val engine: PlanEngine,
    private val analytics: Analytics,
) {
    operator fun invoke(input: PlanInput): AppResult<Plan> {
        val result = engine.plan(input)
        if (result is AppResult.Success) analytics.log(AnalyticsEvent.DestinationShown(result.value.destination))
        return result
    }
}
