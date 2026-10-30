package com.gullak.feature.goaldetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gullak.core.domain.ai.PlanExplanationService
import com.gullak.core.domain.ai.VoiceOutput
import com.gullak.core.domain.repository.SettingsRepository
import com.gullak.core.domain.usecase.ApplyReplanUseCase
import com.gullak.core.domain.usecase.GetReplanOptionsUseCase
import com.gullak.core.domain.usecase.GoalDetail
import com.gullak.core.domain.usecase.ObserveGoalDetailUseCase
import com.gullak.core.domain.usecase.PostNextDebitUseCase
import com.gullak.core.domain.usecase.ReplanOffer
import com.gullak.core.model.ai.GoalLanguage
import com.gullak.core.model.goal.Debit
import com.gullak.core.model.goal.DebitStatus
import com.gullak.core.model.plan.GoalStatus
import com.gullak.core.model.result.AppError
import com.gullak.core.model.result.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The recovery sheet after a failed AutoPay: the engine's choices, the selected one and its explanation. */
data class ReplanUi(
    val offer: ReplanOffer,
    val selected: Int = 0,
    val explanation: String? = null,
    val isExplaining: Boolean = false,
    val isApplying: Boolean = false,
)

data class GoalDetailUiState(
    val isLoading: Boolean = true,
    val detail: GoalDetail? = null,
    val demoControls: Boolean = false,
    val isPosting: Boolean = false,
    val replan: ReplanUi? = null,
) {
    /** The most recent failed AutoPay while the goal is still behind: the "Fix plan" banner. */
    val unresolvedFailure: Debit?
        get() {
            val current = detail ?: return null
            if (current.summary.status !is GoalStatus.Behind) return null
            return current.debits.firstOrNull { it.status == DebitStatus.FAILED }
        }
}

sealed interface GoalDetailEvent {
    data object SimulateNextDebit : GoalDetailEvent

    data object SimulateFailedDebit : GoalDetailEvent

    data class OpenReplan(val failedDebitId: String) : GoalDetailEvent

    data class ChoiceSelected(val index: Int) : GoalDetailEvent

    data object ApplyReplan : GoalDetailEvent

    data object DismissReplan : GoalDetailEvent

    data object SpeakExplanation : GoalDetailEvent
}

sealed interface GoalDetailEffect {
    data class DebitPosted(val debit: Debit) : GoalDetailEffect

    data object NothingDue : GoalDetailEffect

    data object PlanUpdated : GoalDetailEffect

    data object ActionFailed : GoalDetailEffect
}

/**
 * One Goal SIP: saved amount, status and history, the demo controls that post the next debit, and the replan
 * sheet after a failure. Every amount and date comes from the plan engine through the use cases.
 */
