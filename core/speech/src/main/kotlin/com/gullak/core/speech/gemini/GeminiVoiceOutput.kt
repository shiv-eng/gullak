package com.gullak.core.speech.gemini

import android.os.SystemClock
import com.gullak.core.model.ai.GoalLanguage
import com.gullak.core.speech.cloud.CloudSpeaker
import com.gullak.core.speech.cloud.WavAudio
import com.gullak.core.speech.sarvam.SarvamHttp
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal data class GeminiVoiceConfig(
    val apiKey: String,
    val model: String,
    val voice: String,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()
}

/**
 * Gemini's text-to-speech, the voice used when Sarvam is not available. It is sent only the words to say: any style
 * note sent with them gets read aloud on short lines.
 */
@Singleton
internal class GeminiVoiceOutput @Inject constructor(
    private val config: GeminiVoiceConfig,
    private val speaker: CloudSpeaker,
    @SarvamHttp private val client: OkHttpClient,
) {
    /** While rate limited, go straight to the next voice instead of waiting for a refusal on every line. */
    @Volatile
    private var restUntil = 0L

    suspend fun speak(
        text: String,
        language: GoalLanguage,
    ): Boolean {
        if (!config.isConfigured || SystemClock.elapsedRealtime() < restUntil) return false
        // One call per reply, not per sentence: a free key only allows a few a minute.
        return speaker.speak(text, "gemini|${language.name}", bySentence = false, fetch = ::fetch)
    }

    private suspend fun fetch(sentence: String): WavAudio {
        val body = buildJsonObject {
            putJsonArray("contents") {
                add(
                    buildJsonObject {
                        putJsonArray("parts") {
                            add(buildJsonObject { put("text", sentence) })
                        }
                    },
                )
            }
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") { add("AUDIO") }
                putJsonObject("speechConfig") {
                    putJsonObject("voiceConfig") {
                        putJsonObject("prebuiltVoiceConfig") { put("voiceName", config.voice) }
                    }
                }
            }
        }.toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url("$BASE_URL/${config.model}:generateContent")
            .header("x-goog-api-key", config.apiKey)
            .post(body)
            .build()
        val reply = runInterruptible {
            client.newCall(request).execute().use { response ->
                if (response.code == TOO_MANY_REQUESTS) restUntil = SystemClock.elapsedRealtime() + REST_MS
                if (!response.isSuccessful) throw IOException("Gemini voice HTTP ${response.code}")
                response.body.string()
            }
        }
        return geminiClip(reply)
    }

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        const val TOO_MANY_REQUESTS = 429
        const val REST_MS = 60_000L
        val JSON = "application/json".toMediaType()
    }
}

/** Gemini sends either a WAV file or raw 16-bit audio, depending on the model. */
internal fun geminiClip(reply: String): WavAudio {
    val inline = Json.parseToJsonElement(reply).jsonObject.getValue("candidates").jsonArray.first()
        .jsonObject.getValue("content")
        .jsonObject.getValue("parts").jsonArray.first()
        .jsonObject.getValue("inlineData").jsonObject
    val audio = Base64.getDecoder().decode(inline.getValue("data").jsonPrimitive.content)
    if (WavAudio.isWav(audio)) return WavAudio.parse(audio)
    val rate = Regex("""rate=(\d+)""").find(inline.getValue("mimeType").jsonPrimitive.content)
        ?.groupValues?.get(1)?.toInt()
    return WavAudio(rate ?: DEFAULT_RATE, audio)
}

private const val DEFAULT_RATE = 24_000
