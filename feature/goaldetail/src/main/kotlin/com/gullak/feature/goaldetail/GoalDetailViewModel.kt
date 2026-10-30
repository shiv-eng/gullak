package com.gullak.feature.goaldetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gullak.core.domain.ai.PlanExplanationService
import com.gullak.core.domain.ai.ReadbackReply
import com.gullak.core.domain.ai.readbackReply
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

    /** The mic: Gullak says how this goal is doing, and offers to fix a failed AutoPay. */
    data object TalkClicked : GoalDetailEvent
}

sealed interface GoalDetailEffect {
    data class DebitPosted(val debit: Debit) : GoalDetailEffect

    data object NothingDue : GoalDetailEffect

    data object PlanUpdated : GoalDetailEffect

    data object ActionFailed : GoalDetailEffect
}

/**
 * One Gullak: saved amount, status and history, the demo controls that post the next debit, and the replan
 * sheet after a failure. Every amount and date comes from the plan engine through the use cases.
 */
@HiltViewModel
class GoalDetailViewModel @Inject internal constructor(
    savedStateHandle: SavedStateHandle,
    observeDetail: ObserveGoalDetailUseCase,
    settings: SettingsRepository,
    private val postNextDebit: PostNextDebitUseCase,
    private val getReplanOptions: GetReplanOptionsUseCase,
    private val applyReplan: ApplyReplanUseCase,
    private val explainer: PlanExplanationService,
    private val talk: GoalTalk,
) : ViewModel() {

    private val goalId: String = checkNotNull(savedStateHandle[GOAL_ID_KEY]) { "goalId argument missing" }

    private val state = MutableStateFlow(GoalDetailUiState())
    val uiState: StateFlow<GoalDetailUiState> = state.asStateFlow()

    private val effects = Channel<GoalDetailEffect>(Channel.BUFFERED)
    val effect: Flow<GoalDetailEffect> = effects.receiveAsFlow()

    private var explainJob: Job? = null
    private var talkJob: Job? = null

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

            is GoalDetailEvent.ChoiceSelected -> {
                stopTalking()
                select(event.index)
            }

            GoalDetailEvent.ApplyReplan -> {
                stopTalking()
                viewModelScope.launch { apply() }
            }

            GoalDetailEvent.TalkClicked -> talkAboutGoal()

            GoalDetailEvent.SpeakExplanation -> speakExplanation()

            GoalDetailEvent.DismissReplan -> {
                stopTalking()
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
                    talkThroughReplan(result.value)
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
            val language = spokenLanguage(goal.languageTag)
            val text = explainer.explainReplan(goal.name, choice, replan.offer.failedDebit.amount, language)
            state.update { current ->
                current.copy(replan = current.replan?.copy(explanation = text, isExplaining = false))
            }
        }
    }

    /** True if the plan was changed. */
    private suspend fun apply(): Boolean {
        val replan = state.value.replan ?: return false
        val choice = replan.offer.choices.getOrNull(replan.selected) ?: return false
        state.update { it.copy(replan = replan.copy(isApplying = true)) }
        return when (applyReplan(goalId, replan.offer.failedDebit.id, choice)) {
            is AppResult.Success -> {
                state.update { it.copy(replan = null) }
                send(GoalDetailEffect.PlanUpdated)
                true
            }

            is AppResult.Failure -> {
                state.update { it.copy(replan = it.replan?.copy(isApplying = false)) }
                send(GoalDetailEffect.ActionFailed)
                false
            }
        }
    }

    private fun stopTalking() {
        talkJob?.cancel()
        talk.stop()
    }

    /** Spoken Hinglish uses Hindi lines in Devanagari, which the voices say best. */
    private fun spokenLanguage(tag: String): GoalLanguage =
        if (GoalLanguage.fromTag(tag) == GoalLanguage.ENGLISH) GoalLanguage.ENGLISH else GoalLanguage.HINDI

    private fun talkAboutGoal() {
        val current = state.value
        val summary = current.detail?.summary ?: return
        val failure = current.unresolvedFailure
        stopTalking()
        talkJob = viewModelScope.launch {
            talk.say(
                talk.lines.status(spokenLanguage(summary.goal.languageTag), summary),
                spokenLanguage(summary.goal.languageTag),
            )
            if (failure != null && state.value.replan == null) openReplan(failure.id)
        }
    }

    /** Offers the engine's choices aloud, then applies one only on a yes that code reads from the user's words. */
    private fun talkThroughReplan(offer: ReplanOffer) {
        stopTalking()
        talkJob = viewModelScope.launch {
            if (!talk.isOn()) return@launch
            val language = spokenLanguage(offer.goal.languageTag)
            talk.say(talk.lines.offer(language, offer.failedDebit, offer.status, offer.choices), language)
            val index = askWhichChoice(offer, language) ?: return@launch
            select(index)
            val explanation = withTimeoutOrNull(EXPLANATION_WAIT_MS) {
                uiState.first { it.replan?.isExplaining == false && it.replan?.explanation != null }
            }?.replan?.explanation
            talk.say(listOfNotNull(explanation, talk.lines.applyQuestion(language)).joinToString(" "), language)
            val heard = talk.listen() ?: return@launch
            if (readbackReply(heard) == ReadbackReply.YES) {
                if (apply()) talk.say(talk.lines.applied(language), language)
            } else {
                talk.say(talk.lines.leftAsIs(language), language)
            }
        }
    }

    private suspend fun askWhichChoice(
        offer: ReplanOffer,
        language: GoalLanguage,
    ): Int? {
        repeat(CHOICE_TRIES) {
            val heard = talk.listen() ?: return null
            val choice = replanChoiceFrom(heard, offer.choices)
            when {
                choice != null -> return choice

                readbackReply(heard) == ReadbackReply.NO -> {
                    talk.say(talk.lines.leftAsIs(language), language)
                    return null
                }

                else -> talk.say(talk.lines.whichOne(language), language)
            }
        }
        return null
    }

    private fun speakExplanation() {
        val replan = state.value.replan ?: return
        val text = replan.explanation ?: return
        val language = spokenLanguage(replan.offer.goal.languageTag)
        stopTalking()
        talkJob = viewModelScope.launch { talk.say(text, language) }
    }

    override fun onCleared() {
        talk.stop()
    }

    private fun send(effect: GoalDetailEffect) {
        viewModelScope.launch { effects.send(effect) }
    }

    companion object {
        /** Matches the property name in [GoalDetailRoute]. */
        const val GOAL_ID_KEY = "goalId"
        private const val CHOICE_TRIES = 2
        private const val EXPLANATION_WAIT_MS = 10_000L
    }
}
