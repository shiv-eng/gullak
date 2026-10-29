package com.gullak.core.data.db

import com.gullak.core.model.goal.Debit
import com.gullak.core.model.goal.Goal
import com.gullak.core.model.goal.SavingsSchedule
import com.gullak.core.model.money.Money

internal fun GoalEntity.toModel() = Goal(
    id = id,
    name = name,
    target = Money(targetPaise),
    targetDate = targetDate,
    dateIsFixed = dateIsFixed,
    createdOn = createdOn,
    destination = destination,
    schedule = schedule?.toModel(),
    languageTag = languageTag,
)

internal fun Goal.toEntity() = GoalEntity(
    id = id,
    name = name,
    targetPaise = target.paise,
    targetDate = targetDate,
    dateIsFixed = dateIsFixed,
    createdOn = createdOn,
    destination = destination,
    languageTag = languageTag,
    schedule = schedule?.toColumns(),
)

private fun ScheduleColumns.toModel() = SavingsSchedule(
    frequency = frequency,
    baseAmountPerDebit = Money(baseAmountPaise),
    amountPerDebit = Money(amountPaise),
    startDate = startDate,
    endDate = endDate,
    mandateId = mandateId,
    extraDebits = extraDebits,
    excusedDates = excusedDates,
)

private fun SavingsSchedule.toColumns() = ScheduleColumns(
    frequency = frequency,
    baseAmountPaise = baseAmountPerDebit.paise,
    amountPaise = amountPerDebit.paise,
    startDate = startDate,
    endDate = endDate,
    mandateId = mandateId,
    extraDebits = extraDebits,
    excusedDates = excusedDates,
)

internal fun DebitEntity.toModel() = Debit(
    id = id,
    goalId = goalId,
    date = date,
    amount = Money(amountPaise),
    status = status,
    kind = kind,
)

internal fun Debit.toEntity() = DebitEntity(
    id = id,
    goalId = goalId,
    date = date,
    amountPaise = amount.paise,
    status = status,
    kind = kind,
)
