package com.shivangi.gullak.feature.goaldetail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shivangi.gullak.core.domain.ai.PlanExplanationService
import com.shivangi.gullak.core.domain.ai.ReadbackReply
import com.shivangi.gullak.core.domain.ai.readbackReply
import com.shivangi.gullak.core.domain.repository.SettingsRepository
import com.shivangi.gullak.core.domain.usecase.ApplyReplanUseCase
import com.shivangi.gullak.core.domain.usecase.GetReplanOptionsUseCase
import com.shivangi.gullak.core.domain.usecase.GoalDetail
import com.shivangi.gullak.core.domain.usecase.GoalMoneyUseCases
import com.shivangi.gullak.core.domain.usecase.ObserveGoalDetailUseCase
import com.shivangi.gullak.core.domain.usecase.PostNextDebitUseCase
import com.shivangi.gullak.core.domain.usecase.ReplanOffer
import com.shivangi.gullak.core.domain.usecase.TopUpAnswer
import com.shivangi.gullak.core.model.ai.GoalLanguage
import com.shivangi.gullak.core.model.goal.Debit
import com.shivangi.gullak.core.model.goal.DebitKind
import com.shivangi.gullak.core.model.goal.DebitStatus
import com.shivangi.gullak.core.model.goal.Goal
import com.shivangi.gullak.core.model.money.Money
import com.shivangi.gullak.core.model.plan.GoalStatus
import com.shivangi.gullak.core.model.result.AppError
import com.shivangi.gullak.core.model.result.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineExceptionHandler
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

/** The top-up answer being shown, only after the user asked. */
data class TopUpUi(
    val answer: TopUpAnswer,
    val requested: Boolean = false,
)

/** What the goal page's voice is doing, shown on screen. */
enum class GoalTalkPhase { LISTENING, THINKING, SPEAKING }

data class GoalDetailUiState(
    /** Null when the voice is not active. */
    val talkPhase: GoalTalkPhase? = null,
    val gullakSaid: String? = null,
    val youSaid: String? = null,
    val canPayOut: Boolean = false,
    val isPaidOut: Boolean = false,
    val addingMoney: Boolean = false,
    /** How much more this goal can take. */
    val addRoom: Money = Money.ZERO,
    val topUp: TopUpUi? = null,
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

    data object AddMoneyClicked : GoalDetailEvent

    data object AddMoneyDismissed : GoalDetailEvent

    data class AddMoney(val rupees: Long) : GoalDetailEvent

    data object PayOutClicked : GoalDetailEvent

    data object TopUpClicked : GoalDetailEvent

    data object TopUpRequested : GoalDetailEvent

    data object TopUpDismissed : GoalDetailEvent
}

sealed interface GoalDetailEffect {
    data class DebitPosted(val debit: Debit) : GoalDetailEffect

    data object NothingDue : GoalDetailEffect

    data class MoneyAdded(val amount: Money) : GoalDetailEffect

    data class AddMoneyOverGoal(val left: Money) : GoalDetailEffect

    data object AddMoneyGoalFull : GoalDetailEffect

    data object AddMoneyGoalPaidOut : GoalDetailEffect

