package com.shivangi.gullak.core.ai.gemini

import com.shivangi.gullak.core.ai.networkLoggingInterceptor
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** HTTP setup for Gemini, shared by Hilt and the tests (which point it at a local MockWebServer). */
internal object GeminiHttp {
    const val TIMEOUT_MILLIS = 8_000L

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun client(
        config: GeminiConfig,
        timeoutMillis: Long = TIMEOUT_MILLIS,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .writeTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header(GeminiApi.API_KEY_HEADER, config.apiKey).build())
        }
        .apply { networkLoggingInterceptor()?.let(::addInterceptor) }
        .build()

    fun api(
        client: OkHttpClient,
        baseUrl: String = GeminiApi.BASE_URL,
    ): GeminiApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GeminiApi::class.java)
}
