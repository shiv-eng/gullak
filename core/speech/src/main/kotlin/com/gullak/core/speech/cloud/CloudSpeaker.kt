package com.gullak.core.speech.cloud

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.gullak.core.domain.di.IoDispatcher
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

/**
 * Plays voices that come from a server. Sentences are fetched together and played in order, so the first words come
 * quickly; lines already said are cached, which saves both time and credits.
 */
@Singleton
internal class CloudSpeaker @Inject constructor(@IoDispatcher private val ioDispatcher: CoroutineDispatcher) {

    private val cache = ConcurrentHashMap<String, WavAudio>()

    /** False if the voice could not speak the text, so the caller can try the next one. */
    suspend fun speak(
        text: String,
        cacheKey: String,
        fetch: suspend (String) -> WavAudio,
    ): Boolean = withContext(ioDispatcher) {
        try {
            val clips = spokenSentences(text).map { sentence ->
                async {
                    val key = "$cacheKey|$sentence"
                    cache[key] ?: fetch(sentence).also { if (cache.size < MAX_CACHED) cache[key] = it }
                }
            }
            clips.forEach { play(it.await()) }
            true
        } catch (e: IOException) {
            Log.w(TAG, "Voice failed", e)
            false
        } catch (e: SerializationException) {
            Log.w(TAG, "Voice reply not readable", e)
            false
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Voice reply not usable", e)
            false
        }
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
        const val TAG = "CloudSpeaker"
        const val BYTES_PER_SAMPLE = 2
        const val WRITE_CHUNK_BYTES = 8_192
        const val DRAIN_POLL_MS = 30L
        const val MAX_CACHED = 64
    }
}

/** Splits text into sentences, so each can be fetched on its own. Numbers such as "1,500.50" stay whole. */
internal fun spokenSentences(text: String): List<String> = text
    .split(Regex("""(?<=[.!?।])\s+"""))
    .map { it.trim() }
    .filter { it.isNotEmpty() }

/** 16-bit mono PCM, from a WAV file or raw. */
internal class WavAudio(
    val sampleRate: Int,
    val pcm: ByteArray,
) {
    companion object {
        private const val HEADER_BYTES = 12
        private const val CHUNK_HEADER_BYTES = 8
        private const val SAMPLE_RATE_OFFSET = 4
        private const val ID_BYTES = 4

        fun isWav(audio: ByteArray): Boolean =
            audio.size > HEADER_BYTES && String(audio, 0, ID_BYTES, Charsets.US_ASCII) == "RIFF"

        fun parse(wav: ByteArray): WavAudio {
            require(isWav(wav)) { "Not a WAV file" }
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
