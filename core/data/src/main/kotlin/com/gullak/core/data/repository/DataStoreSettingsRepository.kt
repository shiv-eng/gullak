package com.gullak.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gullak.core.domain.repository.SettingsRepository
import com.gullak.core.model.ai.AiPath
import com.gullak.core.model.settings.AppSettings
import com.gullak.core.model.settings.SpeechLanguage
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class DataStoreSettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) :
    SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .map { prefs ->
            val defaults = AppSettings()
            AppSettings(
                demoMode = prefs[DEMO_MODE] ?: defaults.demoMode,
                voiceReplies = prefs[VOICE_REPLIES] ?: defaults.voiceReplies,
                voiceHintShown = prefs[VOICE_HINT_SHOWN] ?: defaults.voiceHintShown,
                interruptBySpeaking = prefs[INTERRUPT_BY_SPEAKING] ?: defaults.interruptBySpeaking,
                speechLanguage = prefs[SPEECH_LANGUAGE]
                    ?.let { name -> SpeechLanguage.entries.firstOrNull { it.name == name } }
                    ?: defaults.speechLanguage,
                lastAiPath = prefs[LAST_AI_PATH]?.let { name -> AiPath.entries.firstOrNull { it.name == name } },
            )
        }
        .distinctUntilChanged()

    override suspend fun setDemoMode(enabled: Boolean) {
        dataStore.edit { it[DEMO_MODE] = enabled }
    }

    override suspend fun setVoiceReplies(enabled: Boolean) {
        dataStore.edit { it[VOICE_REPLIES] = enabled }
    }

    override suspend fun setVoiceHintShown() {
        dataStore.edit { it[VOICE_HINT_SHOWN] = true }
    }

    override suspend fun setInterruptBySpeaking(enabled: Boolean) {
        dataStore.edit { it[INTERRUPT_BY_SPEAKING] = enabled }
    }

    override suspend fun setSpeechLanguage(language: SpeechLanguage) {
        dataStore.edit { it[SPEECH_LANGUAGE] = language.name }
    }

    override suspend fun setLastAiPath(path: AiPath) {
        dataStore.edit { it[LAST_AI_PATH] = path.name }
    }

    private companion object {
        val DEMO_MODE = booleanPreferencesKey("demo_mode")
        val VOICE_REPLIES = booleanPreferencesKey("voice_replies")
        val VOICE_HINT_SHOWN = booleanPreferencesKey("voice_hint_shown")
        val INTERRUPT_BY_SPEAKING = booleanPreferencesKey("interrupt_by_speaking")
        val SPEECH_LANGUAGE = stringPreferencesKey("speech_language")
        val LAST_AI_PATH = stringPreferencesKey("last_ai_path")
    }
}
