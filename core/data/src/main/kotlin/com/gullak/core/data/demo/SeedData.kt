package com.gullak.core.data.demo

import com.gullak.core.domain.gateway.DemoSeedSource
import com.gullak.core.domain.usecase.NewGoalRequest
import com.gullak.core.model.ai.GoalLanguage
import com.gullak.core.model.goal.Frequency
import com.gullak.core.model.money.Money
import java.time.LocalDate
import javax.inject.Inject

/**
 * Sunita's two goals (illustrative user). They go through `CreateGoalUseCase`, so the plan engine sets the
 * amounts: ₹3,600 a month to Paytm Gold for the wedding and ₹4,500 a month to Paytm Money for the laptop.
 */
internal class SeedData @Inject constructor() : DemoSeedSource {
    override fun seedGoals(): List<NewGoalRequest> = listOf(
        NewGoalRequest(
            name = "Bhai ki shaadi",
            target = WEDDING_TARGET,
            targetDate = WEDDING_DATE,
            dateIsFixed = false,
            frequency = Frequency.MONTHLY,
            language = GoalLanguage.HINGLISH,
        ),
        NewGoalRequest(
            name = "Laptop",
            target = LAPTOP_TARGET,
            targetDate = LAPTOP_DATE,
            dateIsFixed = true,
            frequency = Frequency.MONTHLY,
            language = GoalLanguage.HINGLISH,
        ),
    )

    private companion object {
        val WEDDING_TARGET = Money.fromRupees(50_000)
        val WEDDING_DATE: LocalDate = LocalDate.parse("2027-12-31")
        val LAPTOP_TARGET = Money.fromRupees(36_000)
        val LAPTOP_DATE: LocalDate = LocalDate.parse("2027-06-30")
    }
}
