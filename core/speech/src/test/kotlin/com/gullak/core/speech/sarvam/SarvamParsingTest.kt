package com.gullak.core.speech.sarvam

import com.gullak.core.domain.ai.SpeechError
import com.gullak.core.domain.ai.SpeechEvent
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SarvamParsingTest {

    @Test
    fun transcriptsAndSpeechSignalsAreRead() {
        assertEquals(
            SarvamSignal.Transcript("laptop ke liye"),
            sarvamSignal("""{"type":"data","data":{"request_id":"1","transcript":"laptop ke liye"}}"""),
        )
        assertEquals(
            SarvamSignal.SpeechStarted,
            sarvamSignal("""{"type":"events","data":{"event_type":"x","signal_type":"START_SPEECH"}}"""),
        )
        assertEquals(
            SarvamSignal.SpeechEnded,
            sarvamSignal("""{"type":"events","data":{"event_type":"x","signal_type":"END_SPEECH"}}"""),
        )
        assertEquals(SarvamSignal.Failed, sarvamSignal("""{"type":"error","data":{"error":"no","code":"x"}}"""))
    }

    @Test
    fun anUnreadableMessageIsIgnoredNotACrash() {
        assertEquals(SarvamSignal.Other, sarvamSignal("not json"))
        assertEquals(SarvamSignal.Other, sarvamSignal("""{"type":"something_new"}"""))
    }

    @Test
    fun aTurnEndsWhenTheLastWordsArriveAfterSpeechEnds() {
        val turn = SarvamTurn()
        turn.on(SarvamSignal.SpeechStarted)
        assertEquals(true, turn.on(SarvamSignal.Transcript("laptop ke liye")))
        assertEquals(false, turn.isFinished)

        turn.on(SarvamSignal.SpeechEnded)
        turn.on(SarvamSignal.Transcript("36 hazaar"))

        assertEquals(true, turn.isFinished)
        assertEquals(SpeechEvent.Final("laptop ke liye 36 hazaar"), turn.result())
    }

    @Test
    fun aTurnWithNothingHeardIsSilenceAndAFailureAsksForTheFallback() {
        assertEquals(SpeechEvent.Error(SpeechError.NO_SPEECH), SarvamTurn().result())
        val failed = SarvamTurn().apply { on(SarvamSignal.Failed) }
        assertEquals(true, failed.isFinished)
        assertEquals(SpeechEvent.Error(SpeechError.UNAVAILABLE), failed.result())
    }

    @Test
    fun aFailureAfterHearingSomethingStillUsesWhatWasHeard() {
        val turn = SarvamTurn().apply {
            on(SarvamSignal.Transcript("laptop"))
            on(SarvamSignal.Failed)
        }
        assertEquals(SpeechEvent.Final("laptop"), turn.result())
    }

    @Test
    fun loudnessGrowsWithTheSignalAndStaysInRange() {
        val silence = ByteArray(100)
        val loud = ByteBuffer.allocate(100).order(ByteOrder.LITTLE_ENDIAN).apply {
            repeat(50) { putShort(20_000) }
        }.array()

        assertEquals(0f, pcmLevel(silence, silence.size), 0f)
        assertEquals(1f, pcmLevel(loud, loud.size), 0f)
    }

    @Test
    fun textIsSplitIntoSentencesKeepingNumbersWhole() {
        assertEquals(
            listOf("Ho gaya!", "Pehli kist ₹4,500.50 ki hai.", "सही है?", "अच्छा।", "Theek"),
            spokenSentences("Ho gaya! Pehli kist ₹4,500.50 ki hai. सही है? अच्छा। Theek"),
        )
    }

    @Test
    fun aWavFileGivesItsSampleRateAndAudioSkippingOtherChunks() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val audio = WavAudio.parse(wav(24_000, pcm))

        assertEquals(24_000, audio.sampleRate)
        assertArrayEquals(pcm, audio.pcm)
    }

    @Test(expected = IllegalArgumentException::class)
    fun somethingElseIsRejected() {
        WavAudio.parse(ByteArray(40))
    }

    private fun wav(
        rate: Int,
        pcm: ByteArray,
    ): ByteArray = ByteArrayOutputStream().apply {
        fun int(value: Int) = write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
        fun short(value: Int) =
            write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
        write("RIFF".toByteArray())
        int(0)
        write("WAVE".toByteArray())
        write("LIST".toByteArray())
        int(3)
        write(byteArrayOf(0, 0, 0, 0))
        write("fmt ".toByteArray())
        int(16)
        short(1)
        short(1)
        int(rate)
        int(rate * 2)
        short(2)
        short(16)
        write("data".toByteArray())
        int(pcm.size)
        write(pcm)
    }.toByteArray()
}
