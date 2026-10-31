package com.shivangi.gullak.core.ai.explanation

import android.content.Context
import androidx.annotation.StringRes
import com.shivangi.gullak.core.ai.R
import com.shivangi.gullak.core.model.ai.GoalLanguage
import com.shivangi.gullak.core.model.goal.Destination
import com.shivangi.gullak.core.model.goal.Frequency
import com.shivangi.gullak.core.model.money.IndianMoneyFormat
import com.shivangi.gullak.core.model.money.Money
import com.shivangi.gullak.core.model.plan.Plan
import com.shivangi.gullak.core.model.plan.PlanOption
import com.shivangi.gullak.core.model.plan.ReplanChoice
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * Explanations built only from string resources and engine numbers: the fallback whenever the model is
 * unavailable, off (demo mode) or fails NumberGuard. They follow the language the user spoke, not the app's UI
 * language, so each language has its own named string (`_en`, `_hinglish`, `_hi`).
 */
internal class ExplanationTemplates @Inject constructor(@ApplicationContext private val context: Context) {
    fun plan(
        goalName: String,
        plan: Plan,
        chosen: PlanOption,
        language: GoalLanguage,
    ): String {
        val template = when (chosen.frequency) {
            Frequency.DAILY -> Strings(
                R.string.explain_daily_en,
                R.string.explain_daily_hinglish,
                R.string.explain_daily_hi,
            )

            Frequency.WEEKLY ->
                Strings(R.string.explain_weekly_en, R.string.explain_weekly_hinglish, R.string.explain_weekly_hi)

            Frequency.MONTHLY ->
                Strings(R.string.explain_monthly_en, R.string.explain_monthly_hinglish, R.string.explain_monthly_hi)
        }
        val destination = context.getString(
            when (plan.destination) {
                Destination.PAYTM_GOLD -> R.string.destination_gold
                Destination.PAYTM_MONEY_FUND -> R.string.destination_fund
            },
        )
        val main = text(
            template,
            language,
            money(chosen.amountPerDebit),
            chosen.numberOfDebits,
            date(chosen.firstDebit, language),
            date(chosen.lastDebit, language),
            goalName,
            destination,
        )
        if (plan.destination != Destination.PAYTM_GOLD) return main
        val goldRisk =
            Strings(R.string.explain_gold_risk_en, R.string.explain_gold_risk_hinglish, R.string.explain_gold_risk_hi)
        return main + " " + text(goldRisk, language)
    }

    fun replan(
        goalName: String,
        choice: ReplanChoice,
        missed: Money,
        language: GoalLanguage,
    ): String = when (choice) {
        is ReplanChoice.CatchUp -> text(
            Strings(R.string.explain_catch_up_en, R.string.explain_catch_up_hinglish, R.string.explain_catch_up_hi),
            language,
            money(missed),
            money(choice.debits.first().amount),
            date(choice.debits.first().date, language),
            date(choice.debits.last().date, language),
            goalName,
        )

        is ReplanChoice.Spread -> text(
            Strings(R.string.explain_spread_en, R.string.explain_spread_hinglish, R.string.explain_spread_hi),
            language,
            money(missed),
            choice.remainingDebits,
            money(choice.newAmountPerDebit),
            goalName,
        )

        is ReplanChoice.Extend -> text(
            Strings(R.string.explain_extend_en, R.string.explain_extend_hinglish, R.string.explain_extend_hi),
            language,
            money(missed),
            date(choice.newTargetDate, language),
            goalName,
        )
    }

    private class Strings(
        @StringRes val english: Int,
        @StringRes val hinglish: Int,
        @StringRes val hindi: Int,
    )

    private fun text(
        strings: Strings,
        language: GoalLanguage,
        vararg args: Any,
    ): String {
        val id = when (language) {
            GoalLanguage.ENGLISH -> strings.english
            GoalLanguage.HINGLISH -> strings.hinglish
            GoalLanguage.HINDI -> strings.hindi
        }
        return context.getString(id, *args)
    }

    private fun money(amount: Money) = IndianMoneyFormat.format(amount)

    private fun date(
        date: LocalDate,
        language: GoalLanguage,
    ): String = (if (language == GoalLanguage.HINDI) HINDI_DATE else ENGLISH_DATE).format(date)

    private companion object {
        val ENGLISH_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        val HINDI_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("hi-IN"))
    }
}
