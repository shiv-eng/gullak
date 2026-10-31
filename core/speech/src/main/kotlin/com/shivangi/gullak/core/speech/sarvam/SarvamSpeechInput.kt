package com.shivangi.gullak.core.speech.sarvam

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.shivangi.gullak.core.domain.ai.SpeechError
import com.shivangi.gullak.core.domain.ai.SpeechEvent
import com.shivangi.gullak.core.domain.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Base64
import javax.inject.Inject
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** What the speech-to-text socket told us. */
internal sealed interface SarvamSignal {
    data class Transcript(val text: String) : SarvamSignal

    data object SpeechStarted : SarvamSignal

    data object SpeechEnded : SarvamSignal

    data object Failed : SarvamSignal

    /** A message this client has no use for. */
    data object Other : SarvamSignal
}

// A message that cannot be read is skipped: the turn's time limits decide what happens next.
@Suppress("SwallowedException")
internal fun sarvamSignal(message: String): SarvamSignal = try {
    val root = Json.parseToJsonElement(message).jsonObject
    val data = root["data"]?.jsonObject
    when (root["type"]?.jsonPrimitive?.content) {
        "data" -> SarvamSignal.Transcript(data?.get("transcript")?.jsonPrimitive?.content.orEmpty())

        "error" -> SarvamSignal.Failed

        "events" -> when (data?.get("signal_type")?.jsonPrimitive?.content) {
            "START_SPEECH" -> SarvamSignal.SpeechStarted
            "END_SPEECH" -> SarvamSignal.SpeechEnded
            else -> SarvamSignal.Other
        }

        else -> SarvamSignal.Other
    }
} catch (e: SerializationException) {
    SarvamSignal.Other
} catch (e: IllegalArgumentException) {
    SarvamSignal.Other
}

/** One spoken turn over the socket: what has been heard, how long to keep waiting, and how it ended. */
internal class SarvamTurn {
    var heard = ""
        private set
    private var started = false
    private var ended = false
    private var failed = false
    private var heardAfterEnd = false

    val isFinished: Boolean get() = failed || heardAfterEnd

    val waitMs: Long
        get() = when {
            ended -> AFTER_SPEECH_MS
            started -> DURING_SPEECH_MS
            else -> NO_SPEECH_MS
        }

    /** True if there is new text to show. */
    fun on(signal: SarvamSignal): Boolean {
        when (signal) {
            is SarvamSignal.Transcript -> if (signal.text.isNotBlank()) {
                heard = listOf(heard, signal.text.trim()).filter { it.isNotEmpty() }.joinToString(" ")
                started = true
                heardAfterEnd = ended
                return true
            }

            SarvamSignal.SpeechStarted -> started = true

            SarvamSignal.SpeechEnded -> ended = true

            SarvamSignal.Failed -> failed = true

            SarvamSignal.Other -> Unit
        }
        return false
    }

    fun result(): SpeechEvent = when {
        heard.isNotEmpty() -> SpeechEvent.Final(heard)
        failed -> SpeechEvent.Error(SpeechError.UNAVAILABLE)
        else -> SpeechEvent.Error(SpeechError.NO_SPEECH)
    }

    private companion object {
        const val NO_SPEECH_MS = 8_000L
        const val DURING_SPEECH_MS = 15_000L
        const val AFTER_SPEECH_MS = 2_500L
    }
}

/** Loudness of 16-bit PCM from 0 to 1, for the listening animation. */
@Suppress("MagicNumber") // Byte handling of 16-bit samples.
internal fun pcmLevel(
    pcm: ByteArray,
    length: Int,
): Float {
    if (length < 2) return 0f
    var sum = 0.0
    for (i in 0 until length - 1 step 2) {
        val sample = (pcm[i].toInt() and 0xFF) or (pcm[i + 1].toInt() shl 8)
        sum += (sample * sample).toDouble()
    }
    val rms = sqrt(sum / (length / 2))
    return (rms / LOUD_RMS).toFloat().coerceIn(0f, 1f)
}

