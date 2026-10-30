package com.gullak.feature.creategoal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gullak.core.domain.ai.FestivalMatch
import com.gullak.core.domain.ai.FollowUpQuestion
import com.gullak.core.domain.ai.FollowUpReason
import com.gullak.core.domain.ai.PlanExplanationService
import com.gullak.core.domain.ai.Understanding
import com.gullak.core.domain.analytics.Analytics
import com.gullak.core.domain.analytics.AnalyticsEvent
import com.gullak.core.domain.plan.PlanInput
import com.gullak.core.domain.usecase.BuildPlanUseCase
import com.gullak.core.domain.usecase.CreateGoalUseCase
import com.gullak.core.domain.usecase.NewGoalRequest
import com.gullak.core.domain.usecase.UnderstandGoalUseCase
import com.gullak.core.model.ai.ConversationTurn
import com.gullak.core.model.ai.GoalLanguage
import com.gullak.core.model.ai.Speaker
import com.gullak.core.model.goal.Frequency
import com.gullak.core.model.goal.Goal
import com.gullak.core.model.money.Money
import com.gullak.core.model.plan.Plan
import com.gullak.core.model.plan.PlanOption
import com.gullak.core.model.result.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Stage { EDITING, PROCESSING, DONE }

/** Why the form cannot make a plan yet. Shown from string resources. */
enum class InputProblem { AMOUNT_TOO_LOW, AMOUNT_TOO_HIGH, DATE_NOT_IN_FUTURE, TOO_CLOSE_FOR_SIP }

data class CreateGoalUiState(
    val today: LocalDate,
    val name: String = "",
    val amountText: String = "",
    val targetDate: LocalDate? = null,
    val dateIsFixed: Boolean = false,
    /** Typed goals follow the phone's language; speech sets the language the user spoke. */
    val language: GoalLanguage = deviceLanguage(),
    val isUnderstanding: Boolean = false,
    val transcript: String? = null,
    val followUp: FollowUpQuestion? = null,
    val festival: FestivalMatch? = null,
    val voiceFailed: Boolean = false,
    val plan: Plan? = null,
    val frequency: Frequency = Frequency.MONTHLY,
    val problem: InputProblem? = null,
    val explanation: String? = null,
    val isExplaining: Boolean = false,
    val showMandate: Boolean = false,
    val stage: Stage = Stage.EDITING,
    val createFailed: Boolean = false,
    val createdGoal: Goal? = null,
) {
    val amount: Money? get() = amountText.toLongOrNull()?.let(Money::fromRupees)

    /** The option the user picked; null until the form makes a recurring plan. */
    val chosen: PlanOption? get() = plan?.option(frequency)

    val canStart: Boolean get() = chosen != null && problem == null && stage == Stage.EDITING
}

private fun deviceLanguage(): GoalLanguage =
    if (Locale.getDefault().language == HINDI_TAG) GoalLanguage.HINDI else GoalLanguage.ENGLISH

private const val HINDI_TAG = "hi"

sealed interface CreateGoalEvent {
    data class NameChanged(val name: String) : CreateGoalEvent

    data class AmountChanged(val text: String) : CreateGoalEvent

    data class DateSelected(val date: LocalDate) : CreateGoalEvent

    data class DateFixedChanged(val fixed: Boolean) : CreateGoalEvent

    data class FrequencySelected(val frequency: Frequency) : CreateGoalEvent

    /** Text from speech recognition (or typed into the voice box). */
    data class GoalSpoken(val text: String) : CreateGoalEvent

    data object VoiceUnavailable : CreateGoalEvent

    data object StartClicked : CreateGoalEvent

    data object MandateDismissed : CreateGoalEvent

    data object MandateConfirmed : CreateGoalEvent

    data object DoneClicked : CreateGoalEvent

    data object ViewGoalsClicked : CreateGoalEvent
}

sealed interface CreateGoalEffect {
    data object Finish : CreateGoalEffect

    data object OpenGoals : CreateGoalEffect
}

/**
 * The Goal SIP flow. The AI only fills the form from speech; every amount, date and destination comes from
 * the plan engine (via [BuildPlanUseCase]) and is recalculated again when the goal is created.
 */
