package com.gullak.core.domain.plan

import com.gullak.core.model.money.Money
import java.time.LocalDate

/**
 * Inputs to [PlanEngine.plan]. [startDate] defaults to the next 5th on or after tomorrow
 * (or tomorrow, if that 5th is after the target date).
 */
data class PlanInput(
    val target: Money,
    val targetDate: LocalDate,
    val startDate: LocalDate? = null,
    val alreadySaved: Money = Money.ZERO,
    val lumpSum: Money = Money.ZERO,
)
