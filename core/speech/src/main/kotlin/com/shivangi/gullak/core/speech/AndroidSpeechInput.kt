package com.shivangi.gullak.core.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.shivangi.gullak.core.domain.ai.SpeechError
import com.shivangi.gullak.core.domain.ai.SpeechEvent
import com.shivangi.gullak.core.domain.ai.SpeechInput
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Android's [SpeechRecognizer] as a flow. The recogniser is created and destroyed on the collector's main thread. */
internal class AndroidSpeechInput @Inject constructor(@ApplicationContext private val context: Context) : SpeechInput {

    override fun listen(languageTag: String): Flow<SpeechEvent> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(SpeechEvent.Error(SpeechError.UNAVAILABLE))
            close()
            return@callbackFlow
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    trySend(SpeechEvent.Listening)
                }

                override fun onRmsChanged(rmsdB: Float) {
                    trySend(SpeechEvent.Level(speechLevel(rmsdB)))
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults.bestResult()?.let { trySend(SpeechEvent.Partial(it)) }
                }

                override fun onResults(results: Bundle?) {
                    val text = results.bestResult()
                    trySend(if (text != null) SpeechEvent.Final(text) else SpeechEvent.Error(SpeechError.NO_SPEECH))
                    close()
                }

                override fun onError(error: Int) {
                    trySend(SpeechEvent.Error(speechError(error)))
                    close()
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?,
                ) = Unit
            },
        )
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true),
        )
        awaitClose {
            recognizer.cancel()
            recognizer.destroy()
        }
    }

    private fun Bundle?.bestResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }
}

/** Android reports loudness from about -2 to 10 dB. */
internal fun speechLevel(rmsdB: Float): Float = ((rmsdB - MIN_RMS_DB) / (MAX_RMS_DB - MIN_RMS_DB)).coerceIn(0f, 1f)

/** Anything not listed means this phone's recogniser cannot serve us: the caller uses the phone's speech screen. */
internal fun speechError(code: Int): SpeechError = when (code) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechError.NO_SPEECH

    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechError.PERMISSION

    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    SpeechRecognizer.ERROR_AUDIO,
    SpeechRecognizer.ERROR_SERVER,
    -> SpeechError.FAILED

    else -> SpeechError.UNAVAILABLE
}

private const val MIN_RMS_DB = -2f
private const val MAX_RMS_DB = 10f
