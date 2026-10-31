package com.gullak.feature.goaldetail

import android.content.Context
import androidx.annotation.ArrayRes
import com.gullak.core.domain.ai.BargeInDetector
import com.gullak.core.domain.ai.ConversationBrain
import com.gullak.core.domain.ai.SpeechEvent
import com.gullak.core.domain.ai.SpeechInput
import com.gullak.core.domain.ai.TalkIntent
import com.gullak.core.domain.ai.VoiceOutput
import com.gullak.core.domain.ai.speakUnlessInterrupted
import com.gullak.core.domain.repository.SettingsRepository
import com.gullak.core.domain.usecase.GoalSummary
import com.gullak.core.domain.usecase.TopUpAnswer
import com.gullak.core.model.ai.ConversationTurn
import com.gullak.core.model.ai.GoalLanguage
import com.gullak.core.model.ai.Speaker
import com.gullak.core.model.goal.Debit
import com.gullak.core.model.money.IndianMoneyFormat
import com.gullak.core.model.money.Money
import com.gullak.core.model.plan.GoalStatus
import com.gullak.core.model.plan.ReplanChoice
import com.gullak.core.model.result.AppResult
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Speaking and hearing on the goal page. Hinglish goals are spoken in Hindi, which the voices say best. */
internal class GoalTalk @Inject constructor(
    private val voice: VoiceOutput,
    private val speech: SpeechInput,
    private val settings: SettingsRepository,
    private val brain: ConversationBrain,
    private val bargeIn: BargeInDetector,
    private val clock: Clock,
    val lines: GoalVoiceLines,
) {
    /** What the user wants done with this goal. The model reads it; plain rules do if it cannot be reached. */
    suspend fun readRequest(
        text: String,
        situation: String,
    ): GoalRequest? {
        val turn = listOf(ConversationTurn(Speaker.USER, text))
        val heard = brain.hear(turn, situation, LocalDate.now(clock))
        val answer = (heard as? AppResult.Success)?.value ?: return goalRequestFrom(text)
        return when (answer.intent) {
            TalkIntent.ADD_MONEY -> answer.oneTime?.let { GoalRequest.AddMoney(it) }
            TalkIntent.PAY_OUT -> GoalRequest.PayOut
            TalkIntent.ASK_TOP_UP -> GoalRequest.TopUp
            else -> goalRequestFrom(text)
        }
    }

    suspend fun isOn(): Boolean = settings.settings.first().voiceReplies

    suspend fun say(
        text: String,
        language: GoalLanguage,
    ) {
        val prefs = settings.settings.first()
        if (!prefs.voiceReplies) return
        withTimeoutOrNull(SPEECH_LIMIT_MS) {
            if (prefs.interruptBySpeaking) {
                voice.speakUnlessInterrupted(text, language, bargeIn)
            } else {
                voice.speak(text, language)
            }
        }
    }

    /** The user's words, or null if nothing usable was heard. [onPartial] gets what is heard so far. */
    suspend fun listen(onPartial: (String) -> Unit = {}): String? {
        var heard: String? = null
        speech.listen(settings.settings.first().speechLanguage.tag).collect { event ->
            when (event) {
                is SpeechEvent.Partial -> onPartial(event.text)
                is SpeechEvent.Final -> heard = event.text
                else -> Unit
            }
        }
        return heard?.takeIf { it.isNotBlank() }
    }

    fun stop() = voice.stop()

    private companion object {
        const val SPEECH_LIMIT_MS = 45_000L
    }
}

/** Everything Gullak says on the goal page. Amounts and dates are passed in, from the plan engine. */
@Suppress("TooManyFunctions") // One function for each thing Gullak can say.
internal interface GoalVoiceLines {
    fun status(
        language: GoalLanguage,
        summary: GoalSummary,
    ): String

    fun offer(
        language: GoalLanguage,
        failed: Debit,
        status: GoalStatus,
        choices: List<ReplanChoice>,
    ): String

    fun applyQuestion(language: GoalLanguage): String

    fun whichOne(language: GoalLanguage): String

    fun applied(language: GoalLanguage): String

    fun leftAsIs(language: GoalLanguage): String

    fun confirmAdd(
        language: GoalLanguage,
        amount: Money,
    ): String

    fun added(
        language: GoalLanguage,
        amount: Money,
    ): String

    fun cannotAdd(language: GoalLanguage): String

    fun confirmPayOut(
        language: GoalLanguage,
        amount: Money,
    ): String

    fun paidOut(
        language: GoalLanguage,
        amount: Money,
    ): String

