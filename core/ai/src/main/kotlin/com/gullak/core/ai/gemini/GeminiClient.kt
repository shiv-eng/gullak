package com.gullak.core.ai.gemini

import com.gullak.core.model.result.AppError
import com.gullak.core.model.result.AppResult
import java.io.IOException
import java.io.InterruptedIOException
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import retrofit2.HttpException

/**
 * Calls `generateContent` and returns the answer text. One retry on a network error, timeout or 5xx;
 * none on 4xx. Timeouts are set on the OkHttp client (8 s per call).
 */
internal class GeminiClient @Inject constructor(
    private val api: GeminiApi,
    private val config: GeminiConfig,
) {
    val isConfigured: Boolean get() = config.isConfigured

    suspend fun generate(
        systemInstruction: String,
        contents: List<Content>,
        temperature: Double,
        responseSchema: JsonObject? = null,
    ): AppResult<String> {
        if (!config.isConfigured) return AppResult.Failure(AppError.NotConfigured)
        val request = GenerateContentRequest(
            contents = contents,
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction))),
            generationConfig = generationConfig(temperature, responseSchema),
        )
        val first = attempt(request)
        return if (first is AppResult.Failure && first.error.isRetryable()) attempt(request) else first
    }

    private fun generationConfig(
        temperature: Double,
        responseSchema: JsonObject?,
    ) = GenerationConfig(
        temperature = temperature.takeUnless { config.isGemini3OrLater },
        responseMimeType = responseSchema?.let { JSON_MIME_TYPE },
        responseSchema = responseSchema,
        thinkingConfig = ThinkingConfig(THINKING_LEVEL).takeIf { config.isGemini3OrLater },
    )

    // Each exception becomes a typed AppError the caller acts on (retry, fallback, analytics reason);
    // the error type is the information kept.
    @Suppress("SwallowedException")
    private suspend fun attempt(request: GenerateContentRequest): AppResult<String> = try {
        answerText(api.generateContent(config.modelId, request))
    } catch (e: HttpException) {
        AppResult.Failure(AppError.Http(e.code()))
    } catch (e: InterruptedIOException) {
        AppResult.Failure(AppError.Timeout)
    } catch (e: IOException) {
        AppResult.Failure(AppError.Network)
    } catch (e: SerializationException) {
        AppResult.Failure(AppError.InvalidResponse(e.message ?: "unreadable response"))
    }

    private fun answerText(response: GenerateContentResponse): AppResult<String> {
        response.promptFeedback?.blockReason?.let {
            return AppResult.Failure(AppError.InvalidResponse("blocked: $it"))
        }
        val text = response.candidates.firstOrNull()?.content?.parts.orEmpty()
            .filter { it.thought != true }
            .mapNotNull { it.text }
            .joinToString("")
            .trim()
        return if (text.isEmpty()) {
            AppResult.Failure(AppError.InvalidResponse("empty answer"))
        } else {
            AppResult.Success(text)
        }
    }

    private fun AppError.isRetryable(): Boolean = when (this) {
        AppError.Network, AppError.Timeout -> true
        is AppError.Http -> code >= SERVER_ERROR
        else -> false
    }

    private companion object {
        const val JSON_MIME_TYPE = "application/json"
        const val THINKING_LEVEL = "low"
        const val SERVER_ERROR = 500
    }
}
