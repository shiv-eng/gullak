package com.shivangi.gullak.core.ai.conversation

import com.shivangi.gullak.core.ai.gemini.GeminiHttp
import com.shivangi.gullak.core.domain.ai.TalkIntent
import com.shivangi.gullak.core.model.ai.GoalLanguage
import com.shivangi.gullak.core.model.goal.Frequency
import com.shivangi.gullak.core.model.money.Money
import com.shivangi.gullak.core.model.result.AppResult
import com.shivangi.gullak.core.model.result.getOrNull
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseBrainAnswerTest {

    private fun answer(
        intent: String = "GOAL_DETAILS",
        say: String? = "वाह, बधाई हो!",
        followUp: String? = null,
        frequency: String? = null,
    ) = """
        {"intent":"$intent","say":${say.quoted()},"goal_name":"Bhai ki shaadi","target_amount":10000,
        "target_date":"2027-03-15","date_hint":null,"language":"hinglish","follow_up":${followUp.quoted()},
        "frequency":${frequency.quoted()}}
    """.trimIndent()

    private fun String?.quoted() = this?.let { "\"$it\"" } ?: "null"

    private fun parse(text: String) = parseBrainAnswer(GeminiHttp.json, text)

    @Test
    fun aTurnIsRead() {
        val brain = parse(answer(frequency = "MONTHLY")).getOrNull()

        assertEquals(TalkIntent.GOAL_DETAILS, brain?.intent)
        assertEquals("वाह, बधाई हो!", brain?.say)
        assertEquals("Bhai ki shaadi", brain?.goal?.goalName)
        assertEquals(Money.fromRupees(10_000), brain?.goal?.targetAmount)
        assertEquals(LocalDate.of(2027, 3, 15), brain?.goal?.targetDate)
        assertEquals(GoalLanguage.HINGLISH, brain?.goal?.language)
        assertEquals(Frequency.MONTHLY, brain?.frequency)
    }

    @Test
    fun aReactionWithANumberInItIsDropped() {
        listOf("₹500 is great", "बढ़िया, ४५०० रुपये", "That is 5 lakh", "दो हज़ार ठीक है").forEach { say ->
            assertNull(say, parse(answer(say = say)).getOrNull()?.say)
        }
    }

    @Test
    fun aPromiseAboutMoneyIsNeverSpoken() {
        listOf("आपका पैसा पूरी तरह सुरक्षित है।", "इसमें गारंटी है।", "मासिक बेहतर है।").forEach { say ->
            assertNull(say, parse(answer(say = say)).getOrNull()?.say)
        }
    }

    @Test
    fun aReplyInTheWrongScriptIsNotSpoken() {
        assertNull(parse(answer(say = "Daily is good for steady progress.")).getOrNull()?.say)
        val english = answer(say = "बहुत बढ़िया!").replace("hinglish", "en")
        assertNull(parse(english).getOrNull()?.say)
    }

    @Test
    fun aQuestionWithANumberInItIsDropped() {
        assertNull(parse(answer(followUp = "Kya 500 theek hai?")).getOrNull()?.goal?.followUp)
        assertEquals("कितने पैसे?", parse(answer(followUp = "कितने पैसे?")).getOrNull()?.goal?.followUp)
    }

    @Test
    fun anythingUnexpectedIsAFailureNotAGuess() {
        assertTrue(parse("not json") is AppResult.Failure)
        assertTrue(parse(answer(intent = "START_THE_MANDATE")) is AppResult.Failure)
        assertTrue(parse(answer().replace("2027-03-15", "soon")) is AppResult.Failure)
    }
}
