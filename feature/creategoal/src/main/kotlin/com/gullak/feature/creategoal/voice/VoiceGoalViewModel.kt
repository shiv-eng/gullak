package com.gullak.feature.creategoal.voice

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gullak.core.domain.ai.BargeInDetector
import com.gullak.core.domain.ai.BrainAnswer
import com.gullak.core.domain.ai.ConversationBrain
import com.gullak.core.domain.ai.FollowUpQuestion
import com.gullak.core.domain.ai.FollowUpReason
import com.gullak.core.domain.ai.PlanExplanationService
import com.gullak.core.domain.ai.ReadbackReply
import com.gullak.core.domain.ai.SpeechError
import com.gullak.core.domain.ai.SpeechEvent
import com.gullak.core.domain.ai.SpeechInput
import com.gullak.core.domain.ai.TalkIntent
import com.gullak.core.domain.ai.Understanding
import com.gullak.core.domain.ai.VoiceOutput
import com.gullak.core.domain.ai.readbackReply
import com.gullak.core.domain.ai.speakUnlessInterrupted
import com.gullak.core.domain.analytics.Analytics
import com.gullak.core.domain.analytics.AnalyticsEvent
import com.gullak.core.domain.plan.PlanInput
import com.gullak.core.domain.repository.SettingsRepository
import com.gullak.core.domain.usecase.BuildPlanUseCase
import com.gullak.core.domain.usecase.CreateGoalUseCase
import com.gullak.core.domain.usecase.NewGoalRequest
import com.gullak.core.domain.usecase.ObserveGoalsUseCase
import com.gullak.core.domain.usecase.UnderstandGoalUseCase
import com.gullak.core.model.ai.ConversationTurn
import com.gullak.core.model.ai.GoalLanguage
import com.gullak.core.model.ai.ParsedGoal
import com.gullak.core.model.ai.Speaker
import com.gullak.core.model.goal.Frequency
import com.gullak.core.model.goal.Goal
import com.gullak.core.model.money.Money
import com.gullak.core.model.plan.Plan
import com.gullak.core.model.plan.PlanOption
import com.gullak.core.model.result.AppResult
import com.gullak.feature.creategoal.DemoBankAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Deferred
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

/** How Gullak speaks and hears, the settings for it, and the record of it. */
internal class TalkIo @Inject constructor(
    val voice: VoiceOutput,
    val speech: SpeechInput,
    val settings: SettingsRepository,
    val bargeIn: BargeInDetector,
    val analytics: Analytics,
)

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
    /** A one-time payment now; the plan covers what is left. */
    val oneTime: Money = Money.ZERO,
    val dateIsFixed: Boolean = false,
    /** The phone has no voice for the language being spoken. */
    val showVoiceHint: Boolean = false,
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

    data object VoiceHintDismissed : VoiceGoalEvent
}

sealed interface VoiceGoalEffect {
    data object Finish : VoiceGoalEffect

    data object OpenGoals : VoiceGoalEffect

    data object OpenPhoneSpeech : VoiceGoalEffect
}

/**
 * The whole Gullak flow as a conversation. Each turn the model reads what was said and reacts like a friend, but never
 * with a number: every amount, date and option comes from the plan engine and the lines from string resources. If the
 * model cannot be reached, plain rules read the turn instead. Starting a Gullak needs a yes that code itself reads
 * from the user's words, and Gullak says aloud that no real money moves in this demo.
 */
