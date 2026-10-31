package com.shivangi.gullak.core.speech

import com.shivangi.gullak.core.domain.ai.SpeechError
import com.shivangi.gullak.core.domain.ai.SpeechEvent
import com.shivangi.gullak.core.domain.ai.SpeechInput
import com.shivangi.gullak.core.domain.ai.VoiceOutput
import com.shivangi.gullak.core.model.ai.GoalLanguage
import com.shivangi.gullak.core.speech.gemini.GeminiVoiceOutput
import com.shivangi.gullak.core.speech.sarvam.SarvamConfig
import com.shivangi.gullak.core.speech.sarvam.SarvamSpeechInput
import com.shivangi.gullak.core.speech.sarvam.SarvamVoiceOutput
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Sarvam first; with no key, or when it cannot be reached, the phone's own speech recogniser. */
internal class FallbackSpeechInput @Inject constructor(
    private val config: SarvamConfig,
    private val sarvam: SarvamSpeechInput,
    private val phone: AndroidSpeechInput,
) : SpeechInput {
    override fun listen(languageTag: String): Flow<SpeechEvent> {
        if (!config.isConfigured) return phone.listen(languageTag)
        return flow {
            sarvam.listen().collect { event ->
                if (event == SpeechEvent.Error(SpeechError.UNAVAILABLE)) {
                    emitAll(phone.listen(languageTag))
                } else {
                    emit(event)
                }
            }
        }
    }
}

/** Money the way people say it: "₹4,500" is read as "4,500 रुपये", not as a symbol and a number. */
internal fun speakable(
    text: String,
    language: GoalLanguage,
): String = RUPEES.replace(text) { match ->
    "${match.groupValues[1]} ${if (language == GoalLanguage.ENGLISH) "rupees" else "रुपये"}"
}

private val RUPEES = Regex("""₹\s*([\d,]+(?:\.\d+)?)""")

/** Sarvam first, then Gemini's voice, then the phone's own text-to-speech: whichever can speak right now. */
internal class FallbackVoiceOutput @Inject constructor(
    private val config: SarvamConfig,
    private val sarvam: SarvamVoiceOutput,
    private val gemini: GeminiVoiceOutput,
    private val phone: TextToSpeechVoiceOutput,
) : VoiceOutput {
    override suspend fun speak(
        text: String,
        language: GoalLanguage,
    ) {
        val spoken = speakable(text, language)
        if (config.isConfigured && sarvam.speak(spoken, language)) return
        if (gemini.speak(spoken, language)) return
        phone.speak(spoken, language)
    }

    /** Sarvam's voices cover every language Gullak speaks; only the phone's engine can lack one. */
    override suspend fun isVoiceAvailable(language: GoalLanguage): Boolean =
        config.isConfigured || phone.isVoiceAvailable(language)

    override fun stop() {
        phone.stop()
    }
}