    data class PaidOut(val amount: Money) : GoalDetailEffect

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
    private val money: GoalMoneyUseCases,
    private val talk: GoalTalk,
) : ViewModel() {

    private val goalId: String = checkNotNull(savedStateHandle[GOAL_ID_KEY]) { "goalId argument missing" }

    private val state = MutableStateFlow(GoalDetailUiState())
    val uiState: StateFlow<GoalDetailUiState> = state.asStateFlow()

    private val effects = Channel<GoalDetailEffect>(Channel.BUFFERED)
    val effect: Flow<GoalDetailEffect> = effects.receiveAsFlow()

    private var explainJob: Job? = null
    private var talkJob: Job? = null

    /** A failure while talking ends the talk, not the app. The page and its buttons still work. */
    private val talkFailed = CoroutineExceptionHandler { _, error -> Log.e(TAG, "Talk stopped", error) }

    init {
        viewModelScope.launch {
            combine(observeDetail(goalId), settings.settings) { detail, prefs -> detail to prefs.demoMode }
                .collect { (detail, demo) ->
                    state.update {
                        it.copy(
                            isLoading = false,
                            detail = detail,
                            demoControls = demo,
                            canPayOut = detail?.let { d -> money.payOut.canPayOut(d.summary.goal, d.debits) } ?: false,
                            isPaidOut = detail?.debits?.any { d -> d.kind == DebitKind.PAYOUT } ?: false,
                            addRoom = detail?.let { d -> money.addMoney.room(d.summary.goal, d.debits) } ?: Money.ZERO,
                        )
                    }
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

            else -> onMoneyEvent(event)
        }
    }

    private fun onMoneyEvent(event: GoalDetailEvent) {
        when (event) {
            GoalDetailEvent.AddMoneyClicked -> state.update { it.copy(addingMoney = true) }

            GoalDetailEvent.AddMoneyDismissed -> state.update { it.copy(addingMoney = false) }

            is GoalDetailEvent.AddMoney -> viewModelScope.launch {
                state.update { it.copy(addingMoney = false) }
                addMoney(Money.fromRupees(event.rupees))
            }

            GoalDetailEvent.PayOutClicked -> {
                stopTalking()
                viewModelScope.launch { payOut() }
            }

            GoalDetailEvent.TopUpClicked -> viewModelScope.launch { askTopUp() }

            GoalDetailEvent.TopUpRequested -> state.update { it.copy(topUp = it.topUp?.copy(requested = true)) }

            GoalDetailEvent.TopUpDismissed -> state.update { it.copy(topUp = null) }

            else -> Unit
        }
    }

    /** Null if the money was added, otherwise why it was not. */
    private suspend fun addMoney(amount: Money): AppError? = when (val result = money.addMoney(goalId, amount)) {
        is AppResult.Success -> {
            send(GoalDetailEffect.MoneyAdded(amount))
            null
        }

        is AppResult.Failure -> {
            send(
                when (val error = result.error) {
                    is AppError.AmountOverGoal -> GoalDetailEffect.AddMoneyOverGoal(error.left)
                    AppError.GoalFull -> GoalDetailEffect.AddMoneyGoalFull
                    AppError.GoalPaidOut -> GoalDetailEffect.AddMoneyGoalPaidOut
                    else -> GoalDetailEffect.ActionFailed
                },
            )
            result.error
        }
    }

    private suspend fun payOut(): Money? = when (val result = money.payOut(goalId)) {
        is AppResult.Success -> {
            send(GoalDetailEffect.PaidOut(result.value.amount))
            result.value.amount
        }

        is AppResult.Failure -> {
            send(GoalDetailEffect.ActionFailed)
            null
        }
    }

    private suspend fun askTopUp(): TopUpAnswer? = when (val result = money.checkTopUp(goalId)) {
        is AppResult.Success -> {
            state.update { it.copy(topUp = TopUpUi(result.value)) }
            result.value
        }

        is AppResult.Failure -> {
            send(GoalDetailEffect.ActionFailed)
            null
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
                    offerPayOutIfDue()
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
        state.update { it.copy(talkPhase = null) }
    }

    /** Spoken Hinglish uses Hindi lines in Devanagari, which the voices say best. */
    private fun spokenLanguage(tag: String): GoalLanguage =
        if (GoalLanguage.fromTag(tag) == GoalLanguage.ENGLISH) GoalLanguage.ENGLISH else GoalLanguage.HINDI

    private suspend fun say(
        text: String,
        language: GoalLanguage,
    ) {
        state.update { it.copy(talkPhase = GoalTalkPhase.SPEAKING, gullakSaid = text) }
        talk.say(text, language)
    }

    /** The user's words, or null if nothing was heard. */
    private suspend fun listen(): String? {
        state.update { it.copy(talkPhase = GoalTalkPhase.LISTENING, youSaid = null) }
        val heard = talk.listen { partial -> state.update { it.copy(youSaid = partial) } }
        state.update { it.copy(youSaid = heard, talkPhase = if (heard == null) null else GoalTalkPhase.THINKING) }
        return heard
    }

    /** Runs a stretch of talking, and clears what is shown when it ends, however it ends. */
    private fun startTalking(block: suspend () -> Unit) {
        stopTalking()
        talkJob = viewModelScope.launch(talkFailed) {
            try {
                block()
            } finally {
                state.update { it.copy(talkPhase = null) }
            }
        }
    }

    /** The mic listens straight away; a second tap stops it. Silence or anything else gets how the goal is doing. */
    private fun talkAboutGoal() {
        val current = state.value
        val summary = current.detail?.summary ?: return
        if (current.talkPhase != null) {
            stopTalking()
            return
        }
        val failure = current.unresolvedFailure
        val language = spokenLanguage(summary.goal.languageTag)
        startTalking {
            state.update { it.copy(gullakSaid = null) }
            val request = listen()?.let { heard -> talk.readRequest(heard, situation(summary.goal)) }
            if (request != null) {
                act(request, summary.goal, language)
            } else {
                say(talk.lines.status(language, summary), language)
                if (failure != null && state.value.replan == null) openReplan(failure.id)
            }
        }
    }

    private fun situation(goal: Goal) =
        "On the page of the goal ${goal.name}. They tapped the mic and may ask to add money, take it out, " +
            "or about a top-up."

    private suspend fun act(
        request: GoalRequest,
        goal: Goal,
        language: GoalLanguage,
    ) {
        when (request) {
            is GoalRequest.AddMoney -> addMoneyByVoice(request.amount, language)
            GoalRequest.PayOut -> payOutByVoice(goal, language)
            GoalRequest.TopUp -> topUpByVoice(language)
        }
    }

    private suspend fun addMoneyByVoice(
        amount: Money,
        language: GoalLanguage,
    ) {
        // Say why straight away, rather than asking for a yes that cannot be honoured.
        val detail = state.value.detail ?: return
        money.addMoney.problem(detail.summary.goal, detail.debits, amount)?.let { problem ->
            say(cannotAddLine(problem, language), language)
            return
        }
        say(talk.lines.confirmAdd(language, amount), language)
        val heard = listen() ?: return
        if (readbackReply(heard) != ReadbackReply.YES) {
            say(talk.lines.leftAsIs(language), language)
            return
        }
        val line = addMoney(amount)?.let { cannotAddLine(it, language) } ?: talk.lines.added(language, amount)
        say(line, language)
    }

    private fun cannotAddLine(
        problem: AppError,
        language: GoalLanguage,
    ): String = when (problem) {
        is AppError.AmountOverGoal -> talk.lines.onlyLeft(language, problem.left)
        AppError.GoalFull -> talk.lines.goalFull(language)
        AppError.GoalPaidOut -> talk.lines.goalPaidOut(language)
        else -> talk.lines.cannotAdd(language)
    }

    private suspend fun payOutByVoice(
        goal: Goal,
        language: GoalLanguage,
    ) {
        val detail = state.value.detail ?: return
        if (!money.payOut.canPayOut(detail.summary.goal, detail.debits)) {
            say(talk.lines.notTimeToPayOut(language, goal.targetDate), language)
            return
        }
        say(talk.lines.confirmPayOut(language, detail.summary.saved), language)
        val heard = listen() ?: return
        if (readbackReply(heard) == ReadbackReply.YES) {
            payOut()?.let { say(talk.lines.paidOut(language, it), language) }
        } else {
            say(talk.lines.leftAsIs(language), language)
        }
    }

    private suspend fun topUpByVoice(language: GoalLanguage) {
        val answer = askTopUp() ?: return
        say(talk.lines.topUp(language, answer), language)
        if (answer !is TopUpAnswer.Offer) return
        val heard = listen() ?: return
        if (readbackReply(heard) == ReadbackReply.YES) {
            state.update { it.copy(topUp = it.topUp?.copy(requested = true)) }
            say(talk.lines.topUpRequested(language), language)
        } else {
            say(talk.lines.leftAsIs(language), language)
        }
    }

    /** The moment the goal is reached is the moment to say so. */
    private fun offerPayOutIfDue() {
        val detail = state.value.detail ?: return
        startTalking {
            // The new payment reaches the state through the repository's flow.
            val current = withTimeoutOrNull(STATE_WAIT_MS) { uiState.first { it.canPayOut } } ?: return@startTalking
            val goal = current.detail?.summary?.goal ?: detail.summary.goal
            if (current.replan == null && talk.isOn()) payOutByVoice(goal, spokenLanguage(goal.languageTag))
        }
    }

    /** Offers the engine's choices aloud, then applies one only on a yes that code reads from the user's words. */
    private fun talkThroughReplan(offer: ReplanOffer) {
        startTalking {
            if (!talk.isOn()) return@startTalking
            val language = spokenLanguage(offer.goal.languageTag)
            say(talk.lines.offer(language, offer.failedDebit, offer.status, offer.choices), language)
            val index = askWhichChoice(offer, language) ?: return@startTalking
            select(index)
            val explanation = withTimeoutOrNull(EXPLANATION_WAIT_MS) {
                uiState.first { it.replan?.isExplaining == false && it.replan?.explanation != null }
            }?.replan?.explanation
            say(listOfNotNull(explanation, talk.lines.applyQuestion(language)).joinToString(" "), language)
            val heard = listen() ?: return@startTalking
            if (readbackReply(heard) == ReadbackReply.YES) {
                if (apply()) say(talk.lines.applied(language), language)
            } else {
                say(talk.lines.leftAsIs(language), language)
            }
        }
    }

    private suspend fun askWhichChoice(
        offer: ReplanOffer,
        language: GoalLanguage,
    ): Int? {
        repeat(CHOICE_TRIES) {
            val heard = listen() ?: return null
            val choice = replanChoiceFrom(heard, offer.choices)
            when {
                choice != null -> return choice

                readbackReply(heard) == ReadbackReply.NO -> {
                    say(talk.lines.leftAsIs(language), language)
                    return null
                }

                else -> say(talk.lines.whichOne(language), language)
            }
        }
        return null
    }

    private fun speakExplanation() {
        val replan = state.value.replan ?: return
        val text = replan.explanation ?: return
        val language = spokenLanguage(replan.offer.goal.languageTag)
        startTalking { say(text, language) }
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
        private const val TAG = "GoalDetail"
        private const val STATE_WAIT_MS = 500L
        private const val EXPLANATION_WAIT_MS = 10_000L
    }
}
