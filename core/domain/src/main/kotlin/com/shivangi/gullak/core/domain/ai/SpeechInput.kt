package com.shivangi.gullak.core.domain.ai

import kotlinx.coroutines.flow.Flow

sealed interface SpeechEvent {
    data object Listening : SpeechEvent

    /** Loudness from 0 to 1, for the listening animation. */
    data class Level(val level: Float) : SpeechEvent

    data class Partial(val text: String) : SpeechEvent

    data class Final(val text: String) : SpeechEvent

    data class Error(val type: SpeechError) : SpeechEvent
}

enum class SpeechError { NO_SPEECH, PERMISSION, UNAVAILABLE, FAILED }

/** Turns speech into text inside the app. */
interface SpeechInput {
    /**
     * Listens once. The flow completes after [SpeechEvent.Final] or [SpeechEvent.Error]; cancelling it stops listening.
     * Collect on the main thread, as Android's recogniser requires.
     */
    fun listen(languageTag: String): Flow<SpeechEvent>
}
