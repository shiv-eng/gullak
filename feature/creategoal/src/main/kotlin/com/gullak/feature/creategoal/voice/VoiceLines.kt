package com.gullak.feature.creategoal.voice

import android.content.Context
import androidx.annotation.ArrayRes
import androidx.core.os.ConfigurationCompat
import com.gullak.core.domain.ai.FollowUpReason
import com.gullak.core.model.ai.GoalLanguage
import com.gullak.core.model.goal.Frequency
import com.gullak.core.model.money.IndianMoneyFormat
import com.gullak.core.model.money.Money
import com.gullak.core.model.plan.PlanOption
import com.gullak.feature.creategoal.DemoBankAccount
import com.gullak.feature.creategoal.R
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Everything Gullak says in a conversation, in the language the user is speaking. Amounts and dates are passed in. */
@Suppress("TooManyFunctions") // One function for each thing Gullak can say.
internal interface VoiceLines {
    fun defaultLanguage(): GoalLanguage

    fun greeting(language: GoalLanguage): String

    fun greetingAgain(language: GoalLanguage): String

    fun moment(language: GoalLanguage): String

    fun didNotHear(language: GoalLanguage): String

    fun didNotUnderstand(language: GoalLanguage): String

    fun didNotCatchChoice(language: GoalLanguage): String

    fun didNotCatchYes(language: GoalLanguage): String

    fun followUp(
        language: GoalLanguage,
        reason: FollowUpReason,
    ): String

    fun readback(
        language: GoalLanguage,
        name: String,
        target: Money,
        date: LocalDate,
    ): String

    fun plan(
        language: GoalLanguage,
        name: String,
        options: List<PlanOption>,
    ): String

    fun dateTooClose(language: GoalLanguage): String

    fun autoPay(
        language: GoalLanguage,
        option: PlanOption,
        bank: DemoBankAccount,
    ): String

    fun started(
        language: GoalLanguage,
        name: String,
        firstDebit: LocalDate?,
    ): String

    fun couldNotStart(language: GoalLanguage): String

    fun goodbye(language: GoalLanguage): String
}

/**
 * Lines come from string arrays with a few wordings each, so Gullak does not repeat itself word for word. They are
 * named per language rather than by locale folder, because the language follows what the user speaks, not the phone.
 */
@Suppress("TooManyFunctions") // One function for each thing Gullak can say.
internal class ResourceVoiceLines @Inject constructor(@ApplicationContext private val context: Context) : VoiceLines {

    override fun defaultLanguage(): GoalLanguage {
        val locale = ConfigurationCompat.getLocales(context.resources.configuration)[0]
        return if (locale?.language == "hi") GoalLanguage.HINDI else GoalLanguage.HINGLISH
    }

    private fun GoalLanguage.pick(
        @ArrayRes english: Int,
        @ArrayRes hinglish: Int,
        @ArrayRes hindi: Int,
    ): String = context.resources.getStringArray(
        when (this) {
            GoalLanguage.ENGLISH -> english
            GoalLanguage.HINGLISH -> hinglish
            GoalLanguage.HINDI -> hindi
        },
    ).random()

    private fun date(
        language: GoalLanguage,
        date: LocalDate,
    ): String = if (language == GoalLanguage.HINDI) HINDI_DATE.format(date) else ENGLISH_DATE.format(date)

    override fun greeting(language: GoalLanguage) =
        language.pick(R.array.voice_greeting_en, R.array.voice_greeting_hinglish, R.array.voice_greeting_hi)

    override fun greetingAgain(language: GoalLanguage) =
        language.pick(R.array.voice_again_en, R.array.voice_again_hinglish, R.array.voice_again_hi)

    override fun moment(language: GoalLanguage) =
        language.pick(R.array.voice_moment_en, R.array.voice_moment_hinglish, R.array.voice_moment_hi)

    override fun didNotHear(language: GoalLanguage) =
        language.pick(R.array.voice_not_heard_en, R.array.voice_not_heard_hinglish, R.array.voice_not_heard_hi)

    override fun didNotUnderstand(language: GoalLanguage) = language.pick(
        R.array.voice_not_understood_en,
        R.array.voice_not_understood_hinglish,
        R.array.voice_not_understood_hi,
    )

    override fun didNotCatchChoice(language: GoalLanguage) =
        language.pick(R.array.voice_which_en, R.array.voice_which_hinglish, R.array.voice_which_hi)

    override fun didNotCatchYes(language: GoalLanguage) =
        language.pick(R.array.voice_yes_or_no_en, R.array.voice_yes_or_no_hinglish, R.array.voice_yes_or_no_hi)