@HiltViewModel
class CreateGoalViewModel @Inject constructor(
    private val understandGoal: UnderstandGoalUseCase,
    private val buildPlan: BuildPlanUseCase,
    private val createGoal: CreateGoalUseCase,
    private val explainer: PlanExplanationService,
    private val analytics: Analytics,
    clock: Clock,
) : ViewModel() {

    private val state = MutableStateFlow(CreateGoalUiState(today = LocalDate.now(clock)))
    val uiState: StateFlow<CreateGoalUiState> = state.asStateFlow()

    private val effects = Channel<CreateGoalEffect>(Channel.BUFFERED)
    val effect: Flow<CreateGoalEffect> = effects.receiveAsFlow()

    /** The spoken conversation so far, so a follow-up answer is read together with the first sentence. */
    private val conversation = mutableListOf<ConversationTurn>()
    private var explainJob: Job? = null

    fun onEvent(event: CreateGoalEvent) {
        when (event) {
            is CreateGoalEvent.NameChanged -> edit { it.copy(name = event.name) }

            is CreateGoalEvent.AmountChanged -> edit {
                it.copy(amountText = event.text.filter(Char::isDigit).take(MAX_DIGITS))
            }

            is CreateGoalEvent.DateSelected -> edit { it.copy(targetDate = event.date, festival = null) }

            is CreateGoalEvent.DateFixedChanged -> state.update { it.copy(dateIsFixed = event.fixed) }

            is CreateGoalEvent.FrequencySelected -> {
                state.update { it.copy(frequency = event.frequency) }
                explain()
            }

            is CreateGoalEvent.GoalSpoken -> understand(event.text)

            CreateGoalEvent.VoiceUnavailable -> state.update { it.copy(voiceFailed = true) }

            CreateGoalEvent.StartClicked -> if (state.value.canStart) state.update { it.copy(showMandate = true) }

            CreateGoalEvent.MandateDismissed -> state.update { it.copy(showMandate = false) }

            CreateGoalEvent.MandateConfirmed -> confirm()

            CreateGoalEvent.DoneClicked -> send(CreateGoalEffect.Finish)

            CreateGoalEvent.ViewGoalsClicked -> send(CreateGoalEffect.OpenGoals)
        }
    }

    private fun edit(change: (CreateGoalUiState) -> CreateGoalUiState) {
        state.update { change(it).copy(createFailed = false) }
        replan()
    }

    private fun understand(text: String) {
        if (text.isBlank()) return
        analytics.log(AnalyticsEvent.VoiceUsed)
        conversation += ConversationTurn(Speaker.USER, text.trim())
        state.update { it.copy(isUnderstanding = true, transcript = text.trim(), followUp = null, voiceFailed = false) }
        viewModelScope.launch {
            when (val result = understandGoal(conversation.toList())) {
                is AppResult.Success -> applyUnderstanding(result.value)
                is AppResult.Failure -> state.update { it.copy(isUnderstanding = false, voiceFailed = true) }
            }
        }
    }

    private fun applyUnderstanding(understanding: Understanding) {
        val parsed = understanding.parsed
        state.update { current ->
            current.copy(
                isUnderstanding = false,
                name = parsed.goalName ?: current.name,
                amountText =
                    parsed.targetAmount?.let { (it.paise / Money.PAISE_PER_RUPEE).toString() } ?: current.amountText,
                targetDate = parsed.targetDate ?: current.targetDate,
                language = parsed.language,
            )
        }
        when (understanding) {
            is Understanding.ReadyToConfirm -> {
                conversation.clear()
                state.update {
                    it.copy(
                        name = understanding.goalName,
                        amountText = (understanding.target.paise / Money.PAISE_PER_RUPEE).toString(),
                        targetDate = understanding.targetDate,
                        festival = understanding.festival,
                        followUp = null,
                    )
                }
            }

            is Understanding.NeedsFollowUp -> {
                val question = understanding.question
                val asked = when (question) {
                    is FollowUpQuestion.FromModel -> question.text
                    is FollowUpQuestion.Standard -> modelContext(question.reason)
                }
                conversation += ConversationTurn(Speaker.GULLAK, asked)
                state.update { it.copy(followUp = question, festival = understanding.festival) }
            }
        }
        replan()
    }

    /** Recalculates the plan with the engine whenever the form changes. */
    private fun replan() {
        val current = state.value
        val amount = current.amount
        val date = current.targetDate
        if (amount == null || date == null || current.name.isBlank()) {
            clearPlan(problem = inputProblem(current))
            return
        }
        val problem = inputProblem(current)
        if (problem != null) {
            clearPlan(problem)
            return
        }
        when (val result = buildPlan(PlanInput(target = amount, targetDate = date))) {
            is AppResult.Success -> {
                val plan = result.value
                val frequency = current.frequency.takeIf { plan.option(it) != null }
                    ?: plan.options.lastOrNull()?.frequency
                    ?: current.frequency
                state.update {
                    it.copy(
                        plan = plan,
                        frequency = frequency,
                        problem = InputProblem.TOO_CLOSE_FOR_SIP.takeIf { plan.oneTimeOnly },
                    )
                }
                explain()
            }

            is AppResult.Failure -> state.update { it.copy(plan = null, problem = InputProblem.DATE_NOT_IN_FUTURE) }
        }
    }

    private fun clearPlan(problem: InputProblem?) {
        explainJob?.cancel()
        state.update { it.copy(plan = null, problem = problem, explanation = null, isExplaining = false) }
    }

    /** A value the plan cannot use, or null if the form is fine (or not filled in yet). */
    private fun inputProblem(current: CreateGoalUiState): InputProblem? {
        val amount = current.amount ?: return null
        val date = current.targetDate ?: return null
        return when {
            amount < MIN_AMOUNT -> InputProblem.AMOUNT_TOO_LOW
            amount > MAX_AMOUNT -> InputProblem.AMOUNT_TOO_HIGH
            date <= current.today -> InputProblem.DATE_NOT_IN_FUTURE
            else -> null
        }
    }

    /** Asks for an explanation of the chosen option, after a short pause so typing does not call the model. */
    private fun explain() {
        explainJob?.cancel()
        val current = state.value
        val plan = current.plan
        val chosen = current.chosen
        if (plan == null || chosen == null) return
        state.update { it.copy(explanation = null, isExplaining = true) }
        explainJob = viewModelScope.launch {
            delay(EXPLAIN_DEBOUNCE_MS)
            val text = explainer.explain(current.name.trim(), plan, chosen, current.language)
            state.update { it.copy(explanation = text, isExplaining = false) }
        }
    }

    private fun confirm() {
        val current = state.value
        val amount = current.amount ?: return
        val date = current.targetDate ?: return
        if (!current.canStart) return
        state.update { it.copy(showMandate = false, stage = Stage.PROCESSING) }
        viewModelScope.launch {
            val request = NewGoalRequest(
                name = current.name.trim(),
                target = amount,
                targetDate = date,
                dateIsFixed = current.dateIsFixed,
                frequency = current.frequency,
                language = current.language,
            )
            when (val result = createGoal(request)) {
                is AppResult.Success -> state.update { it.copy(stage = Stage.DONE, createdGoal = result.value) }
                is AppResult.Failure -> state.update { it.copy(stage = Stage.EDITING, createFailed = true) }
            }
        }
    }

    /** What Gullak asked, as context for the model's next reading (not shown to the user). */
    private fun modelContext(reason: FollowUpReason): String = when (reason) {
        FollowUpReason.MISSING_NAME -> "What are you saving for?"
        FollowUpReason.MISSING_AMOUNT -> "How much do you need?"
        FollowUpReason.MISSING_DATE -> "By when do you need it?"
        FollowUpReason.AMOUNT_OUT_OF_RANGE -> "Please give an amount between 500 and 50 lakh rupees."
        FollowUpReason.DATE_NOT_IN_FUTURE -> "Please give a date after today."
        FollowUpReason.DATE_TOO_FAR -> "Please give a date within 10 years."
    }

    private fun send(effect: CreateGoalEffect) {
        viewModelScope.launch { effects.send(effect) }
    }

    private companion object {
        val MIN_AMOUNT = Money.fromRupees(500)
        val MAX_AMOUNT = Money.fromRupees(50_00_000)
        const val MAX_DIGITS = 8
        const val EXPLAIN_DEBOUNCE_MS = 400L
    }
}
