package com.gullak.core.data.repository

import com.gullak.core.data.db.DebitDao
import com.gullak.core.data.db.GoalDao
import com.gullak.core.data.db.toEntity
import com.gullak.core.data.db.toModel
import com.gullak.core.domain.repository.DebitRepository
import com.gullak.core.domain.repository.GoalRepository
import com.gullak.core.model.goal.Debit
import com.gullak.core.model.goal.Goal
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room runs suspend queries and flows on its own executors, so no dispatcher switching is needed here. */
internal class RoomGoalRepository @Inject constructor(private val dao: GoalDao) : GoalRepository {
    override fun observeGoals(): Flow<List<Goal>> = dao.observeAll().map { goals -> goals.map { it.toModel() } }

    override fun observeGoal(id: String): Flow<Goal?> = dao.observe(id).map { it?.toModel() }

    override suspend fun getGoals(): List<Goal> = dao.getAll().map { it.toModel() }

    override suspend fun getGoal(id: String): Goal? = dao.get(id)?.toModel()

    override suspend fun upsert(goal: Goal) = dao.upsert(goal.toEntity())

    override suspend fun deleteAll() = dao.deleteAll()
}

internal class RoomDebitRepository @Inject constructor(private val dao: DebitDao) : DebitRepository {
    override fun observeDebits(goalId: String): Flow<List<Debit>> =
        dao.observeForGoal(goalId).map { debits -> debits.map { it.toModel() } }

    override fun observeAllDebits(): Flow<List<Debit>> = dao.observeAll().map { debits -> debits.map { it.toModel() } }

    override suspend fun getDebits(goalId: String): List<Debit> = dao.getForGoal(goalId).map { it.toModel() }

    override suspend fun getDebit(id: String): Debit? = dao.get(id)?.toModel()

    override suspend fun insert(debit: Debit) = dao.insert(debit.toEntity())

    override suspend fun deleteAll() = dao.deleteAll()
}
