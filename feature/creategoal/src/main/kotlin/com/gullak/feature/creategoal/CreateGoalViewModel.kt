package com.gullak.feature.creategoal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gullak.core.domain.ai.FestivalMatch
import com.gullak.core.domain.ai.FollowUpQuestion
import com.gullak.core.domain.ai.FollowUpReason
import com.gullak.core.domain.ai.PlanExplanationService
import com.gullak.core.domain.ai.ReadbackReply
import com.gullak.core.domain.ai.SpeechError
import com.gullak.core.domain.ai.SpeechEvent
import com.gullak.core.domain.ai.SpeechInput
import com.gullak.core.domain.ai.Understanding
import com.gullak.core.domain.ai.VoiceOutput
import com.gullak.core.domain.ai.readbackReply
import com.gullak.core.domain.analytics.Analytics
import com.gullak.core.domain.analytics.AnalyticsEvent
import com.gullak.core.domain.plan.PlanInput
import com.gullak.core.domain.repository.SettingsRepository
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
import com.gullak.core.model.settings.SpeechLanguage
import com.gullak.feature.creategoal.voice.TalkIo
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
import kotlinx.coroutines.withTimeoutOrNull

/** EDITING → (AutoPay sheet) → BANK → APPROVAL → PROCESSING → DONE. */
enum class Stage { EDITING, BANK, APPROVAL, PROCESSING, DONE }

/** Made-up accounts for the mock UPI AutoPay step. No real bank, account or UPI ID. */
enum class DemoBankAccount { SBI_SAVINGS, HDFC_SAVINGS, ICICI_SAVINGS }

/** Why speech did not fill the form. Shown from string resources. */
enum class VoiceProblem { FAILED, NOT_HEARD, MIC_DENIED }

/** Why the form cannot make a plan yet. Shown from string resources. */
enum class InputProblem { AMOUNT_TOO_LOW, AMOUNT_TOO_HIGH, DATE_NOT_IN_FUTURE, TOO_CLOSE_FOR_SIP, LUMP_SUM_TOO_HIGH }

/** What Gullak understood from speech, read back for the user to confirm. All values are checked ones. */
data class GoalReadback(
    val name: String,
    val target: Money,
    val date: LocalDate,
)

data class CreateGoalUiState(
    val today: LocalDate,
    val name: String = "",
    val amountText: String = "",
    /** Optional one-time payment now; the plan covers what is left. */
    val lumpSumText: String = "",
    val targetDate: LocalDate? = null,
    val dateIsFixed: Boolean = false,
    /** Typed goals follow the phone's language; speech sets the language the user spoke. */
    val language: GoalLanguage = deviceLanguage(),
    val isUnderstanding: Boolean = false,
    val transcript: String? = null,
    val followUp: FollowUpQuestion? = null,
    /** Waiting for the user to confirm or correct what was understood. */
    val readback: GoalReadback? = null,
    val festival: FestivalMatch? = null,
    val isListening: Boolean = false,
    /** What the recogniser has heard so far, while listening. */
    val partialTranscript: String? = null,
    /** Loudness from 0 to 1, while listening. */
    val micLevel: Float = 0f,
    val voiceProblem: VoiceProblem? = null,
    /** The phone has no voice for the spoken language. */
    val showVoiceHint: Boolean = false,
    val plan: Plan? = null,
    val frequency: Frequency = Frequency.MONTHLY,
    val problem: InputProblem? = null,
    val explanation: String? = null,
    val isExplaining: Boolean = false,
    val showMandate: Boolean = false,
    val stage: Stage = Stage.EDITING,
    val bankAccount: DemoBankAccount = DemoBankAccount.SBI_SAVINGS,
    /** How many demo approval digits are entered. The digits themselves are never kept. */
    val approvalDigits: Int = 0,
    /** BCP 47 tag for speech input, from Settings. */
    val speechLanguageTag: String = SpeechLanguage.ENGLISH_INDIA.tag,
    val voiceReplies: Boolean = true,
    val createFailed: Boolean = false,
    val createdGoal: Goal? = null,
) {
    val voiceFailed: Boolean get() = voiceProblem == VoiceProblem.FAILED

    val amount: Money? get() = amountText.toLongOrNull()?.let(Money::fromRupees)

    val lumpSum: Money get() = lumpSumText.toLongOrNull()?.let(Money::fromRupees) ?: Money.ZERO

    /** The option the user picked; null until the form makes a recurring plan. */
    val chosen: PlanOption? get() = plan?.option(frequency)

    private val planReady: Boolean get() = chosen != null && problem == null

    val canStart: Boolean get() = planReady && stage == Stage.EDITING

    val canConfirm: Boolean get() = planReady && stage == Stage.APPROVAL && approvalDigits == APPROVAL_LENGTH

    companion object {
        const val APPROVAL_LENGTH = 4
    }
}

