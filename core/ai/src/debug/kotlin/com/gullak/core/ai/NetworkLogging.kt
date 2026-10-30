package com.gullak.core.ai

import com.gullak.core.ai.gemini.GeminiApi
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/** Debug builds only: logs request and response bodies, with the API key header redacted. */
internal fun networkLoggingInterceptor(): Interceptor? = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
    redactHeader(GeminiApi.API_KEY_HEADER)
}