@HiltViewModel
class VoiceGoalViewModel @Inject internal constructor(
    private val understandGoal: UnderstandGoalUseCase,
    private val brain: ConversationBrain,
    private val observeGoals: ObserveGoalsUseCase,
    private val clock: Clock,
    private val buildPlan: BuildPlanUseCase,
    private val createGoal: CreateGoalUseCase,
    private val explainer: PlanExplanationService,
    io: TalkIo,
    private val lines: VoiceLines,
) : ViewModel() {

    private val voice: VoiceOutput = io.voice
    private val speech: SpeechInput = io.speech
    private val settings: SettingsRepository = io.settings
    private val bargeIn: BargeInDetector = io.bargeIn
    private val analytics: Analytics = io.analytics

    /** A failure in the conversation ends the turn, not the app: Gullak waits for a tap on the orb. */
    private val conversationFailed = CoroutineExceptionHandler { _, error ->
        Log.e(TAG, "Conversation stopped", error)
        state.update { it.copy(phase = TalkPhase.WAITING, micLevel = 0f) }
    }

    /** Times the voice was stopped by a noise, not by the user. Twice, and this conversation stops listening for it. */
    private var falseInterruptions = 0
    private var voiceHintChecked = false

    private val state = MutableStateFlow(VoiceGoalUiState())
    val uiState: StateFlow<VoiceGoalUiState> = state.asStateFlow()

    private val effects = Channel<VoiceGoalEffect>(Channel.BUFFERED)
    val effect: Flow<VoiceGoalEffect> = effects.receiveAsFlow()

    private val conversation = mutableListOf<ConversationTurn>()
    private var language: GoalLanguage = lines.defaultLanguage()
    private var goalLanguage: GoalLanguage = language
    private var talk: Job? = null
    private var started = false
    private var phoneReply: CompletableDeferred<String?>? = null
    private var silences = 0

    fun onEvent(event: VoiceGoalEvent) {
        when (event) {
            VoiceGoalEvent.MicReady -> if (!started) {
                started = true
                state.update { it.copy(micDenied = false) }
                talk = viewModelScope.launch(conversationFailed) { converse(greeting()) }
            }

            VoiceGoalEvent.MicDenied -> state.update { it.copy(micDenied = true) }

            VoiceGoalEvent.OrbTapped -> onOrbTapped()

            is VoiceGoalEvent.PhoneSpeechHeard -> phoneReply?.complete(event.text)

            VoiceGoalEvent.PhoneSpeechFailed -> phoneReply?.complete(null)

            VoiceGoalEvent.ViewGoalsClicked -> stopTalking(VoiceGoalEffect.OpenGoals)

            VoiceGoalEvent.DoneClicked -> stopTalking(VoiceGoalEffect.Finish)

            VoiceGoalEvent.VoiceHintDismissed -> state.update { it.copy(showVoiceHint = false) }
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

    private fun talkFrom(first: String?) {
        talk = viewModelScope.launch(conversationFailed) { converse(first) }
    }

    /** Says a line, listens, answers, and so on until there is nothing more to ask. */
    private suspend fun converse(first: String?) {
        var line = first
        do {
            val interrupted = line?.takeIf { it.isNotEmpty() }?.let { say(it) } ?: false
            val heard = listen()
            line = when {
                heard != null -> respond(heard)

                // It stopped, but nobody spoke: a noise did it. Say the line again.
                interrupted -> {
                    falseInterruptions++
                    line
                }

                else -> afterSilence()
            }
        } while (line != null)
        state.update { it.copy(phase = TalkPhase.WAITING, micLevel = 0f) }
    }

    /** Greets a returning saver with how far they have come. */
    private suspend fun greeting(): String {
        val best = observeGoals().first().goals.filter { it.saved.isPositive }.maxByOrNull { it.saved.paise }
        return if (best == null) {
            lines.greeting(language)
        } else {
            lines.greetingWithProgress(language, best.goal.name, best.saved)
        }
    }

    /** True if the user talked over it. */
    private suspend fun say(
        line: String,
        canBeInterrupted: Boolean = true,
    ): Boolean {
        state.update { it.copy(phase = TalkPhase.SPEAKING, gullakSaid = line, micLevel = 0f) }
        val prefs = settings.settings.first()
        if (!prefs.voiceReplies) return false
        checkVoice(prefs.voiceHintShown)
        // A voice engine that never reports the end must not stop the conversation.
        return withTimeoutOrNull(SPEECH_LIMIT_MS) {
            if (canBeInterrupted && prefs.interruptBySpeaking && falseInterruptions < MAX_FALSE_INTERRUPTIONS) {
                voice.speakUnlessInterrupted(line, language, bargeIn)
            } else {
                voice.speak(line, language)
                false
            }
        } ?: false
    }

    /** A missing Hindi voice is explained once, and only if the phone's own voice would be the one speaking. */
    private suspend fun checkVoice(alreadyShown: Boolean) {
        if (alreadyShown || voiceHintChecked || language == GoalLanguage.ENGLISH) return
        voiceHintChecked = true
        if (!voice.isVoiceAvailable(language)) {
            state.update { it.copy(showVoiceHint = true) }
            settings.setVoiceHintShown()
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

    /**
     * The phone's recogniser gives up after a few seconds, too quick for someone thinking: it listens again quietly,
     * then gives one nudge, then Gullak waits for a tap on the orb, as a person would stop talking to an empty room.
     */
    private fun afterSilence(): String? {
        silences++
        return when {
            state.value.step == TalkStep.DONE -> null
            silences == 1 -> ""
            silences == 2 -> lines.didNotHear(language)
            else -> null
        }
    }

    private suspend fun respond(text: String): String? {
        silences = 0
        analytics.log(AnalyticsEvent.VoiceUsed)
        if (saysDateIsFixed(text)) state.update { it.copy(dateIsFixed = true) }
        conversation += ConversationTurn(Speaker.USER, text.trim())
        val heard = thinking { brain.hear(conversation.toList(), situation(), LocalDate.now(clock)) }
        return when (heard) {
            is AppResult.Success -> respondToBrain(text, heard.value)
            is AppResult.Failure -> respondByRules(text)
        }
    }

    /** A person says "one moment" only if it takes a moment. */
    private suspend fun <T> thinking(work: suspend () -> T): T {
        state.update { it.copy(phase = TalkPhase.THINKING) }
        val result: Deferred<T> = viewModelScope.async { work() }
        withTimeoutOrNull(ACK_AFTER_MS) { result.join() } ?: run {
            say(lines.moment(language), canBeInterrupted = false)
            state.update { it.copy(phase = TalkPhase.THINKING) }
        }
        return result.await()
    }

    /** Where the conversation is, in plain words for the model. */
    private fun situation(): String {
        val current = state.value
        val agreed = "Goal so far: name=${current.name}, amount in rupees=" +
            "${current.target?.let { it.paise / Money.PAISE_PER_RUPEE }}, date=${current.date}."
        val asked = when (current.step) {
            TalkStep.GOAL -> "You asked what they are saving for, how much, and by when."
            TalkStep.CONFIRM_GOAL -> "You read the goal back and asked if it is right."
            TalkStep.CHOOSE_PLAN -> "You offered saving daily, weekly or monthly and asked which they want."
            TalkStep.CONFIRM_AUTOPAY -> "You asked if you should start AutoPay. This is a demo: no real money moves."
            TalkStep.DONE -> "Their Gullak has started and you asked if there is anything else."
        }
        return "$asked $agreed"
    }

    private suspend fun respondToBrain(
        text: String,
        heard: BrainAnswer,
    ): String? {
        adoptLanguage(heard.goal.language)
        heard.oneTime?.let { oneTime -> state.update { it.copy(oneTime = oneTime) } }
        if (heard.dateFixed) state.update { it.copy(dateIsFixed = true) }
        val next = nextLine(text, heard) ?: return null
        return listOfNotNull(heard.say, next).joinToString(" ").also { line ->
            conversation += ConversationTurn(Speaker.GULLAK, line)
        }
    }

    /** Null means the conversation is over. */
    private suspend fun nextLine(
        text: String,
        heard: BrainAnswer,
    ): String? = when (heard.intent) {
        // Extra money while setting up a goal is the one-time payment, which is already noted.
        TalkIntent.GOAL_DETAILS, TalkIntent.ADD_MONEY -> onUnderstood(understandGoal.check(withWhatWeKnow(heard.goal)))

        TalkIntent.QUESTION, TalkIntent.CHECK_SAVINGS, TalkIntent.WHERE_MONEY_GOES,
        TalkIntent.PAY_OUT, TalkIntent.ASK_TOP_UP,
        -> answerQuestion(heard.intent, text)

        TalkIntent.NO -> if (state.value.step == TalkStep.DONE) {
            finish()
        } else {
            ask(FollowUpQuestion.Standard(FollowUpReason.WHAT_TO_CHANGE))
        }

        TalkIntent.CHOOSE_PLAN ->
            state.value.plan?.options
                ?.firstOrNull { it.frequency == heard.frequency }
                ?.let { offerAutoPay(it) }
                ?: currentQuestion()

        TalkIntent.ANOTHER_GOAL -> startAnother().let { again ->
            // "One more, for a bike": carry straight on with what they just said.
            if (heard.goal.goalName == null) again else onUnderstood(understandGoal.check(heard.goal))
        }

        TalkIntent.FINISHED -> finish()

        // Yes is decided by step, and at AutoPay by the rules alone, from the user's own words.
        TalkIntent.YES, TalkIntent.UNCLEAR -> respondByRules(text)
    }

    /** A side question: answered, then back to what was being asked. */
    private suspend fun answerQuestion(
        intent: TalkIntent,
        text: String,
    ): String = when (intent) {
        TalkIntent.WHERE_MONEY_GOES -> whereMoneyGoes(text)
        TalkIntent.CHECK_SAVINGS -> "${savingsSoFar()} ${currentQuestion()}"
        TalkIntent.PAY_OUT, TalkIntent.ASK_TOP_UP -> "${lines.onTheGoalPage(language)} ${currentQuestion()}"
        else -> currentQuestion()
    }

    /** The amounts are from the plan engine, whatever the model thought. */
    private suspend fun savingsSoFar(): String {
        val overview = observeGoals().first()
        val best = overview.goals.filter { it.saved.isPositive }.maxByOrNull { it.saved.paise }
        return if (best == null) {
            lines.noSavingsYet(language)
        } else {
            lines.savings(language, overview.totalSaved, best.goal.name, best.saved)
        }
    }

    private fun withWhatWeKnow(goal: ParsedGoal): ParsedGoal {
        val current = state.value
        return goal.copy(
            goalName = goal.goalName ?: current.name,
            targetAmount = goal.targetAmount ?: current.target,
            targetDate = goal.targetDate ?: current.date,
        )
    }

    /** After a side question, the talk goes back to what was being asked. */
    private fun currentQuestion(): String {
        val current = state.value
        val name = current.name
        val target = current.target
        val date = current.date
        return when (current.step) {
            TalkStep.GOAL -> when {
                name == null -> lines.followUp(language, FollowUpReason.MISSING_NAME)
                target == null -> lines.followUp(language, FollowUpReason.MISSING_AMOUNT)
                else -> lines.followUp(language, FollowUpReason.MISSING_DATE)
            }

            TalkStep.CONFIRM_GOAL -> if (name != null && target != null && date != null) {
                lines.readback(language, name, target, date)
            } else {
                lines.followUp(language, FollowUpReason.WHAT_TO_CHANGE)
            }

            TalkStep.CHOOSE_PLAN -> lines.didNotCatchChoice(language)

            TalkStep.CONFIRM_AUTOPAY -> lines.didNotCatchYes(language)

            TalkStep.DONE -> lines.anythingElse(language)
        }
    }

    private fun startAnother(): String {
        conversation.clear()
        state.value = VoiceGoalUiState()
        return lines.greetingAgain(language)
    }

    private suspend fun finish(): String? {
        say(lines.goodbye(language), canBeInterrupted = false)
        effects.trySend(VoiceGoalEffect.Finish)
        return null
    }

    /** The conversation is held in Hindi or English: spoken Hinglish is said best in Devanagari by the voices. */
    private fun adoptLanguage(heard: GoalLanguage) {
        goalLanguage = heard
        language = if (heard == GoalLanguage.ENGLISH) GoalLanguage.ENGLISH else GoalLanguage.HINDI
    }

    /** A long silence is worse than a plainer sentence: if the model is slow, the caller says its own checked line. */
    private suspend fun explainDestination(
        name: String,
        plan: Plan,
        asked: String?,
    ): String? = withTimeoutOrNull(EXPLAIN_LIMIT_MS) { explainer.explainDestination(name, plan, asked, language) }

    /** The honest answer on gold or a fund: the engine decides, the model explains it, and its numbers are checked. */
    private suspend fun whereMoneyGoes(asked: String): String {
        val current = state.value
        val target = current.target
        val date = current.date
        val plan = current.plan ?: if (target != null && date != null) {
            (buildPlan(PlanInput(target = target, targetDate = date)) as? AppResult.Success)?.value
        } else {
            null
        }
        val answer = if (plan == null) {
            lines.whereMoneyGoesInGeneral(language)
        } else {
            thinking { explainDestination(current.name.orEmpty(), plan, asked) }
                ?: lines.whereMoneyGoes(language, plan.destination, plan.monthsToGoal)
        }
        return "$answer ${currentQuestion()}"
    }

    private suspend fun respondByRules(text: String): String? {
        if (asksWhereMoneyGoes(text) && state.value.step != TalkStep.DONE) return whereMoneyGoes(text)
        return respondByStep(text)
    }

    private suspend fun respondByStep(text: String): String? = when (state.value.step) {
        TalkStep.GOAL -> understand()
        TalkStep.CONFIRM_GOAL -> respondToReadback(text)
        TalkStep.CHOOSE_PLAN -> respondToPlan(text)
        TalkStep.CONFIRM_AUTOPAY -> respondToAutoPay(text)
        TalkStep.DONE -> respondAfterDone(text)
    }

    private suspend fun understand(): String = when (val result = thinking { understandGoal(conversation.toList()) }) {
        is AppResult.Failure -> lines.didNotUnderstand(language)
        is AppResult.Success -> onUnderstood(result.value)
    }

    private fun onUnderstood(understanding: Understanding): String {
        val parsed = understanding.parsed
        adoptLanguage(parsed.language)
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
        ReadbackReply.CORRECTION -> understand()
    }

    private suspend fun presentPlan(): String {
        val current = state.value
        val target = current.target
        val date = current.date
        if (target == null || date == null) return ask(FollowUpQuestion.Standard(FollowUpReason.MISSING_AMOUNT))
        // More than the goal is not a one-time payment towards it, so that is left out rather than guessed at.
        val oneTime = current.oneTime.takeIf { it < target } ?: Money.ZERO
        state.update { it.copy(oneTime = oneTime) }
        val plan = when (val result = buildPlan(PlanInput(target = target, targetDate = date, lumpSum = oneTime))) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> return dateTooClose()
        }
        if (plan.oneTimeOnly || plan.options.isEmpty()) return dateTooClose()
        state.update { it.copy(step = TalkStep.CHOOSE_PLAN, plan = plan, frequency = null) }
        val why = thinking { explainDestination(current.name.orEmpty(), plan, null) }
        val options = "${why ?: lines.goesTo(language, plan.destination)} " +
            lines.plan(language, current.name.orEmpty(), plan.options)
        return if (oneTime.isPositive) "${lines.oneTimeNoted(language, oneTime)} $options" else options
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

            text.any(Char::isDigit) -> understand()

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
            text.any(Char::isDigit) -> understand()
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
            dateIsFixed = current.dateIsFixed,
            frequency = current.frequency,
            lumpSum = current.oneTime,
            language = goalLanguage,
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

    private suspend fun respondAfterDone(text: String): String? = if (wantsAnother(text)) startAnother() else finish()

    override fun onCleared() {
        voice.stop()
    }

    private companion object {
        const val SPEECH_LIMIT_MS = 45_000L
        const val ACK_AFTER_MS = 1_200L
        const val MAX_FALSE_INTERRUPTIONS = 2
        const val TAG = "VoiceGoal"
        const val EXPLAIN_LIMIT_MS = 6_000L
    }
}