@HiltViewModel
class GoalDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDetail: ObserveGoalDetailUseCase,
    settings: SettingsRepository,
    private val postNextDebit: PostNextDebitUseCase,
    private val getReplanOptions: GetReplanOptionsUseCase,
    private val applyReplan: ApplyReplanUseCase,
    private val explainer: PlanExplanationService,
    private val voice: VoiceOutput,
) : ViewModel() {

    private val goalId: String = checkNotNull(savedStateHandle[GOAL_ID_KEY]) { "goalId argument missing" }

    private val state = MutableStateFlow(GoalDetailUiState())
    val uiState: StateFlow<GoalDetailUiState> = state.asStateFlow()

    private val effects = Channel<GoalDetailEffect>(Channel.BUFFERED)
    val effect: Flow<GoalDetailEffect> = effects.receiveAsFlow()

    private var explainJob: Job? = null

    init {
        viewModelScope.launch {
            combine(observeDetail(goalId), settings.settings) { detail, prefs -> detail to prefs.demoMode }
                .collect { (detail, demo) ->
                    state.update { it.copy(isLoading = false, detail = detail, demoControls = demo) }
                }
        }
    }

    fun onEvent(event: GoalDetailEvent) {
        when (event) {
            GoalDetailEvent.SimulateNextDebit -> simulate(succeed = true)

            GoalDetailEvent.SimulateFailedDebit -> simulate(succeed = false)

            is GoalDetailEvent.OpenReplan -> openReplan(event.failedDebitId)

            is GoalDetailEvent.ChoiceSelected -> select(event.index)

            GoalDetailEvent.ApplyReplan -> apply()

            GoalDetailEvent.SpeakExplanation -> speakExplanation()

            GoalDetailEvent.DismissReplan -> {
                explainJob?.cancel()
                state.update { it.copy(replan = null) }
            }
        }
    }

    private fun simulate(succeed: Boolean) {
        if (state.value.isPosting) return
        state.update { it.copy(isPosting = true) }
        viewModelScope.launch {
            val result = postNextDebit(goalId, succeed)
            state.update { it.copy(isPosting = false) }
            when (result) {
                is AppResult.Success -> {
                    send(GoalDetailEffect.DebitPosted(result.value))
                    if (result.value.status == DebitStatus.FAILED) openReplan(result.value.id)
                }

                is AppResult.Failure -> send(
                    if (result.error ==
                        AppError.NothingDue
                    ) {
                        GoalDetailEffect.NothingDue
                    } else {
                        GoalDetailEffect.ActionFailed
                    },
                )
            }
        }
    }

    private fun openReplan(failedDebitId: String) {
        viewModelScope.launch {
            when (val result = getReplanOptions(goalId, failedDebitId)) {
                is AppResult.Success -> {
                    state.update { it.copy(replan = ReplanUi(result.value)) }
                    explainSelected()
                }

                is AppResult.Failure -> send(GoalDetailEffect.ActionFailed)
            }
        }
    }

    private fun select(index: Int) {
        val replan = state.value.replan ?: return
        if (index !in replan.offer.choices.indices || index == replan.selected) return
        state.update { it.copy(replan = replan.copy(selected = index, explanation = null)) }
        explainSelected()
    }

    private fun explainSelected() {
        explainJob?.cancel()
        val replan = state.value.replan ?: return
        val choice = replan.offer.choices.getOrNull(replan.selected) ?: return
        val goal = replan.offer.goal
        state.update { it.copy(replan = replan.copy(isExplaining = true)) }
        explainJob = viewModelScope.launch {
            val language = GoalLanguage.fromTag(goal.languageTag) ?: GoalLanguage.ENGLISH
            val text = explainer.explainReplan(goal.name, choice, replan.offer.failedDebit.amount, language)
            state.update { current ->
                current.copy(replan = current.replan?.copy(explanation = text, isExplaining = false))
            }
        }
    }

    private fun apply() {
        val replan = state.value.replan ?: return
        val choice = replan.offer.choices.getOrNull(replan.selected) ?: return
        state.update { it.copy(replan = replan.copy(isApplying = true)) }
        viewModelScope.launch {
            when (applyReplan(goalId, replan.offer.failedDebit.id, choice)) {
                is AppResult.Success -> {
                    state.update { it.copy(replan = null) }
                    send(GoalDetailEffect.PlanUpdated)
                }

                is AppResult.Failure -> {
                    state.update { it.copy(replan = it.replan?.copy(isApplying = false)) }
                    send(GoalDetailEffect.ActionFailed)
                }
            }
        }
    }

    private fun speakExplanation() {
        val replan = state.value.replan ?: return
        val text = replan.explanation ?: return
        val language = GoalLanguage.fromTag(replan.offer.goal.languageTag) ?: GoalLanguage.ENGLISH
        viewModelScope.launch { voice.speak(text, language) }
    }

    override fun onCleared() {
        voice.stop()
    }

    private fun send(effect: GoalDetailEffect) {
        viewModelScope.launch { effects.send(effect) }
    }

    companion object {
        /** Matches the property name in [GoalDetailRoute]. */
        const val GOAL_ID_KEY = "goalId"
    }
}
