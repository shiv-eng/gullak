package com.gullak.feature.creategoal.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gullak.core.domain.ai.FollowUpQuestion
import com.gullak.core.domain.ai.FollowUpReason
import com.gullak.core.domain.ai.PlanExplanationService
import com.gullak.core.domain.ai.SpeechError
import com.gullak.core.domain.ai.SpeechEvent
import com.gullak.core.domain.ai.SpeechInput
import com.gullak.core.domain.ai.Understanding
import com.gullak.core.domain.ai.VoiceOutput
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
import com.gullak.feature.creategoal.DemoBankAccount
import com.gullak.feature.creategoal.ReadbackReply
import com.gullak.feature.creategoal.readbackReply
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What Gullak is doing right now, shown by the orb. */
enum class TalkPhase { WAITING, SPEAKING, LISTENING, THINKING }

/** Where the conversation has got to. */
enum class TalkStep { GOAL, CONFIRM_GOAL, CHOOSE_PLAN, CONFIRM_AUTOPAY, DONE }

data class VoiceGoalUiState(
    val phase: TalkPhase = TalkPhase.WAITING,
    val step: TalkStep = TalkStep.GOAL,
    /** What Gullak last said, as a caption. */
    val gullakSaid: String? = null,
    /** What the user last said, or is saying. */
    val youSaid: String? = null,
    val micLevel: Float = 0f,
    val micDenied: Boolean = false,
    val name: String? = null,
    val target: Money? = null,
    val date: LocalDate? = null,
    val plan: Plan? = null,
    val frequency: Frequency? = null,
    val bank: DemoBankAccount = DemoBankAccount.SBI_SAVINGS,
    val createdGoal: Goal? = null,
) {
    val chosen: PlanOption? get() = frequency?.let { plan?.option(it) }
}

sealed interface VoiceGoalEvent {
    /** The microphone can be used: the conversation starts, if it has not. */
    data object MicReady : VoiceGoalEvent

    data object MicDenied : VoiceGoalEvent

    /** The orb: interrupts Gullak, stops listening, or starts listening. */
    data object OrbTapped : VoiceGoalEvent

    /** Text from the phone's own speech screen, on phones that cannot recognise speech inside the app. */
    data class PhoneSpeechHeard(val text: String) : VoiceGoalEvent

    data object PhoneSpeechFailed : VoiceGoalEvent

    data object ViewGoalsClicked : VoiceGoalEvent

    data object DoneClicked : VoiceGoalEvent
}

sealed interface VoiceGoalEffect {
    data object Finish : VoiceGoalEffect

    data object OpenGoals : VoiceGoalEffect

    data object OpenPhoneSpeech : VoiceGoalEffect
}

/**
 * The whole Gullak flow as a conversation. The AI only hears and reads what was said; every amount, date and
 * option comes from the plan engine, the lines from string resources, and yes, no and plan choices are read by
 * code. Saying yes to AutoPay is the demo's approval, and Gullak says aloud that no real money moves.
 */
