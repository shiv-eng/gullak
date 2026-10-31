package com.shivangi.gullak.core.speech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechOnsetDetectorTest {

    private fun SpeechOnsetDetector.feed(
        level: Float,
        frames: Int,
    ): Boolean = (1..frames).map { heard(level) }.any { it }

    @Test
    fun gullaksOwnEchoNeverCountsAsSpeech() {
        val onset = SpeechOnsetDetector()

        assertFalse(onset.feed(0.2f, 300))
    }

    @Test
    fun aVoiceClearlyAboveTheEchoIsHeardAfterAboutFourHundredMilliseconds() {
        val onset = SpeechOnsetDetector()
        onset.feed(0.1f, 30)

        assertFalse(onset.feed(0.5f, 12))
        assertTrue(onset.heard(0.5f))
    }

    @Test
    fun aCoughOrDoorIsIgnored() {
        val onset = SpeechOnsetDetector()
        onset.feed(0.05f, 30)

        assertFalse(onset.feed(0.9f, 6))
        assertFalse(onset.feed(0.05f, 20))
        assertFalse(onset.feed(0.9f, 6))
    }

    @Test
    fun aBriefDipBetweenSyllablesIsForgiven() {
        val onset = SpeechOnsetDetector()
        onset.feed(0.05f, 30)

        assertFalse(onset.feed(0.5f, 8))
        assertFalse(onset.feed(0.05f, 2))
        assertTrue(onset.feed(0.5f, 5))
    }

    @Test
    fun aQuietRoomNeedsARealVoiceNotJustANoise() {
        val onset = SpeechOnsetDetector()
        onset.feed(0f, 30)

        assertFalse(onset.feed(0.08f, 100))
    }

    @Test
    fun soundDuringTheLearningMomentIsTakenAsEcho() {
        val onset = SpeechOnsetDetector()

        assertFalse(onset.feed(0.4f, 10))
        assertFalse(onset.feed(0.4f, 100))
    }
}
