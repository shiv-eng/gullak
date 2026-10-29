package com.gullak.core.domain.repository

import com.gullak.core.model.ai.AiPath
import com.gullak.core.model.goal.Debit
import com.gullak.core.model.goal.Goal
import com.gullak.core.model.settings.AppSettings
import com.gullak.core.model.settings.SpeechLanguage
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    fun observeGoals(): Flow<List<Goal>>

    fun observeGoal(id: String): Flow<Goal?>

    suspend fun getGoals(): List<Goal>

    suspend fun getGoal(id: String): Goal?

    /** Inserts or replaces the goal with the same id. */
    suspend fun upsert(goal: Goal)

    suspend fun deleteAll()
}

interface DebitRepository {
    fun observeDebits(goalId: String): Flow<List<Debit>>

    fun observeAllDebits(): Flow<List<Debit>>

    suspend fun getDebits(goalId: String): List<Debit>

    suspend fun getDebit(id: String): Debit?

    suspend fun insert(debit: Debit)

    suspend fun deleteAll()
}

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setDemoMode(enabled: Boolean)

    suspend fun setVoiceReplies(enabled: Boolean)

    suspend fun setSpeechLanguage(language: SpeechLanguage)

    suspend fun setLastAiPath(path: AiPath)
}
