package com.gullak.core.ai.gemini

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/** The API key is added by an interceptor as the `x-goog-api-key` header, never in the URL. */
internal interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Body request: GenerateContentRequest,
    ): GenerateContentResponse

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/"
        const val API_KEY_HEADER = "x-goog-api-key"
    }
}
