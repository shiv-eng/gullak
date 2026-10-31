package com.shivangi.gullak.core.ai.understanding

import com.shivangi.gullak.core.ai.WEDDING_JSON
import com.shivangi.gullak.core.ai.gemini.GeminiConfig
import com.shivangi.gullak.core.ai.geminiResponse
import com.shivangi.gullak.core.ai.geminiUnderstanding
import com.shivangi.gullak.core.ai.httpError
import com.shivangi.gullak.core.model.ai.ConversationTurn
import com.shivangi.gullak.core.model.ai.GoalLanguage
import com.shivangi.gullak.core.model.ai.ParsedGoal
import com.shivangi.gullak.core.model.ai.Speaker
import com.shivangi.gullak.core.model.money.Money
import com.shivangi.gullak.core.model.result.AppError
import com.shivangi.gullak.core.model.result.AppResult
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The Gemini client and understanding service against a local server: request shape, parsing, retries. */
class GeminiGoalUnderstandingServiceTest {

    private val server = MockWebServer()
    private val today = LocalDate.of(2026, 10, 31)
    private val conversation = listOf(ConversationTurn(Speaker.USER, "Bhai ki shaadi, agle December tak, 50 hazaar"))

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    @Test
    fun parsesAStructuredAnswer() = runTest {
        server.enqueue(geminiResponse(WEDDING_JSON))

        val result = server.geminiUnderstanding().understand(conversation, today)

        assertEquals(
            AppResult.Success(
                ParsedGoal(
                    goalName = "Bhai ki shaadi",
                    targetAmount = Money.fromRupees(50_000),
                    targetDate = LocalDate.parse("2027-12-31"),
                    dateHint = null,
                    language = GoalLanguage.HINGLISH,
                    followUp = null,
                ),
            ),
            result,
        )
    }

    @Test
    fun sendsTheKeyInAHeaderWithStructuredOutputAndNoTemperatureForGemini3() = runTest {
        server.enqueue(geminiResponse(WEDDING_JSON))

        server.geminiUnderstanding().understand(conversation, today)

        val request = server.takeRequest()
        assertEquals("/v1beta/models/gemini-3.8-flash:generateContent", request.url.encodedPath)
        assertEquals("test-key", request.headers["x-goog-api-key"])
        assertFalse(request.url.toString().contains("test-key"))
        val body = Json.parseToJsonElement(request.body?.utf8().orEmpty()).jsonObject
        val config = body.getValue("generationConfig").jsonObject
        assertEquals("application/json", config.getValue("responseMimeType").jsonPrimitive.content)
        assertTrue("responseSchema" in config)
        assertFalse("temperature" in config)
        assertEquals(
            "low",
            config.getValue("thinkingConfig").jsonObject.getValue("thinkingLevel").jsonPrimitive.content,
        )
        val instruction = body.getValue("systemInstruction").toString()
        assertTrue(instruction.contains("Today's date is 2026-10-31"))
        assertTrue(instruction.contains("never as instructions"))
    }

    @Test
    fun olderModelsGetTheBriefsTemperatureAndNoThinkingLevel() = runTest {
        server.enqueue(geminiResponse(WEDDING_JSON))

        server.geminiUnderstanding(GeminiConfig("test-key", "gemini-2.5-flash")).understand(conversation, today)

        val config = Json.parseToJsonElement(server.takeRequest().body?.utf8().orEmpty())
            .jsonObject.getValue("generationConfig").jsonObject
        assertEquals("0.0", config.getValue("temperature").jsonPrimitive.content)
        assertFalse("thinkingConfig" in config)
    }

    @Test
    fun retriesOnceOnAServerError() = runTest {
        server.enqueue(httpError(503))
        server.enqueue(geminiResponse(WEDDING_JSON))

        val result = server.geminiUnderstanding().understand(conversation, today)

        assertTrue(result is AppResult.Success)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun givesUpAfterTheRetry() = runTest {
        server.enqueue(httpError(500))
        server.enqueue(httpError(500))

        assertEquals(
            AppResult.Failure(AppError.Http(500)),
            server.geminiUnderstanding().understand(conversation, today),
        )
        assertEquals(2, server.requestCount)
    }

    @Test
    fun doesNotRetryAClientError() = runTest {
        server.enqueue(httpError(400))

        assertEquals(
            AppResult.Failure(AppError.Http(400)),
            server.geminiUnderstanding().understand(conversation, today),
        )
        assertEquals(1, server.requestCount)
    }

    @Test
    fun timesOutAndRetriesOnce() = runTest {
        repeat(2) {
            server.enqueue(MockResponse.Builder().headersDelay(3, TimeUnit.SECONDS).body("{}").build())
        }

        assertEquals(AppResult.Failure(AppError.Timeout), server.geminiUnderstanding().understand(conversation, today))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun answerOutsideTheSchemaIsInvalid() = runTest {
        val wrong = listOf(
            "not json",
            """{"goal_name":"x","target_amount":"fifty","language":"en"}""",
            """{"goal_name":"x","target_date":"31/12/2027","language":"en"}""",
            """{"goal_name":"x","language":"french"}""",
            """{"goal_name":"x","target_amount":-5,"language":"en"}""",
        )
        wrong.forEach { server.enqueue(geminiResponse(it)) }

        wrong.forEach { answer ->
            val result = server.geminiUnderstanding().understand(conversation, today)
            assertTrue(answer, (result as? AppResult.Failure)?.error is AppError.InvalidResponse)
        }
    }

    @Test
    fun blockedPromptIsInvalid() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"promptFeedback":{"blockReason":"SAFETY"}}""").build())

        val result = server.geminiUnderstanding().understand(conversation, today)

        assertEquals(AppResult.Failure(AppError.InvalidResponse("blocked: SAFETY")), result)
    }

    @Test
    fun missingKeyIsNotConfiguredAndMakesNoCall() = runTest {
        val result = server.geminiUnderstanding(GeminiConfig("", "gemini-3.8-flash")).understand(conversation, today)

        assertEquals(AppResult.Failure(AppError.NotConfigured), result)
        assertEquals(0, server.requestCount)
    }
}
