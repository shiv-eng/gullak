package com.gullak.core.speech.sarvam

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.gullak.core.domain.di.IoDispatcher
import com.gullak.core.model.ai.GoalLanguage
import java.io.IOException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
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

/**
 * Sarvam's Bulbul voice over REST. Sentences are fetched together and played in order, so the first words come
 * quickly; lines already said are cached, which saves both time and credits.
 */
@Singleton
internal class SarvamVoiceOutput @Inject constructor(
    private val config: SarvamConfig,
    @SarvamHttp private val client: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val cache = ConcurrentHashMap<String, WavAudio>()

    /** False if Sarvam could not speak the text, so the caller can use the phone's own voice. */
    suspend fun speak(
        text: String,
        language: GoalLanguage,
    ): Boolean = withContext(ioDispatcher) {
        try {
            val clips = spokenSentences(text).map { sentence -> async { clip(sentence, language) } }
            clips.forEach { play(it.await()) }
            true
        } catch (e: IOException) {
            Log.w(TAG, "Sarvam voice failed", e)
            false
        } catch (e: SerializationException) {
            Log.w(TAG, "Sarvam voice reply not readable", e)
            false
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Sarvam voice reply not usable", e)
            false
        }
    }

    private suspend fun clip(
        sentence: String,
        language: GoalLanguage,
    ): WavAudio {
        val key = "${language.name}|$sentence"
        cache[key]?.let { return it }
        return fetch(sentence, language).also { if (cache.size < MAX_CACHED) cache[key] = it }
    }

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

    private suspend fun play(audio: WavAudio) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(audio.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(WRITE_CHUNK_BYTES * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            track.play()
            var offset = 0
            while (offset < audio.pcm.size) {
                currentCoroutineContext().ensureActive()
                val written = track.write(audio.pcm, offset, minOf(WRITE_CHUNK_BYTES, audio.pcm.size - offset))
                if (written < 0) throw IOException("Audio output failed: $written")
                offset += written
            }
            val frames = audio.pcm.size / BYTES_PER_SAMPLE
            while (track.playbackHeadPosition < frames) {
                currentCoroutineContext().ensureActive()
                delay(DRAIN_POLL_MS)
            }
        } finally {
            track.stop()
            track.release()
        }
    }

    private companion object {
        const val TAG = "SarvamVoice"
        const val TTS_URL = "https://api.sarvam.ai/text-to-speech"
        const val KEY_HEADER = "api-subscription-key"
        const val SAMPLE_RATE = 24_000

        /** A little quicker than the default, which sounds slow in conversation. */
        const val PACE = 1.1
        const val BYTES_PER_SAMPLE = 2
        const val WRITE_CHUNK_BYTES = 8_192
        const val DRAIN_POLL_MS = 30L
        const val MAX_CACHED = 64
        val JSON = "application/json".toMediaType()
    }
}

/** Splits text into sentences, so each can be fetched on its own. Numbers such as "1,500.50" stay whole. */
internal fun spokenSentences(text: String): List<String> = text
    .split(Regex("""(?<=[.!?।])\s+"""))
    .map { it.trim() }
    .filter { it.isNotEmpty() }

/** 16-bit mono PCM from a WAV file. */
internal class WavAudio(
    val sampleRate: Int,
    val pcm: ByteArray,
) {
    companion object {
        private const val HEADER_BYTES = 12
        private const val CHUNK_HEADER_BYTES = 8
        private const val SAMPLE_RATE_OFFSET = 4
        private const val ID_BYTES = 4

        fun parse(wav: ByteArray): WavAudio {
            require(wav.size > HEADER_BYTES && String(wav, 0, ID_BYTES, Charsets.US_ASCII) == "RIFF") {
                "Not a WAV file"
            }
            var offset = HEADER_BYTES
            var sampleRate = 0
            while (offset + CHUNK_HEADER_BYTES <= wav.size) {
                val id = String(wav, offset, ID_BYTES, Charsets.US_ASCII)
                val size = wav.littleEndianInt(offset + ID_BYTES)
                val start = offset + CHUNK_HEADER_BYTES
                when (id) {
                    "fmt " -> sampleRate = wav.littleEndianInt(start + SAMPLE_RATE_OFFSET)

                    "data" -> {
                        require(sampleRate > 0) { "WAV has no format chunk" }
                        // Streamed WAVs can give a size longer than the file.
                        val end = (start.toLong() + size.toUInt().toLong()).coerceAtMost(wav.size.toLong()).toInt()
                        return WavAudio(sampleRate, wav.copyOfRange(start, end))
                    }
                }
                offset = start + size + (size and 1)
            }
            throw IllegalArgumentException("WAV has no audio")
        }

        @Suppress("MagicNumber") // Byte positions and shifts of a little-endian int.
        private fun ByteArray.littleEndianInt(at: Int): Int =
            (this[at].toInt() and 0xFF) or
                ((this[at + 1].toInt() and 0xFF) shl 8) or
                ((this[at + 2].toInt() and 0xFF) shl 16) or
                ((this[at + 3].toInt() and 0xFF) shl 24)
    }
}
