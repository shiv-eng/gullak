package com.gullak.core.speech

import android.speech.SpeechRecognizer
import com.gullak.core.domain.ai.SpeechError
import com.gullak.core.model.ai.GoalLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidSpeechInputTest {

    @Test
    fun moneyIsWrittenTheWayItIsSaid() {
        assertEquals("हर महीने 4,500 रुपये", speakable("हर महीने ₹4,500", GoalLanguage.HINDI))
        assertEquals("1,50,000 rupees a month", speakable("₹1,50,000 a month", GoalLanguage.ENGLISH))
    }

    @Test
    fun silenceIsNotAFailure() {
        assertEquals(SpeechError.NO_SPEECH, speechError(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(SpeechError.NO_SPEECH, speechError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
    }

    @Test
    fun aMissingPermissionAndANetworkFailureAreReportedAsSuch() {
        assertEquals(SpeechError.PERMISSION, speechError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
        assertEquals(SpeechError.FAILED, speechError(SpeechRecognizer.ERROR_NETWORK))
    }

    @Test
    fun aBusyOrUnknownRecogniserIsUnavailable() {
        assertEquals(SpeechError.UNAVAILABLE, speechError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertEquals(SpeechError.UNAVAILABLE, speechError(UNKNOWN_ERROR))
    }

    @Test
    fun loudnessIsScaledToBetweenZeroAndOne() {
        assertEquals(0f, speechLevel(-5f), 0f)
        assertEquals(0.5f, speechLevel(4f), 0f)
        assertEquals(1f, speechLevel(20f), 0f)
    }

    private companion object {
        const val UNKNOWN_ERROR = 999
    }
}
