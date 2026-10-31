package com.shivangi.gullak.core.ai.explanation

import androidx.test.core.app.ApplicationProvider
import com.shivangi.gullak.core.ai.gemini.GeminiConfig
import com.shivangi.gullak.core.ai.geminiClient
import com.shivangi.gullak.core.ai.geminiResponse
import com.shivangi.gullak.core.ai.guard.NumberGuard
import com.shivangi.gullak.core.ai.httpError
import com.shivangi.gullak.core.domain.analytics.AnalyticsEvent
import com.shivangi.gullak.core.domain.testing.RecordingAnalytics
import com.shivangi.gullak.core.model.ai.GoalLanguage
import com.shivangi.gullak.core.model.goal.DebitKind
import com.shivangi.gullak.core.model.goal.Destination
import com.shivangi.gullak.core.model.goal.Frequency
import com.shivangi.gullak.core.model.goal.ScheduledDebit
import com.shivangi.gullak.core.model.money.Money
import com.shivangi.gullak.core.model.plan.DestinationReason
import com.shivangi.gullak.core.model.plan.Plan
import com.shivangi.gullak.core.model.plan.PlanOption
import com.shivangi.gullak.core.model.plan.ReplanChoice
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeminiPlanExplanationServiceTest {

    private val server = MockWebServer()
    private val analytics = RecordingAnalytics()
    private val templates = ExplanationTemplates(ApplicationProvider.getApplicationContext())

    private val monthly = PlanOption(
        frequency = Frequency.MONTHLY,
        amountPerDebit = Money.fromRupees(3_600),
        numberOfDebits = 14,
        firstDebit = LocalDate.parse("2026-11-05"),
        lastDebit = LocalDate.parse("2027-12-05"),
    )
    private val weddingPlan = Plan(
        remaining = Money.fromRupees(50_000),
        options = listOf(monthly),
        oneTimeOnly = false,
        destination = Destination.PAYTM_GOLD,
        destinationReason = DestinationReason.LONG_GOAL,
        monthsToGoal = 14,
    )

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun service(config: GeminiConfig = GeminiConfig("test-key", "gemini-3.8-flash")) =
        GeminiPlanExplanationService(
            client = server.geminiClient(config),
            guard = NumberGuard(),
            templates = templates,
            analytics = analytics,
        )

    private suspend fun explainWedding(
        service: GeminiPlanExplanationService,
        language: GoalLanguage,
    ) = service.explain("Bhai ki shaadi", weddingPlan, monthly, language)

    @Test
    fun groundedModelReplyIsUsed() = runTest {
        val reply = "Har mahine ₹3,600 bachaiye, 14 baar, aur Bhai ki shaadi ka paisa jud jayega. " +
            "Sone ka daam upar-neeche ho sakta hai."
        server.enqueue(geminiResponse(reply))

        assertEquals(reply, explainWedding(service(), GoalLanguage.HINGLISH))
        val sent = server.takeRequest().body?.utf8().orEmpty()
        assertTrue(sent.contains("amount_per_debit_rupees"))
        assertTrue(sent.contains("Language: hinglish"))
    }

    @Test
    fun replyWithAnInventedNumberIsReplacedByTheTemplate() = runTest {
        server.enqueue(geminiResponse("Har mahine ₹3,700 bachaiye aur 8% return milega."))

        val text = explainWedding(service(), GoalLanguage.HINGLISH)

        assertTrue(text, text.startsWith("Har mahine ki 5 tareekh ko ₹3,600 bachaiye"))
        assertTrue(text.contains("Sone ka daam"))
        assertTrue(AnalyticsEvent.AiFallbackUsed("number_mismatch") in analytics.events)
    }

    @Test
    fun modelFailureFallsBackToTheTemplate() = runTest {
        server.enqueue(httpError(500))
        server.enqueue(httpError(500))

        val text = explainWedding(service(), GoalLanguage.ENGLISH)

        assertEquals(
            "Save ₹3,600 on the 5th of every month, 14 times from 5 Nov 2026 to 5 Dec 2027, and Bhai ki shaadi " +
                "is covered. The money goes to Paytm Gold. Gold's price can go up or down, so its value may " +
                "change along the way.",
            text,
        )
    }

    @Test
    fun replanTemplatesUseTheEnginesNumbers() = runTest {
        val catchUp = ReplanChoice.CatchUp(
            listOf(
                ScheduledDebit(LocalDate.parse("2027-03-15"), Money.fromRupees(2_250), DebitKind.CATCH_UP),
                ScheduledDebit(LocalDate.parse("2027-03-25"), Money.fromRupees(2_250), DebitKind.CATCH_UP),
            ),
        )

        // No key: the template answers without calling anything.
        val text = service(config = GeminiConfig("", ""))
            .explainReplan("Laptop", catchUp, Money.fromRupees(4_500), GoalLanguage.ENGLISH)

        assertEquals(
            "Your ₹4,500 AutoPay did not go through. Two extra debits of ₹2,250, on 15 Mar 2027 and 25 Mar 2027, " +
                "put Laptop back on track.",
            text,
        )
    }

    @Test
    fun replanReplyIsGuardedToo() = runTest {
        server.enqueue(geminiResponse("Koi baat nahi. Agle 3 debit ₹6,000 ke honge."))
        server.enqueue(geminiResponse("Koi baat nahi. Agle 4 debit ₹6,000 ke honge."))
        val spread = ReplanChoice.Spread(Money.fromRupees(6_000), 3)
        val service = service()

        val grounded = service.explainReplan("Laptop", spread, Money.fromRupees(4_500), GoalLanguage.HINGLISH)
        val invented = service.explainReplan("Laptop", spread, Money.fromRupees(4_500), GoalLanguage.HINGLISH)

        assertEquals("Koi baat nahi. Agle 3 debit ₹6,000 ke honge.", grounded)
        assertTrue(invented, invented.startsWith("₹4,500 ki AutoPay nahi hui."))
    }
}