    fun notTimeToPayOut(
        language: GoalLanguage,
        date: LocalDate,
    ): String

    fun topUp(
        language: GoalLanguage,
        answer: TopUpAnswer,
    ): String

    fun topUpRequested(language: GoalLanguage): String
}

@Suppress("TooManyFunctions") // One function for each thing Gullak can say.
internal class ResourceGoalVoiceLines @Inject constructor(@ApplicationContext private val context: Context) :
    GoalVoiceLines {

    private fun GoalLanguage.pick(
        @ArrayRes english: Int,
        @ArrayRes hindi: Int,
    ): String = context.resources.getStringArray(if (this == GoalLanguage.ENGLISH) english else hindi).random()

    private fun GoalLanguage.date(date: LocalDate): String =
        (if (this == GoalLanguage.ENGLISH) ENGLISH_DATE else HINDI_DATE).format(date)

    private fun money(amount: Money) = IndianMoneyFormat.format(amount)

    override fun status(
        language: GoalLanguage,
        summary: GoalSummary,
    ): String {
        val standing = when (val status = summary.status) {
            GoalStatus.OnTrack -> language.pick(R.array.voice_on_track_en, R.array.voice_on_track_hi)

            is GoalStatus.Behind -> language.pick(
                R.array.voice_behind_en,
                R.array.voice_behind_hi,
            ).format(money(status.by))

            GoalStatus.Reached -> language.pick(R.array.voice_reached_en, R.array.voice_reached_hi)
        }
        val next = summary.nextDebit?.let {
            language.pick(R.array.voice_next_en, R.array.voice_next_hi).format(money(it.amount), language.date(it.date))
        }
        val saved = language.pick(R.array.voice_status_en, R.array.voice_status_hi)
            .format(summary.goal.name, money(summary.saved), money(summary.goal.target))
        return listOfNotNull(saved, standing, next).joinToString(" ")
    }

    override fun offer(
        language: GoalLanguage,
        failed: Debit,
        status: GoalStatus,
        choices: List<ReplanChoice>,
    ): String {
        val ways = choices.joinToString(language.pick(R.array.voice_or_en, R.array.voice_or_hi)) { choice ->
            when (choice) {
                is ReplanChoice.CatchUp -> language.pick(R.array.voice_catch_up_en, R.array.voice_catch_up_hi).format(
                    money(choice.debits.first().amount),
                    choice.debits.joinToString(language.pick(R.array.voice_and_en, R.array.voice_and_hi)) {
                        language.date(it.date)
                    },
                )

                is ReplanChoice.Spread -> language.pick(R.array.voice_spread_en, R.array.voice_spread_hi)
                    .format(money(choice.newAmountPerDebit), choice.remainingDebits)

                is ReplanChoice.Extend -> language.pick(R.array.voice_extend_en, R.array.voice_extend_hi)
                    .format(language.date(choice.newTargetDate))
            }
        }
        val behind = (status as? GoalStatus.Behind)?.by ?: failed.amount
        return language.pick(R.array.voice_offer_en, R.array.voice_offer_hi)
            .format(language.date(failed.date), money(behind), ways)
    }

    override fun confirmAdd(
        language: GoalLanguage,
        amount: Money,
    ) = language.pick(R.array.voice_confirm_add_en, R.array.voice_confirm_add_hi).format(money(amount))

    override fun added(
        language: GoalLanguage,
        amount: Money,
    ) = language.pick(R.array.voice_added_en, R.array.voice_added_hi).format(money(amount))

    override fun cannotAdd(language: GoalLanguage) =
        language.pick(R.array.voice_cannot_add_en, R.array.voice_cannot_add_hi)

    override fun confirmPayOut(
        language: GoalLanguage,
        amount: Money,
    ) = language.pick(R.array.voice_confirm_pay_out_en, R.array.voice_confirm_pay_out_hi).format(money(amount))

    override fun paidOut(
        language: GoalLanguage,
        amount: Money,
    ) = language.pick(R.array.voice_paid_out_en, R.array.voice_paid_out_hi).format(money(amount))

    override fun notTimeToPayOut(
        language: GoalLanguage,
        date: LocalDate,
    ) = language.pick(R.array.voice_not_yet_en, R.array.voice_not_yet_hi).format(language.date(date))

    override fun topUp(
        language: GoalLanguage,
        answer: TopUpAnswer,
    ) = when (answer) {
        TopUpAnswer.NoGap -> language.pick(R.array.voice_top_up_no_gap_en, R.array.voice_top_up_no_gap_hi)

        is TopUpAnswer.NotYetProven -> language.pick(R.array.voice_top_up_not_yet_en, R.array.voice_top_up_not_yet_hi)
            .format(money(answer.shortfall))

        is TopUpAnswer.Offer -> language.pick(R.array.voice_top_up_offer_en, R.array.voice_top_up_offer_hi)
            .format(money(answer.shortfall))
    }

    override fun topUpRequested(language: GoalLanguage) =
        language.pick(R.array.voice_top_up_sent_en, R.array.voice_top_up_sent_hi)

    override fun applyQuestion(language: GoalLanguage) = language.pick(R.array.voice_apply_en, R.array.voice_apply_hi)

    override fun whichOne(language: GoalLanguage) =
        language.pick(R.array.voice_which_way_en, R.array.voice_which_way_hi)

    override fun applied(language: GoalLanguage) = language.pick(R.array.voice_applied_en, R.array.voice_applied_hi)

    override fun leftAsIs(language: GoalLanguage) = language.pick(R.array.voice_left_en, R.array.voice_left_hi)

    private companion object {
        val ENGLISH_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
        val HINDI_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("hi-IN"))
    }
}

