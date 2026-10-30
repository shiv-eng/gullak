package com.gullak.core.domain.ai

import com.gullak.core.model.ai.GoalLanguage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select

/** Notices the user starting to talk over Gullak. */
interface BargeInDetector {
    /** Returns when someone starts speaking. Suspends for ever if the microphone cannot be used. */
    suspend fun awaitSpeech()
}

/**
 * Speaks [text] until it ends or the user talks over it, whichever comes first. True if the user talked over it. The
 * microphone is released before this returns, so the caller can start listening straight away.
 */
suspend fun VoiceOutput.speakUnlessInterrupted(
    text: String,
    language: GoalLanguage,
    bargeIn: BargeInDetector,
): Boolean = coroutineScope {
    val speaking = async {
        speak(text, language)
        false
    }
    val listening = async {
        bargeIn.awaitSpeech()
        true
    }
    val interrupted = select {
        speaking.onAwait { it }
        listening.onAwait { it }
    }
    speaking.cancel()
    listening.cancel()
    if (interrupted) stop()
    interrupted
}
