package com.shivangi.gullak.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface GoalDao {
    /** Goals in the order they were created (SQLite rowid). */
    @Query("SELECT * FROM goals ORDER BY rowid")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id")
    fun observe(id: String): Flow<GoalEntity?>

    @Query("SELECT * FROM goals ORDER BY rowid")
    suspend fun getAll(): List<GoalEntity>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun get(id: String): GoalEntity?

    /** Upsert updates in place; REPLACE would delete the row and cascade-delete its debits. */
    @Upsert
    suspend fun upsert(goal: GoalEntity)

    @Query("DELETE FROM goals")
    suspend fun deleteAll()
}

@Dao
internal interface DebitDao {
    @Query("SELECT * FROM debits WHERE goal_id = :goalId ORDER BY date, rowid")
    fun observeForGoal(goalId: String): Flow<List<DebitEntity>>

    @Query("SELECT * FROM debits ORDER BY date, rowid")
    fun observeAll(): Flow<List<DebitEntity>>

    @Query("SELECT * FROM debits WHERE goal_id = :goalId ORDER BY date, rowid")
    suspend fun getForGoal(goalId: String): List<DebitEntity>

    @Query("SELECT * FROM debits WHERE id = :id")
    suspend fun get(id: String): DebitEntity?

    @Insert
    suspend fun insert(debit: DebitEntity)

    @Query("DELETE FROM debits")
    suspend fun deleteAll()
}