/** What can be asked for on the goal page. */
internal sealed interface GoalRequest {
    data class AddMoney(val amount: Money) : GoalRequest

    data object PayOut : GoalRequest

    data object TopUp : GoalRequest
}

/** Plain rules for when the model cannot be reached. */
internal fun goalRequestFrom(text: String): GoalRequest? {
    val lower = text.lowercase()
    return when {
        TOP_UP_WORDS.any { it in lower } -> GoalRequest.TopUp
        PAY_OUT_WORDS.any { it in lower } -> GoalRequest.PayOut
        ADD_WORDS.any { it in lower } -> rupeesIn(lower)?.let { GoalRequest.AddMoney(it) }
        else -> null
    }
}

/** "5000", "5,000", "5 hazaar". */
private fun rupeesIn(text: String): Money? {
    val match = Regex("""(\d[\d,]*)\s*(hazaar|hazar|thousand|k\b|हज़ार|हजार)?""").find(text) ?: return null
    val number = match.groupValues[1].replace(",", "").toLongOrNull() ?: return null
    return Money.fromRupees(if (match.groupValues[2].isEmpty()) number else number * THOUSAND)
}

private const val THOUSAND = 1_000L
private val TOP_UP_WORDS =
    listOf("loan", "top up", "top-up", "topup", "udhar", "kam pad", "short of", "लोन", "उधार", "कम पड़")
private val PAY_OUT_WORDS =
    listOf("pay out", "payout", "withdraw", "nikal", "bank mein", "bank me", "निकाल", "बैंक में")
private val ADD_WORDS = listOf("add", "daal", "dal", "jama", "jod", "put", "डाल", "जमा", "जोड़")

/** The recovery choice a reply names: "catch up", "aage baant do", "doosra". Null if it names none on offer. */
internal fun replanChoiceFrom(
    text: String,
    choices: List<ReplanChoice>,
): Int? {
    val lower = text.lowercase()
    val named = choices.indexOfFirst { choice ->
        when (choice) {
            is ReplanChoice.CatchUp -> CATCH_UP_WORDS
            is ReplanChoice.Spread -> SPREAD_WORDS
            is ReplanChoice.Extend -> EXTEND_WORDS
        }.any { it in lower }
    }
    if (named >= 0) return named
    val position = POSITION_WORDS.indexOfFirst { words -> words.any { it in lower } }
    return position.takeIf { it in choices.indices }
}

private val CATCH_UP_WORDS = listOf("catch", "isi mahine", "this month", "extra", "इसी महीने", "अतिरिक्त", "कैच")
private val SPREAD_WORDS = listOf("spread", "baant", "bant", "बाँट", "बांट", "next payments", "agli", "अगले")
private val EXTEND_WORDS = listOf("date", "tareekh", "tarikh", "extend", "तारीख़", "तारीख", "aage badha", "आगे बढ़ा")
private val POSITION_WORDS = listOf(
    listOf("pehla", "pehle", "first", "पहला", "पहले"),
    listOf("doosra", "dusra", "second", "दूसरा"),
    listOf("teesra", "tisra", "third", "तीसरा"),
)

@Module
@InstallIn(SingletonComponent::class)
internal abstract class GoalVoiceLinesModule {
    @Binds
    abstract fun lines(impl: ResourceGoalVoiceLines): GoalVoiceLines
}
