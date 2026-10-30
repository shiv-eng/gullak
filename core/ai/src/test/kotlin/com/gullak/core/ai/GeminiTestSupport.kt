package com.gullak.core.ai

import com.gullak.core.ai.gemini.GeminiClient
import com.gullak.core.ai.gemini.GeminiConfig
import com.gullak.core.ai.gemini.GeminiHttp
import com.gullak.core.ai.understanding.GeminiGoalUnderstandingService
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

/** Production HTTP setup pointed at a local [MockWebServer], with a short timeout. */
internal fun MockWebServer.geminiClient(
    config: GeminiConfig = GeminiConfig(apiKey = "test-key", modelId = "gemini-3.8-flash"),
    timeoutMillis: Long = 1_000,
): GeminiClient = GeminiClient(GeminiHttp.api(GeminiHttp.client(config, timeoutMillis), url("/").toString()), config)

internal fun MockWebServer.geminiUnderstanding(config: GeminiConfig = GeminiConfig("test-key", "gemini-3.8-flash")) =
    GeminiGoalUnderstandingService(geminiClient(config), GeminiHttp.json)

/** A `generateContent` response whose answer is [text], as Gemini returns it (with a thinking part first). */
internal fun geminiResponse(text: String): MockResponse {
    val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"")
    val body = """
        {
          "candidates": [{
            "content": {"role": "model", "parts": [
              {"text": "thinking about the goal", "thought": true},
              {"text": "$escaped"}
            ]},
            "finishReason": "STOP"
          }],
          "usageMetadata": {"promptTokenCount": 120},
          "modelVersion": "gemini-3.8-flash"
        }
    """.trimIndent()
    return MockResponse.Builder().code(200).body(body).build()
}

internal fun httpError(code: Int): MockResponse = MockResponse.Builder().code(code).body("{}").build()

internal const val WEDDING_JSON =
    """{"goal_name":"Bhai ki shaadi","target_amount":50000,"target_date":"2027-12-31","date_hint":null,""" +
        """"language":"hinglish","follow_up":null}"""
