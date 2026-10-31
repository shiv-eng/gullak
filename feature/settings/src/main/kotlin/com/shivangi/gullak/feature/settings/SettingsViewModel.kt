package com.shivangi.gullak.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shivangi.gullak.core.domain.ai.AiInfo
import com.shivangi.gullak.core.domain.gateway.DemoClockController
import com.shivangi.gullak.core.domain.repository.SettingsRepository
import com.shivangi.gullak.core.domain.usecase.FastForwardUseCase
import com.shivangi.gullak.core.domain.usecase.ResetDemoDataUseCase
import com.shivangi.gullak.core.model.result.AppResult
import com.shivangi.gullak.core.model.settings.AppSettings
import com.shivangi.gullak.core.model.settings.SpeechLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val today: LocalDate? = null,
    val modelId: String = "",
    val liveModelConfigured: Boolean = false,
    val isWorking: Boolean = false,
) {
    /** The next months the demo can jump to, for the fast-forward chips. */
    val fastForwardMonths: List<YearMonth>
        get() = today?.let { date -> (1..FAST_FORWARD_MONTHS).map { YearMonth.from(date).plusMonths(it.toLong()) } }
            ?: emptyList()

    companion object {
        const val FAST_FORWARD_MONTHS = 12
    }
}

sealed interface SettingsEvent {
    data class DemoModeChanged(val enabled: Boolean) : SettingsEvent

    data class InterruptBySpeakingChanged(val enabled: Boolean) : SettingsEvent

    data class VoiceRepliesChanged(val enabled: Boolean) : SettingsEvent

    data class SpeechLanguageChanged(val language: SpeechLanguage) : SettingsEvent

    data class FastForward(val month: YearMonth) : SettingsEvent

    data object BackToToday : SettingsEvent

    /** Clears every goal and resets the demo date; with [seed], adds Sunita's two goals. */
    data class ResetDemo(val seed: Boolean) : SettingsEvent
}

sealed interface SettingsEffect {
    data class FastForwarded(val date: LocalDate) : SettingsEffect

    data object DateReset : SettingsEffect

    data class DemoReset(val seeded: Boolean) : SettingsEffect

    data object Failed : SettingsEffect
}

/** Demo controls and preferences. The app language is changed by the screen through Android's per-app locales. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val demoClock: DemoClockController,
    private val fastForward: FastForwardUseCase,
    private val resetDemoData: ResetDemoDataUseCase,
    aiInfo: AiInfo,
) : ViewModel() {

    private val state = MutableStateFlow(
        SettingsUiState(modelId = aiInfo.modelId, liveModelConfigured = aiInfo.isLiveModelConfigured),
    )
    val uiState: StateFlow<SettingsUiState> = state.asStateFlow()

    private val effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effect: Flow<SettingsEffect> = effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(settings.settings, demoClock.today) { prefs, today -> prefs to today }
                .collect { (prefs, today) -> state.update { it.copy(settings = prefs, today = today) } }
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.DemoModeChanged -> viewModelScope.launch { settings.setDemoMode(event.enabled) }

            is SettingsEvent.InterruptBySpeakingChanged -> viewModelScope.launch {
                settings.setInterruptBySpeaking(event.enabled)
            }

            is SettingsEvent.VoiceRepliesChanged -> viewModelScope.launch { settings.setVoiceReplies(event.enabled) }

            is SettingsEvent.SpeechLanguageChanged -> viewModelScope.launch {
                settings.setSpeechLanguage(event.language)
            }

            is SettingsEvent.FastForward -> work {
                when (val result = fastForward(event.month)) {
                    is AppResult.Success -> SettingsEffect.FastForwarded(result.value)
                    is AppResult.Failure -> SettingsEffect.Failed
                }
            }

            SettingsEvent.BackToToday -> work {
                demoClock.reset()
                SettingsEffect.DateReset
            }

            is SettingsEvent.ResetDemo -> work {
                when (resetDemoData(seed = event.seed)) {
                    is AppResult.Success -> SettingsEffect.DemoReset(event.seed)
                    is AppResult.Failure -> SettingsEffect.Failed
                }
            }
        }
    }

    /** Runs one demo action at a time and reports its outcome. */
    private fun work(action: suspend () -> SettingsEffect) {
        if (state.value.isWorking) return
        state.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            val effect = action()
            state.update { it.copy(isWorking = false) }
            effects.send(effect)
        }
    }
}
