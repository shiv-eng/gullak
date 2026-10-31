package com.shivangi.gullak.core.domain.usecase

import com.shivangi.gullak.core.domain.analytics.Analytics
import com.shivangi.gullak.core.domain.analytics.AnalyticsEvent
import com.shivangi.gullak.core.domain.plan.PlanEngine
import com.shivangi.gullak.core.domain.plan.PlanInput
import com.shivangi.gullak.core.model.plan.Plan
import com.shivangi.gullak.core.model.result.AppResult
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