    override fun followUp(
        language: GoalLanguage,
        reason: FollowUpReason,
    ): String = when (reason) {
        FollowUpReason.WHAT_TO_CHANGE ->
            language.pick(R.array.voice_change_en, R.array.voice_change_hinglish, R.array.voice_change_hi)

        FollowUpReason.MISSING_NAME ->
            language.pick(R.array.voice_ask_name_en, R.array.voice_ask_name_hinglish, R.array.voice_ask_name_hi)

        FollowUpReason.MISSING_AMOUNT ->
            language.pick(R.array.voice_ask_amount_en, R.array.voice_ask_amount_hinglish, R.array.voice_ask_amount_hi)

        FollowUpReason.MISSING_DATE ->
            language.pick(R.array.voice_ask_date_en, R.array.voice_ask_date_hinglish, R.array.voice_ask_date_hi)

        FollowUpReason.AMOUNT_OUT_OF_RANGE ->
            language.pick(
                R.array.voice_amount_range_en,
                R.array.voice_amount_range_hinglish,
                R.array.voice_amount_range_hi,
            )

        FollowUpReason.DATE_NOT_IN_FUTURE, FollowUpReason.DATE_TOO_FAR ->
            language.pick(R.array.voice_date_range_en, R.array.voice_date_range_hinglish, R.array.voice_date_range_hi)
    }

    override fun readback(
        language: GoalLanguage,
        name: String,
        target: Money,
        date: LocalDate,
    ) = language.pick(R.array.voice_readback_en, R.array.voice_readback_hinglish, R.array.voice_readback_hi)
        .format(name, IndianMoneyFormat.format(target), date(language, date))

    private fun perPeriod(
        language: GoalLanguage,
        frequency: Frequency,
    ): String = language.pick(
        when (frequency) {
            Frequency.DAILY -> R.array.voice_daily_en
            Frequency.WEEKLY -> R.array.voice_weekly_en
            Frequency.MONTHLY -> R.array.voice_monthly_en
        },
        when (frequency) {
            Frequency.DAILY -> R.array.voice_daily_hinglish
            Frequency.WEEKLY -> R.array.voice_weekly_hinglish
            Frequency.MONTHLY -> R.array.voice_monthly_hinglish
        },
        when (frequency) {
            Frequency.DAILY -> R.array.voice_daily_hi
            Frequency.WEEKLY -> R.array.voice_weekly_hi
            Frequency.MONTHLY -> R.array.voice_monthly_hi
        },
    )

    override fun plan(
        language: GoalLanguage,
        name: String,
        options: List<PlanOption>,
    ): String {
        val or = language.pick(R.array.voice_or_en, R.array.voice_or_hinglish, R.array.voice_or_hi)
        val choices = options.joinToString(or) { option ->
            perPeriod(language, option.frequency).format(IndianMoneyFormat.format(option.amountPerDebit))
        }
        return language.pick(R.array.voice_plan_en, R.array.voice_plan_hinglish, R.array.voice_plan_hi)
            .format(name, choices)
    }

    override fun dateTooClose(language: GoalLanguage) =
        language.pick(R.array.voice_too_close_en, R.array.voice_too_close_hinglish, R.array.voice_too_close_hi)

    override fun autoPay(
        language: GoalLanguage,
        option: PlanOption,
        bank: DemoBankAccount,
    ): String {
        val bankName = when (bank) {
            DemoBankAccount.SBI_SAVINGS -> context.getString(R.string.voice_bank_sbi)
            DemoBankAccount.HDFC_SAVINGS -> context.getString(R.string.voice_bank_hdfc)
            DemoBankAccount.ICICI_SAVINGS -> context.getString(R.string.voice_bank_icici)
        }
        return language.pick(R.array.voice_autopay_en, R.array.voice_autopay_hinglish, R.array.voice_autopay_hi).format(
            bankName,
            perPeriod(language, option.frequency).format(IndianMoneyFormat.format(option.amountPerDebit)),
            date(language, option.firstDebit),
        )
    }

    override fun started(
        language: GoalLanguage,
        name: String,
        firstDebit: LocalDate?,
    ) = language.pick(R.array.voice_started_en, R.array.voice_started_hinglish, R.array.voice_started_hi)
        .format(name, firstDebit?.let { date(language, it) }.orEmpty())

    override fun couldNotStart(language: GoalLanguage) =
        language.pick(R.array.voice_failed_en, R.array.voice_failed_hinglish, R.array.voice_failed_hi)

    override fun goodbye(language: GoalLanguage) =
        language.pick(R.array.voice_bye_en, R.array.voice_bye_hinglish, R.array.voice_bye_hi)

    private companion object {
        val ENGLISH_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
        val HINDI_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("hi-IN"))
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class VoiceLinesModule {
    @Binds
    abstract fun voiceLines(impl: ResourceVoiceLines): VoiceLines
}