private const val LOUD_RMS = 6_000.0

/**
 * Sarvam's Saaras over its WebSocket: streams the microphone and lets the server detect the end of speech. The
 * language is auto-detected, so Hindi, Hinglish and English all work without a setting.
 */
internal class SarvamSpeechInput @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: SarvamConfig,
    @SarvamHttp private val client: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    fun listen(): Flow<SpeechEvent> = callbackFlow {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            trySend(SpeechEvent.Error(SpeechError.PERMISSION))
            close()
            return@callbackFlow
        }
        val signals = Channel<SarvamSignal>(Channel.UNLIMITED)
        val socket = client.newWebSocket(request(), listener(signals))
        val microphone = launch(ioDispatcher) { streamMicrophone(socket) }
        launch {
            trySend(SpeechEvent.Listening)
            follow(signals, socket)
            close()
        }
        awaitClose {
            microphone.cancel()
            socket.cancel()
        }
    }

    private fun request(): Request {
        val url = "https://api.sarvam.ai/speech-to-text/ws".toHttpUrl().newBuilder()
            .addQueryParameter("language-code", "unknown")
            .addQueryParameter("model", config.sttModel)
            .addQueryParameter("sample_rate", SAMPLE_RATE.toString())
            .addQueryParameter("input_audio_codec", "pcm_s16le")
            .addQueryParameter("vad_signals", "true")
            .addQueryParameter("flush_signal", "true")
            .build()
        return Request.Builder().url(url).header("Api-Subscription-Key", config.apiKey).build()
    }

    private fun listener(signals: Channel<SarvamSignal>) = object : WebSocketListener() {
        override fun onMessage(
            webSocket: WebSocket,
            text: String,
        ) {
            signals.trySend(sarvamSignal(text))
        }

        override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?,
        ) {
            Log.w(TAG, "Sarvam speech failed: ${response?.code}", t)
            signals.trySend(SarvamSignal.Failed)
        }
    }

    /** Turns socket messages into speech events, with limits so silence or a stalled server never hangs the talk. */
    private suspend fun ProducerScope<SpeechEvent>.follow(
        signals: Channel<SarvamSignal>,
        socket: WebSocket,
    ) {
        val turn = SarvamTurn()
        withTimeoutOrNull(MAX_TURN_MS) {
            while (!turn.isFinished) {
                val signal = withTimeoutOrNull(turn.waitMs) { signals.receive() } ?: break
                if (signal == SarvamSignal.SpeechEnded) socket.send(FLUSH)
                if (turn.on(signal)) trySend(SpeechEvent.Partial(turn.heard))
            }
        }
        trySend(turn.result())
    }

    // The permission is checked in listen() before this runs.
    @SuppressLint("MissingPermission")
    private suspend fun ProducerScope<SpeechEvent>.streamMicrophone(socket: WebSocket) {
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            maxOf(minimum, CHUNK_BYTES * 2),
        )
        try {
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                trySend(SpeechEvent.Error(SpeechError.FAILED))
                return
            }
            recorder.startRecording()
            val buffer = ByteArray(CHUNK_BYTES)
            while (isActive) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                trySend(SpeechEvent.Level(pcmLevel(buffer, read)))
                socket.send(audioMessage(buffer, read))
            }
        } finally {
            recorder.release()
        }
    }

    private fun audioMessage(
        pcm: ByteArray,
        length: Int,
    ): String = buildJsonObject {
        putJsonObject("audio") {
            put("data", Base64.getEncoder().encodeToString(pcm.copyOf(length)))
            put("sample_rate", SAMPLE_RATE.toString())
            put("encoding", "audio/wav")
        }
    }.toString()

    private companion object {
        const val TAG = "SarvamSpeech"
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        /** 100 ms of 16-bit audio at 16 kHz. */
        const val CHUNK_BYTES = 3_200
        const val MAX_TURN_MS = 30_000L
        const val FLUSH = """{"type":"flush"}"""
    }
}