@HiltViewModel
class VoiceGoalViewModel @Inject internal constructor(
    private val understandGoal: UnderstandGoalUseCase,
    private val buildPlan: BuildPlanUseCase,
    private val createGoal: CreateGoalUseCase,
    private val explainer: PlanExplanationService,
    private val voice: VoiceOutput,
    private val speech: SpeechInput,
    private val settings: SettingsRepository,
    private val lines: VoiceLines,
) : ViewModel() {

    private val state = MutableStateFlow(VoiceGoalUiState())
    val uiState: StateFlow<VoiceGoalUiState> = state.asStateFlow()

    private val effects = Channel<VoiceGoalEffect>(Channel.BUFFERED)
    val effect: Flow<VoiceGoalEffect> = effects.receiveAsFlow()

    private val conversation = mutableListOf<ConversationTurn>()
    private var language: GoalLanguage = lines.defaultLanguage()
    private var talk: Job? = null
    private var started = false
    private var phoneReply: CompletableDeferred<String?>? = null
    private var silences = 0

    fun onEvent(event: VoiceGoalEvent) {
        when (event) {
            VoiceGoalEvent.MicReady -> if (!started) {
                started = true
                state.update { it.copy(micDenied = false) }
                talkFrom(lines.greeting(language))
            }

            VoiceGoalEvent.MicDenied -> state.update { it.copy(micDenied = true) }

            VoiceGoalEvent.OrbTapped -> onOrbTapped()

            is VoiceGoalEvent.PhoneSpeechHeard -> phoneReply?.complete(event.text)

            VoiceGoalEvent.PhoneSpeechFailed -> phoneReply?.complete(null)

            VoiceGoalEvent.ViewGoalsClicked -> stopTalking(VoiceGoalEffect.OpenGoals)

            VoiceGoalEvent.DoneClicked -> stopTalking(VoiceGoalEffect.Finish)
        }
    }

    private fun onOrbTapped() {
        val phase = state.value.phase
        if (phase == TalkPhase.THINKING) return
        talk?.cancel()
        voice.stop()
        if (phase == TalkPhase.LISTENING) {
            state.update { it.copy(phase = TalkPhase.WAITING, micLevel = 0f) }
        } else {
            silences = 0
            talkFrom(null)
        }
    }

    private fun stopTalking(effect: VoiceGoalEffect) {
        talk?.cancel()
        voice.stop()
        effects.trySend(effect)
    }

    /** Says a line, listens, answers, and so on until there is nothing more to ask. */
    private fun talkFrom(first: String?) {
        talk = viewModelScope.launch {
            var line = first
            do {
                line?.let { say(it) }
                val heard = listen()
                line = if (heard == null) afterSilence() else respond(heard)
            } while (line != null)
            state.update { it.copy(phase = TalkPhase.WAITING, micLevel = 0f) }
        }
    }

    private suspend fun say(line: String) {
        state.update { it.copy(phase = TalkPhase.SPEAKING, gullakSaid = line, micLevel = 0f) }
        if (settings.settings.first().voiceReplies) {
            // A voice engine that never reports the end must not stop the conversation.
            withTimeoutOrNull(SPEECH_LIMIT_MS) { voice.speak(line, language) }
        }
    }

    /** The user's words, or null if nothing was heard. */
    private suspend fun listen(): String? {
        state.update { it.copy(phase = TalkPhase.LISTENING, micLevel = 0f) }
        var heard: String? = null
        var useTheScreen = false
        val tag = settings.settings.first().speechLanguage.tag
        speech.listen(tag).collect { event ->
            when (event) {
                SpeechEvent.Listening -> Unit

                is SpeechEvent.Level -> state.update { it.copy(micLevel = event.level) }

                is SpeechEvent.Partial -> state.update { it.copy(youSaid = event.text) }

                is SpeechEvent.Final -> heard = event.text

                is SpeechEvent.Error -> when (event.type) {
                    SpeechError.PERMISSION -> state.update { it.copy(micDenied = true) }
                    SpeechError.UNAVAILABLE -> useTheScreen = true
                    SpeechError.NO_SPEECH, SpeechError.FAILED -> Unit
                }
            }
        }
        if (useTheScreen) {
            val reply = CompletableDeferred<String?>()
            phoneReply = reply
            effects.trySend(VoiceGoalEffect.OpenPhoneSpeech)
            heard = reply.await()
        }
        heard?.let { text -> state.update { it.copy(youSaid = text) } }
        return heard?.takeIf { it.isNotBlank() }
    }

    /** One nudge, then Gullak waits for a tap on the orb, as a person would stop talking to an empty room. */
    private fun afterSilence(): String? {
        silences++
        return if (silences == 1 && state.value.step != TalkStep.DONE) lines.didNotHear(language) else null
    }

    private suspend fun respond(text: String): String? {
        silences = 0
        return when (state.value.step) {
            TalkStep.GOAL -> understand(text)
            TalkStep.CONFIRM_GOAL -> respondToReadback(text)
            TalkStep.CHOOSE_PLAN -> respondToPlan(text)
            TalkStep.CONFIRM_AUTOPAY -> respondToAutoPay(text)
            TalkStep.DONE -> respondAfterDone(text)
        }
    }

    private suspend fun understand(text: String): String {
        conversation += ConversationTurn(Speaker.USER, text.trim())
        state.update { it.copy(phase = TalkPhase.THINKING) }
        // A person says "one moment" only if it takes a moment.
        val understanding = viewModelScope.async { understandGoal(conversation.toList()) }
        withTimeoutOrNull(ACK_AFTER_MS) { understanding.join() } ?: run {
            say(lines.moment(language))
            state.update { it.copy(phase = TalkPhase.THINKING) }
        }
        return when (val result = understanding.await()) {
            is AppResult.Failure -> lines.didNotUnderstand(language)
            is AppResult.Success -> onUnderstood(result.value)
        }
    }

    private fun onUnderstood(understanding: Understanding): String {
        val parsed = understanding.parsed
        language = parsed.language
        state.update {
            it.copy(
                name = parsed.goalName ?: it.name,
                target = parsed.targetAmount ?: it.target,
                date = parsed.targetDate ?: it.date,
            )
        }
        return when (understanding) {
            is Understanding.NeedsFollowUp -> ask(understanding.question)

            is Understanding.ReadyToConfirm -> {
                state.update {
                    it.copy(
                        step = TalkStep.CONFIRM_GOAL,
                        name = understanding.goalName,
                        target = understanding.target,
                        date = understanding.targetDate,
                    )
                }
                lines.readback(language, understanding.goalName, understanding.target, understanding.targetDate)
                    .also { conversation += ConversationTurn(Speaker.GULLAK, it) }
            }
        }
    }

    private fun ask(question: FollowUpQuestion): String {
        state.update { it.copy(step = TalkStep.GOAL) }
        val text = when (question) {
            is FollowUpQuestion.FromModel -> question.text
            is FollowUpQuestion.Standard -> lines.followUp(language, question.reason)
        }
        conversation += ConversationTurn(Speaker.GULLAK, text)
        return text
    }

    private suspend fun respondToReadback(text: String): String = when (readbackReply(text)) {
        ReadbackReply.YES -> presentPlan()
        ReadbackReply.NO -> ask(FollowUpQuestion.Standard(FollowUpReason.WHAT_TO_CHANGE))
        ReadbackReply.CORRECTION -> understand(text)
    }

    private fun presentPlan(): String {
        val current = state.value
        val target = current.target
        val date = current.date
        if (target == null || date == null) return ask(FollowUpQuestion.Standard(FollowUpReason.MISSING_AMOUNT))
        val plan = when (val result = buildPlan(PlanInput(target = target, targetDate = date))) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return dateTooClose()
        }
        if (plan.oneTimeOnly || plan.options.isEmpty()) return dateTooClose()
        state.update { it.copy(step = TalkStep.CHOOSE_PLAN, plan = plan, frequency = null) }
        return lines.plan(language, current.name.orEmpty(), plan.options)
    }

    private fun dateTooClose(): String {
        state.update { it.copy(step = TalkStep.GOAL, date = null) }
        return lines.dateTooClose(language).also { conversation += ConversationTurn(Speaker.GULLAK, it) }
    }

    private suspend fun respondToPlan(text: String): String {
        val plan = state.value.plan ?: return presentPlan()
        val picked = optionFrom(text, plan.options)
        val reply = readbackReply(text)
        return when {
            picked != null -> offerAutoPay(picked)

            // "Theek hai" with no choice takes the plan's last option, which is the fewest payments.
            reply == ReadbackReply.YES -> offerAutoPay(plan.options.last())

            reply == ReadbackReply.NO -> ask(FollowUpQuestion.Standard(FollowUpReason.WHAT_TO_CHANGE))

            text.any(Char::isDigit) -> understand(text)

            else -> lines.didNotCatchChoice(language)
        }
    }

    private suspend fun offerAutoPay(option: PlanOption): String {
        val current = state.value
        val plan = current.plan ?: return presentPlan()
        state.update {
            it.copy(step = TalkStep.CONFIRM_AUTOPAY, frequency = option.frequency, phase = TalkPhase.THINKING)
        }
        val why = explainer.explain(current.name.orEmpty(), plan, option, language)
        return "$why ${lines.autoPay(language, option, current.bank)}"
    }

    private suspend fun respondToAutoPay(text: String): String {
        val current = state.value
        val plan = current.plan ?: return presentPlan()
        val bank = bankFrom(text)
        if (bank != null) state.update { it.copy(bank = bank) }
        val reply = readbackReply(text)
        val otherOption = optionFrom(text, plan.options)?.takeIf { it.frequency != current.frequency }
        return when {
            otherOption != null -> offerAutoPay(otherOption)
            reply == ReadbackReply.NO -> ask(FollowUpQuestion.Standard(FollowUpReason.WHAT_TO_CHANGE))
            reply == ReadbackReply.YES || bank != null -> startGullak()
            text.any(Char::isDigit) -> understand(text)
            else -> lines.didNotCatchYes(language)
        }
    }

    private suspend fun startGullak(): String {
        val current = state.value
        val name = current.name
        val target = current.target
        val date = current.date
        if (name == null || target == null || date == null) return lines.couldNotStart(language)
        state.update { it.copy(phase = TalkPhase.THINKING) }
        val request = NewGoalRequest(
            name = name.trim(),
            target = target,
            targetDate = date,
            dateIsFixed = false,
            frequency = current.frequency,
            language = language,
        )
        return when (val result = createGoal(request)) {
            is AppResult.Failure -> lines.couldNotStart(language)

            is AppResult.Success -> {
                state.update { it.copy(step = TalkStep.DONE, createdGoal = result.value) }
                val first = current.chosen?.firstDebit
                lines.started(language, name, first)
            }
        }
    }

    private suspend fun respondAfterDone(text: String): String? {
        if (wantsAnother(text)) {
            conversation.clear()
            state.value = VoiceGoalUiState()
            return lines.greetingAgain(language)
        }
        say(lines.goodbye(language))
        effects.trySend(VoiceGoalEffect.Finish)
        return null
    }

    override fun onCleared() {
        voice.stop()
    }

    private companion object {
        const val SPEECH_LIMIT_MS = 45_000L
        const val ACK_AFTER_MS = 1_200L
    }
}
