package com.gullak.core.speech

import com.gullak.core.domain.ai.SpeechError
import com.gullak.core.domain.ai.SpeechEvent
import com.gullak.core.domain.ai.SpeechInput
import com.gullak.core.domain.ai.VoiceOutput
import com.gullak.core.model.ai.GoalLanguage
import com.gullak.core.speech.sarvam.SarvamConfig
import com.gullak.core.speech.sarvam.SarvamSpeechInput
import com.gullak.core.speech.sarvam.SarvamVoiceOutput
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

/** Sarvam first; with no key, or when it cannot be reached, the phone's own text-to-speech. */
internal class FallbackVoiceOutput @Inject constructor(
    private val config: SarvamConfig,
    private val sarvam: SarvamVoiceOutput,
    private val phone: TextToSpeechVoiceOutput,
) : VoiceOutput {
    override suspend fun speak(
        text: String,
        language: GoalLanguage,
    ) {
        if (config.isConfigured && sarvam.speak(text, language)) return
        phone.speak(text, language)
    }

    /** Sarvam's voices cover every language Gullak speaks; only the phone's engine can lack one. */
    override suspend fun isVoiceAvailable(language: GoalLanguage): Boolean =
        config.isConfigured || phone.isVoiceAvailable(language)

    override fun stop() {
        phone.stop()
    }
}
