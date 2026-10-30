package com.gullak.core.domain.testing

import com.gullak.core.domain.repository.DebitRepository
import com.gullak.core.domain.repository.GoalRepository
import com.gullak.core.domain.repository.SettingsRepository
import com.gullak.core.model.ai.AiPath
import com.gullak.core.model.goal.Debit
import com.gullak.core.model.goal.Goal
import com.gullak.core.model.settings.AppSettings
import com.gullak.core.model.settings.SpeechLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory goals, in insertion order. */
class FakeGoalRepository : GoalRepository {
    private val state = MutableStateFlow<Map<String, Goal>>(emptyMap())

    val goals: List<Goal> get() = state.value.values.toList()

    override fun observeGoals(): Flow<List<Goal>> = state.map { it.values.toList() }

    override fun observeGoal(id: String): Flow<Goal?> = state.map { it[id] }

    override suspend fun getGoals(): List<Goal> = goals

    override suspend fun getGoal(id: String): Goal? = state.value[id]

    override suspend fun upsert(goal: Goal) = state.update { it + (goal.id to goal) }

    override suspend fun deleteAll() {
        state.value = emptyMap()
    }
}

class FakeDebitRepository : DebitRepository {
    private val state = MutableStateFlow<List<Debit>>(emptyList())

    val debits: List<Debit> get() = state.value

    override fun observeDebits(goalId: String): Flow<List<Debit>> = state.map { all ->
        all.filter { it.goalId == goalId }
    }

    override fun observeAllDebits(): Flow<List<Debit>> = state

    override suspend fun getDebits(goalId: String): List<Debit> = state.value.filter { it.goalId == goalId }

    override suspend fun getDebit(id: String): Debit? = state.value.firstOrNull { it.id == id }

    override suspend fun insert(debit: Debit) = state.update { it + debit }

    override suspend fun deleteAll() {
        state.value = emptyList()
    }
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: StateFlow<AppSettings> = state

    override suspend fun setDemoMode(enabled: Boolean) = state.update { it.copy(demoMode = enabled) }

    override suspend fun setVoiceReplies(enabled: Boolean) = state.update { it.copy(voiceReplies = enabled) }

    override suspend fun setVoiceHintShown() = state.update { it.copy(voiceHintShown = true) }

    override suspend fun setSpeechLanguage(language: SpeechLanguage) = state.update {
        it.copy(speechLanguage = language)
    }

    override suspend fun setLastAiPath(path: AiPath) = state.update { it.copy(lastAiPath = path) }
}
