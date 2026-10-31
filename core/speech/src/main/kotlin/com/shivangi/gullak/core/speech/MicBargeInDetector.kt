package com.shivangi.gullak.core.speech

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import com.shivangi.gullak.core.domain.ai.BargeInDetector
import com.shivangi.gullak.core.domain.di.IoDispatcher
import com.shivangi.gullak.core.speech.sarvam.pcmLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Listens to the microphone while Gullak speaks. The hard part is that the microphone also hears Gullak, so this uses
 * the phone's echo cancelling, measures how loud the leftover echo is, and only reacts to sustained sound well above
 * it. No audio leaves the phone or is kept: only its loudness is looked at.
 */
internal class MicBargeInDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BargeInDetector {

    override suspend fun awaitSpeech() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) awaitCancellation()
        withContext(ioDispatcher) { watchMicrophone() }
    }

    // The permission is checked in awaitSpeech() before this runs.
    @SuppressLint("MissingPermission")
    private suspend fun watchMicrophone() {
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            maxOf(minimum, FRAME_BYTES * 2),
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            awaitCancellation()
        }
        val echoCanceler =
            effectIf(AcousticEchoCanceler.isAvailable()) { AcousticEchoCanceler.create(recorder.audioSessionId) }
        val noiseSuppressor =
            effectIf(NoiseSuppressor.isAvailable()) { NoiseSuppressor.create(recorder.audioSessionId) }
        try {
            echoCanceler?.enabled = true
            noiseSuppressor?.enabled = true
            recorder.startRecording()
            val onset = SpeechOnsetDetector()
            val frame = ByteArray(FRAME_BYTES)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = recorder.read(frame, 0, frame.size)
                if (read > 0 && onset.heard(pcmLevel(frame, read))) return
            }
        } finally {
            echoCanceler?.release()
            noiseSuppressor?.release()
            recorder.stop()
            recorder.release()
        }
    }

    private fun <T> effectIf(
        available: Boolean,
        create: () -> T?,
    ): T? = if (available) create() else null

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        /** 30 ms of 16-bit audio at 16 kHz. */
        const val FRAME_BYTES = 960
    }
}

/**
 * Decides, one 30 ms frame at a time, whether someone has started speaking over the echo of Gullak's own voice. It
 * learns the echo level first and follows it slowly, then wants about 400 ms of sound clearly above it. Short noises
 * such as a cough or a door are ignored, and a brief dip between syllables is forgiven.
 */
internal class SpeechOnsetDetector {
    private var frames = 0
    private var echo = 0f
    private var loud = 0
    private var dips = 0

    fun heard(level: Float): Boolean {
        frames++
        if (frames <= LEARNING_FRAMES) {
            echo = maxOf(echo, level)
            return false
        }
        val threshold = maxOf(MIN_LEVEL, echo * ABOVE_ECHO)
        if (level >= threshold) {
            loud++
            dips = 0
        } else {
            dips++
            if (dips > ALLOWED_DIPS) loud = 0
            echo += (level - echo) * ECHO_FOLLOW
        }
        return loud >= SPEECH_FRAMES
    }

    private companion object {
        /** About 300 ms. */
        const val LEARNING_FRAMES = 10

        /** About 400 ms. */
        const val SPEECH_FRAMES = 13
        const val ALLOWED_DIPS = 2
        const val MIN_LEVEL = 0.12f
        const val ABOVE_ECHO = 2.5f
        const val ECHO_FOLLOW = 0.05f
    }
}