private fun deviceLanguage(): GoalLanguage =
    if (Locale.getDefault().language == HINDI_TAG) GoalLanguage.HINDI else GoalLanguage.ENGLISH

private const val HINDI_TAG = "hi"

sealed interface CreateGoalEvent {
    data class NameChanged(val name: String) : CreateGoalEvent

    data class AmountChanged(val text: String) : CreateGoalEvent

    data class LumpSumChanged(val text: String) : CreateGoalEvent

    data class DateSelected(val date: LocalDate) : CreateGoalEvent

    data class DateFixedChanged(val fixed: Boolean) : CreateGoalEvent

    data class FrequencySelected(val frequency: Frequency) : CreateGoalEvent

    /** Text from speech recognition (or typed into the voice box). */
    data class GoalSpoken(val text: String) : CreateGoalEvent

    data object VoiceUnavailable : CreateGoalEvent

    /** The mic button, once the microphone permission is granted: starts or stops listening. */
    data object ListenClicked : CreateGoalEvent

    data object MicDenied : CreateGoalEvent

    /** The current follow-up question, in words: read it aloud if replies are on, then listen for the answer. */
    data class FollowUpShown(val text: String) : CreateGoalEvent

    /** The current read-back, in words: read it aloud if replies are on, then listen for yes, no or a correction. */
    data class ReadbackShown(val text: String) : CreateGoalEvent

    data object VoiceHintDismissed : CreateGoalEvent

    /** Read [text] aloud (a follow-up question or an explanation, already in the right language). */
    data class Speak(val text: String) : CreateGoalEvent

    data object StartClicked : CreateGoalEvent

    data object MandateDismissed : CreateGoalEvent

    data object MandateConfirmed : CreateGoalEvent

    data class BankSelected(val account: DemoBankAccount) : CreateGoalEvent

    data object BankProceed : CreateGoalEvent

    /** A key on the demo approval keypad; the value is not used or stored. */
    data object ApprovalDigit : CreateGoalEvent

    data object ApprovalDelete : CreateGoalEvent

    /** Back from the bank or approval step. */
    data object StepBack : CreateGoalEvent

    data object DoneClicked : CreateGoalEvent

    data object ViewGoalsClicked : CreateGoalEvent
}

sealed interface CreateGoalEffect {
    data object Finish : CreateGoalEffect

    data object OpenGoals : CreateGoalEffect

    /** This phone cannot recognise speech inside the app: use its own speech screen. */
    data object OpenPhoneSpeech : CreateGoalEffect
}

/**
 * The Gullak flow. The AI only fills the form from speech; every amount, date and destination comes from
 * the plan engine (via [BuildPlanUseCase]) and is recalculated again when the goal is created.
 */
