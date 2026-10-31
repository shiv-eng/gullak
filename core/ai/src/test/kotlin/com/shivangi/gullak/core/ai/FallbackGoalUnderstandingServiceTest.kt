package com.shivangi.gullak.core.ai

import com.shivangi.gullak.core.ai.demo.DemoAiResponses
import com.shivangi.gullak.core.ai.gemini.GeminiConfig
import com.shivangi.gullak.core.ai.rules.RuleBasedGoalParser
import com.shivangi.gullak.core.domain.analytics.AnalyticsEvent
import com.shivangi.gullak.core.domain.testing.FakeSettingsRepository
import com.shivangi.gullak.core.domain.testing.RecordingAnalytics
import com.shivangi.gullak.core.model.ai.AiPath
import com.shivangi.gullak.core.model.ai.ConversationTurn
import com.shivangi.gullak.core.model.ai.Speaker
import com.shivangi.gullak.core.model.money.Money
import com.shivangi.gullak.core.model.result.getOrNull
import com.shivangi.gullak.core.model.settings.AppSettings
import java.time.LocalDate
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Demo answers → Gemini → rules, with a fake HTTP layer (MockWebServer). */
class FallbackGoalUnderstandingServiceTest {

    private val server = MockWebServer()
    private val today = LocalDate.of(2026, 10, 31)
    private val analytics = RecordingAnalytics()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun service(
        demoMode: Boolean,
        config: GeminiConfig = GeminiConfig("test-key", "gemini-3.8-flash"),
    ): Pair<FallbackGoalUnderstandingService, FakeSettingsRepository> {
        val settings = FakeSettingsRepository(AppSettings(demoMode = demoMode))
        val service = FallbackGoalUnderstandingService(
            settings = settings,
            demo = DemoAiResponses(),
            gemini = server.geminiUnderstanding(config),
            rules = RuleBasedGoalParser(),
            analytics = analytics,
        )
        return service to settings
    }

    private fun said(vararg turns: String) = turns.mapIndexed { i, text ->
        ConversationTurn(if (i % 2 == 0) Speaker.USER else Speaker.GULLAK, text)
    }

    @Test
    fun demoModeUsesSavedAnswersWithSimulatedLatencyAndNoNetwork() = runTest {
        val (service, settings) = service(demoMode = true)

        val goal = service.understand(said("Bhai ki shaadi, agle December tak, 50 hazaar"), today).getOrNull()

        assertEquals(Money.fromRupees(50_000), goal?.targetAmount)
        assertEquals(700L, currentTime)
        assertEquals(0, server.requestCount)
        assertEquals(AiPath.DEMO, settings.settings.value.lastAiPath)
        assertTrue(AnalyticsEvent.AiPathUsed(AiPath.DEMO) in analytics.events)
    }

    @Test
    fun demoModeAsksGeminiForUnknownSentencesThenFallsBackToRulesAndCombinesTurns() = runTest {
        val (service, settings) = service(demoMode = true)

        val goal = service.understand(
            said(
                "Mujhe naya phone lena hai",
                "Phone ke liye kitne paise chahiye, aur kab tak?",
                "20 hazaar, March tak",
            ),
            today,
        ).getOrNull()

        assertEquals("Naya phone", goal?.goalName)
        assertEquals(Money.fromRupees(20_000), goal?.targetAmount)
        assertEquals(LocalDate.parse("2027-03-31"), goal?.targetDate)
        assertEquals(AiPath.RULES, settings.settings.value.lastAiPath)
        assertTrue(server.requestCount > 0)
    }

    @Test
    fun liveModeUsesGemini() = runTest {
        server.enqueue(geminiResponse(WEDDING_JSON))
        val (service, settings) = service(demoMode = false)

        val goal = service.understand(said("Bhai ki shaadi, agle December tak, 50 hazaar"), today).getOrNull()

        assertEquals("Bhai ki shaadi", goal?.goalName)
        assertEquals(AiPath.GEMINI, settings.settings.value.lastAiPath)
    }

    @Test
    fun geminiServerErrorsFallBackToRules() = runTest {
        server.enqueue(httpError(500))
        server.enqueue(httpError(500))
        val (service, settings) = service(demoMode = false)

        val goal = service.understand(said("Laptop ke liye 36 hazaar, June tak"), today).getOrNull()

        assertEquals(Money.fromRupees(36_000), goal?.targetAmount)
        assertEquals(AiPath.RULES, settings.settings.value.lastAiPath)
        assertTrue(AnalyticsEvent.AiFallbackUsed("http_500") in analytics.events)
    }

    @Test
    fun invalidJsonFallsBackToRules() = runTest {
        server.enqueue(geminiResponse("""{"goal_name": 5}"""))
        val (service, _) = service(demoMode = false)

        val goal = service.understand(said("Goa trip in March, 30,000"), today).getOrNull()

        assertEquals(LocalDate.parse("2027-03-31"), goal?.targetDate)
        assertTrue(AnalyticsEvent.AiFallbackUsed("invalid_response") in analytics.events)
    }

    @Test
    fun missingKeyFallsBackToRulesWithoutCalling() = runTest {
        val (service, _) = service(demoMode = false, config = GeminiConfig("", ""))

        val goal = service.understand(said("Diwali ke liye 20 hazaar"), today).getOrNull()

        assertEquals("Diwali", goal?.dateHint)
        assertEquals(0, server.requestCount)
        assertTrue(AnalyticsEvent.AiFallbackUsed("not_configured") in analytics.events)
    }

    @Test
    fun aSentenceLikeASavedOneButWithAnotherAmountIsNotGivenTheSavedAnswer() {
        val demo = DemoAiResponses()

        assertEquals(Money.fromRupees(50_000), demo.match("Bhai ki shadi, agle December tak, 50 hazar")?.targetAmount)
        assertEquals(null, demo.match("Bhai ki shaadi, agle December tak, 60 hazaar"))
        assertEquals(null, demo.match("Bhai ki shaadi, agle January tak, 50 hazaar"))
    }
}
