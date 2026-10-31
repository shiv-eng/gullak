package com.shivangi.gullak.core.speech.sarvam

import com.shivangi.gullak.core.model.ai.GoalLanguage
import com.shivangi.gullak.core.speech.cloud.CloudSpeaker
import com.shivangi.gullak.core.speech.cloud.WavAudio
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Sarvam's Bulbul voice over REST. */
internal class SarvamVoiceOutput @Inject constructor(
    private val config: SarvamConfig,
    private val speaker: CloudSpeaker,
    @SarvamHttp private val client: OkHttpClient,
) {
    suspend fun speak(
        text: String,
        language: GoalLanguage,
    ): Boolean = speaker.speak(text, "sarvam|${language.name}") { sentence -> fetch(sentence, language) }

    private suspend fun fetch(
        sentence: String,
        language: GoalLanguage,
    ): WavAudio {
        val body = buildJsonObject {
            put("text", sentence)
            put("language_code", if (language == GoalLanguage.ENGLISH) "en-IN" else "hi-IN")
            put("model", config.ttsModel)
            put("speaker", config.ttsSpeaker)
            put("speech_sample_rate", SAMPLE_RATE)
            put("output_audio_codec", "wav")
            put("pace", PACE)
        }.toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url(TTS_URL)
            .header(KEY_HEADER, config.apiKey)
            .post(body)
            .build()
        val reply = runInterruptible {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Sarvam voice HTTP ${response.code}")
                response.body.string()
            }
        }
        val encoded = Json.parseToJsonElement(
            reply,
        ).jsonObject.getValue("audios").jsonArray.first().jsonPrimitive.content
        return WavAudio.parse(Base64.getDecoder().decode(encoded))
    }

    private companion object {
        const val TTS_URL = "https://api.sarvam.ai/text-to-speech"
        const val KEY_HEADER = "api-subscription-key"
        const val SAMPLE_RATE = 24_000

        /** A little quicker than the default, which sounds slow in conversation. */
        const val PACE = 1.1
        val JSON = "application/json".toMediaType()
    }
}