@HiltViewModel
class CreateGoalViewModel @Inject internal constructor(
    private val savedState: SavedStateHandle,
    private val understandGoal: UnderstandGoalUseCase,
    private val buildPlan: BuildPlanUseCase,
    private val createGoal: CreateGoalUseCase,
    private val explainer: PlanExplanationService,
    private val analytics: Analytics,
    io: TalkIo,
    clock: Clock,
) : ViewModel() {

    private val voice: VoiceOutput = io.voice
    private val speech: SpeechInput = io.speech
    private val settings: SettingsRepository = io.settings

    private val state = MutableStateFlow(restoredForm(CreateGoalUiState(today = LocalDate.now(clock))))
    val uiState: StateFlow<CreateGoalUiState> = state.asStateFlow()

    private val effects = Channel<CreateGoalEffect>(Channel.BUFFERED)
    val effect: Flow<CreateGoalEffect> = effects.receiveAsFlow()

    /** The spoken conversation so far, so a follow-up answer is read together with the first sentence. */
    private val conversation = mutableListOf<ConversationTurn>()
    private var explainJob: Job? = null
    private var speakJob: Job? = null
    private var listenJob: Job? = null
    private var announcedFollowUp: FollowUpQuestion? = null
    private var announcedReadback: GoalReadback? = null
    private var voiceHintShown = false

    /** Read the next explanation aloud: the user has just confirmed a goal by voice. */
    private var speakNextExplanation = false

    init {
        replan()
        viewModelScope.launch {
            settings.settings.collect { prefs ->
                voiceHintShown = prefs.voiceHintShown
                state.update {
                    it.copy(speechLanguageTag = prefs.speechLanguage.tag, voiceReplies = prefs.voiceReplies)
                }
            }
        }
    }

    fun onEvent(event: CreateGoalEvent) {
        when (event) {
            is CreateGoalEvent.NameChanged -> edit { it.copy(name = event.name) }

            is CreateGoalEvent.AmountChanged -> edit {
                it.copy(amountText = event.text.filter(Char::isDigit).take(MAX_DIGITS))
            }

            is CreateGoalEvent.LumpSumChanged -> edit {
                it.copy(lumpSumText = event.text.filter(Char::isDigit).take(MAX_DIGITS))
            }

            is CreateGoalEvent.DateSelected -> edit { it.copy(targetDate = event.date, festival = null) }

            is CreateGoalEvent.DateFixedChanged -> {
                state.update { it.copy(dateIsFixed = event.fixed) }
                rememberForm()
            }

            is CreateGoalEvent.FrequencySelected -> {
                state.update { it.copy(frequency = event.frequency) }
                explain()
            }

            CreateGoalEvent.DoneClicked -> send(CreateGoalEffect.Finish)

            CreateGoalEvent.ViewGoalsClicked -> send(CreateGoalEffect.OpenGoals)

            else -> onVoiceEvent(event)
        }
    }

    private fun onVoiceEvent(event: CreateGoalEvent) {
        when (event) {
            is CreateGoalEvent.GoalSpoken -> heard(event.text)

            CreateGoalEvent.VoiceUnavailable -> state.update { it.copy(voiceProblem = VoiceProblem.FAILED) }

            CreateGoalEvent.ListenClicked -> {
                val wasListening = state.value.isListening
                stopVoice()
                if (!wasListening) startListening()
            }

            CreateGoalEvent.MicDenied -> state.update { it.copy(voiceProblem = VoiceProblem.MIC_DENIED) }

            is CreateGoalEvent.FollowUpShown -> announceFollowUp(event.text)

            is CreateGoalEvent.ReadbackShown -> announceReadback(event.text)

            CreateGoalEvent.VoiceHintDismissed -> state.update { it.copy(showVoiceHint = false) }

            is CreateGoalEvent.Speak -> speak(event.text)

            else -> onPaymentEvent(event)
        }
    }

    /** The AutoPay steps: sheet, bank account, demo approval. */
    private fun onPaymentEvent(event: CreateGoalEvent) {
        when (event) {
            CreateGoalEvent.StartClicked -> if (state.value.canStart) {
                stopVoice()
                state.update { it.copy(showMandate = true, readback = null) }
            }

            CreateGoalEvent.MandateDismissed -> state.update { it.copy(showMandate = false) }

            CreateGoalEvent.MandateConfirmed -> if (state.value.canStart) {
                state.update { it.copy(showMandate = false, stage = Stage.BANK) }
            }

            is CreateGoalEvent.BankSelected -> state.update { it.copy(bankAccount = event.account) }

            CreateGoalEvent.BankProceed -> state.update { it.copy(stage = Stage.APPROVAL, approvalDigits = 0) }

            CreateGoalEvent.ApprovalDigit -> enterDigit()

            CreateGoalEvent.ApprovalDelete -> state.update {
                it.copy(approvalDigits = (it.approvalDigits - 1).coerceAtLeast(0))
            }

            CreateGoalEvent.StepBack -> stepBack()

            else -> Unit
        }
    }

    /** The typed form comes back if Android closes the app while it is in the background. */
    private fun rememberForm() {
        val current = state.value
        savedState[KEY_NAME] = current.name
        savedState[KEY_AMOUNT] = current.amountText
        savedState[KEY_LUMP_SUM] = current.lumpSumText
        savedState[KEY_DATE] = current.targetDate?.toString()
        savedState[KEY_FIXED] = current.dateIsFixed
    }

    private fun restoredForm(blank: CreateGoalUiState) = blank.copy(
        name = savedState[KEY_NAME] ?: blank.name,
        amountText = savedState[KEY_AMOUNT] ?: blank.amountText,
        lumpSumText = savedState[KEY_LUMP_SUM] ?: blank.lumpSumText,
        targetDate = savedState.get<String>(KEY_DATE)?.let(LocalDate::parse),
        dateIsFixed = savedState[KEY_FIXED] ?: blank.dateIsFixed,
    )

    private fun edit(change: (CreateGoalUiState) -> CreateGoalUiState) {
        // Typing takes over from speech.
        speakNextExplanation = false
        if (speakJob?.isActive == true || state.value.isListening) stopVoice()
        state.update { change(it).copy(createFailed = false, readback = null) }
        rememberForm()
        replan()
    }

    private fun understand(text: String) {
        if (text.isBlank()) return
        analytics.log(AnalyticsEvent.VoiceUsed)
        // A question is open: this reply belongs to the same goal. Otherwise it starts a new one.
        if (state.value.followUp == null && state.value.readback == null) conversation.clear()
        conversation += ConversationTurn(Speaker.USER, text.trim())
        announcedFollowUp = null
        announcedReadback = null
        speakNextExplanation = false
        state.update {
            it.copy(
                isUnderstanding = true,
                transcript = text.trim(),
                followUp = null,
                readback = null,
                voiceProblem = null,
            )
        }
        viewModelScope.launch {
            when (val result = understandGoal(conversation.toList())) {
                is AppResult.Success -> applyUnderstanding(result.value)

                is AppResult.Failure -> state.update {
                    it.copy(isUnderstanding = false, voiceProblem = VoiceProblem.FAILED)
                }
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
                val readback = GoalReadback(understanding.goalName, understanding.target, understanding.targetDate)
                conversation += ConversationTurn(Speaker.GULLAK, modelContext(readback))
                state.update {
                    it.copy(
                        readback = readback,
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
        when (val result = buildPlan(PlanInput(target = amount, targetDate = date, lumpSum = current.lumpSum))) {
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
            current.lumpSum >= amount -> InputProblem.LUMP_SUM_TOO_HIGH
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
            if (speakNextExplanation) {
                speakNextExplanation = false
                speak(text)
            }
        }
    }

    /** Back from approval goes to the bank step; back from the bank step returns to the form. */
    private fun stepBack() {
        state.update {
            when (it.stage) {
                Stage.APPROVAL -> it.copy(stage = Stage.BANK, approvalDigits = 0)
                Stage.BANK -> it.copy(stage = Stage.EDITING)
                else -> it
            }
        }
    }

    private fun enterDigit() {
        val current = state.value
        if (current.stage != Stage.APPROVAL || current.approvalDigits >= CreateGoalUiState.APPROVAL_LENGTH) return
        state.update { it.copy(approvalDigits = it.approvalDigits + 1) }
        if (state.value.canConfirm) confirm()
    }

    private fun confirm() {
        val current = state.value
        val amount = current.amount ?: return
        val date = current.targetDate ?: return
        if (!current.canConfirm) return
        state.update { it.copy(stage = Stage.PROCESSING, approvalDigits = 0) }
        viewModelScope.launch {
            val request = NewGoalRequest(
                name = current.name.trim(),
                target = amount,
                targetDate = date,
                dateIsFixed = current.dateIsFixed,
                frequency = current.frequency,
                lumpSum = current.lumpSum,
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
        FollowUpReason.WHAT_TO_CHANGE -> "What should I change?"
        FollowUpReason.MISSING_NAME -> "What are you saving for?"
        FollowUpReason.MISSING_AMOUNT -> "How much do you need?"
        FollowUpReason.MISSING_DATE -> "By when do you need it?"
        FollowUpReason.AMOUNT_OUT_OF_RANGE -> "Please give an amount between 500 and 50 lakh rupees."
        FollowUpReason.DATE_NOT_IN_FUTURE -> "Please give a date after today."
        FollowUpReason.DATE_TOO_FAR -> "Please give a date within 10 years."
    }

    private fun modelContext(readback: GoalReadback): String =
        "${readback.name}, ${readback.target.paise / Money.PAISE_PER_RUPEE} rupees, by ${readback.date}. Is that right?"

    private fun heard(text: String) {
        if (state.value.readback == null) understand(text) else replyToReadback(text)
    }

    private fun replyToReadback(text: String) {
        when (readbackReply(text)) {
            ReadbackReply.YES -> {
                state.update { it.copy(readback = null, transcript = text.trim()) }
                val explanation = state.value.explanation
                when {
                    !state.value.voiceReplies -> Unit
                    explanation != null -> speak(explanation)
                    else -> speakNextExplanation = true
                }
            }

            ReadbackReply.NO -> {
                conversation += ConversationTurn(Speaker.USER, text.trim())
                conversation += ConversationTurn(Speaker.GULLAK, modelContext(FollowUpReason.WHAT_TO_CHANGE))
                announcedFollowUp = null
                state.update {
                    it.copy(
                        readback = null,
                        transcript = text.trim(),
                        followUp = FollowUpQuestion.Standard(FollowUpReason.WHAT_TO_CHANGE),
                    )
                }
            }

            ReadbackReply.CORRECTION -> understand(text)
        }
    }

    private fun speak(text: String) {
        speakJob?.cancel()
        speakJob = viewModelScope.launch { sayAloud(text) }
    }

    private suspend fun sayAloud(text: String) {
        val language = state.value.language
        if (language != GoalLanguage.ENGLISH && !voiceHintShown && !voice.isVoiceAvailable(language)) {
            voiceHintShown = true
            state.update { it.copy(showVoiceHint = true) }
            settings.setVoiceHintShown()
        }
        voice.speak(text, language)
    }

    /** Runs once per question, so a screen rotation neither repeats it nor listens twice. */
    private fun announceFollowUp(text: String) {
        val question = state.value.followUp ?: return
        if (question == announcedFollowUp) return
        announcedFollowUp = question
        askAloud(text)
    }

    /** Runs once per read-back, for the same reason. With voice replies off, the read-back is only on screen. */
    private fun announceReadback(text: String) {
        val readback = state.value.readback ?: return
        if (readback == announcedReadback) return
        announcedReadback = readback
        if (state.value.voiceReplies) askAloud(text)
    }

    private fun askAloud(text: String) {
        speakJob?.cancel()
        speakJob = viewModelScope.launch {
            // A text-to-speech engine that never reports the end must not stop the mic from listening.
            if (state.value.voiceReplies) withTimeoutOrNull(FOLLOW_UP_SPEECH_LIMIT_MS) { sayAloud(text) }
            startListening()
        }
    }

    private fun startListening() {
        listenJob?.cancel()
        state.update { it.copy(isListening = true, partialTranscript = null, micLevel = 0f, voiceProblem = null) }
        listenJob = viewModelScope.launch {
            speech.listen(state.value.speechLanguageTag).collect(::onSpeech)
            stopListeningState()
        }
    }

    private fun onSpeech(event: SpeechEvent) {
        when (event) {
            SpeechEvent.Listening -> Unit

            is SpeechEvent.Level -> state.update { it.copy(micLevel = event.level) }

            is SpeechEvent.Partial -> state.update { it.copy(partialTranscript = event.text) }

            is SpeechEvent.Final -> {
                stopListeningState()
                heard(event.text)
            }

            is SpeechEvent.Error -> {
                stopListeningState()
                when (event.type) {
                    // Silence after a read-back just leaves the form as it is.
                    SpeechError.NO_SPEECH -> if (state.value.readback == null) {
                        state.update { it.copy(voiceProblem = VoiceProblem.NOT_HEARD) }
                    }

                    SpeechError.PERMISSION -> state.update { it.copy(voiceProblem = VoiceProblem.MIC_DENIED) }

                    SpeechError.FAILED -> state.update { it.copy(voiceProblem = VoiceProblem.FAILED) }

                    SpeechError.UNAVAILABLE -> send(CreateGoalEffect.OpenPhoneSpeech)
                }
            }
        }
    }

    private fun stopListeningState() {
        state.update { it.copy(isListening = false, partialTranscript = null, micLevel = 0f) }
    }

    private fun stopVoice() {
        speakJob?.cancel()
        listenJob?.cancel()
        voice.stop()
        stopListeningState()
    }

    override fun onCleared() {
        voice.stop()
    }

    private fun send(effect: CreateGoalEffect) {
        viewModelScope.launch { effects.send(effect) }
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_AMOUNT = "amount"
        const val KEY_LUMP_SUM = "lump_sum"
        const val KEY_DATE = "date"
        const val KEY_FIXED = "date_is_fixed"
        val MIN_AMOUNT = Money.fromRupees(500)
        val MAX_AMOUNT = Money.fromRupees(50_00_000)
        const val MAX_DIGITS = 8
        const val EXPLAIN_DEBOUNCE_MS = 400L
        const val FOLLOW_UP_SPEECH_LIMIT_MS = 15_000L
    }
}
