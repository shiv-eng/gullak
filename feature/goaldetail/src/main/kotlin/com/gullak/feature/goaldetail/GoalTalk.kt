package com.gullak.feature.goaldetail

import android.content.Context
import androidx.annotation.ArrayRes
import com.gullak.core.domain.ai.SpeechEvent
import com.gullak.core.domain.ai.SpeechInput
import com.gullak.core.domain.ai.VoiceOutput
import com.gullak.core.domain.repository.SettingsRepository
import com.gullak.core.domain.usecase.GoalSummary
import com.gullak.core.model.ai.GoalLanguage
import com.gullak.core.model.goal.Debit
import com.gullak.core.model.money.IndianMoneyFormat
import com.gullak.core.model.money.Money
import com.gullak.core.model.plan.GoalStatus
import com.gullak.core.model.plan.ReplanChoice
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
    val lines: GoalVoiceLines,
) {
    suspend fun isOn(): Boolean = settings.settings.first().voiceReplies

    suspend fun say(
        text: String,
        language: GoalLanguage,
    ) {
        if (isOn()) withTimeoutOrNull(SPEECH_LIMIT_MS) { voice.speak(text, language) }
    }

    /** The user's words, or null if nothing usable was heard. */
    suspend fun listen(): String? {
        var heard: String? = null
        speech.listen(settings.settings.first().speechLanguage.tag).collect { event ->
            if (event is SpeechEvent.Final) heard = event.text
        }
        return heard?.takeIf { it.isNotBlank() }
    }

    fun stop() = voice.stop()

    private companion object {
        const val SPEECH_LIMIT_MS = 45_000L
    }
}

/** Everything Gullak says on the goal page. Amounts and dates are passed in, from the plan engine. */
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
}

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
