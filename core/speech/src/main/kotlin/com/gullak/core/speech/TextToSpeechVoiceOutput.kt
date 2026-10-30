package com.gullak.core.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.gullak.core.domain.ai.VoiceOutput
import com.gullak.core.model.ai.GoalLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Android text-to-speech, started on first use. Hindi and Hinglish use a hi-IN voice and English en-IN; if the
 * phone has no such voice, the engine's current voice is used.
 */
@Singleton
internal class TextToSpeechVoiceOutput @Inject constructor(@ApplicationContext private val context: Context) :
    VoiceOutput {

    private val ready = CompletableDeferred<Boolean>()
    private var engine: TextToSpeech? = null

    override suspend fun speak(
        text: String,
        language: GoalLanguage,
    ) {
        val tts = engine ?: TextToSpeech(context) { status ->
            ready.complete(status == TextToSpeech.SUCCESS)
        }.also { engine = it }
        if (!ready.await()) return

        val locale = Locale.forLanguageTag(if (language == GoalLanguage.ENGLISH) ENGLISH_INDIA else HINDI_INDIA)
        if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.language = locale

        suspendCancellableCoroutine { continuation ->
            tts.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) = finish()

                    @Deprecated("Required by the abstract class", ReplaceWith("finish()"))
                    override fun onError(utteranceId: String?) = finish()

                    override fun onStop(
                        utteranceId: String?,
                        interrupted: Boolean,
                    ) = finish()

                    private fun finish() {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                },
            )
            continuation.invokeOnCancellation { tts.stop() }
            val queued = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
            if (queued != TextToSpeech.SUCCESS && continuation.isActive) continuation.resume(Unit)
        }
    }

    override fun stop() {
        engine?.stop()
    }

    private companion object {
        const val ENGLISH_INDIA = "en-IN"
        const val HINDI_INDIA = "hi-IN"
    }
}
