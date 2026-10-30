package com.gullak.core.speech.cloud

import java.io.IOException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A voice service failing must mean "try the next voice", never a crash: this crashed the app on a rate limit. */
@RunWith(RobolectricTestRunner::class)
class CloudSpeakerTest {

    @Test
    fun aRefusedRequestReturnsFalseInsteadOfThrowing() = runTest {
        val speaker = CloudSpeaker(StandardTestDispatcher(testScheduler))

        val spoke = speaker.speak("Pehla. Doosra.", "test") { throw IOException("HTTP 429") }

        assertFalse(spoke)
    }

    @Test
    fun aRefusedRequestDoesNotReachTheCallersScope() = runTest {
        var uncaught: Throwable? = null
        val scope = CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(testScheduler) +
                CoroutineExceptionHandler { _, error -> uncaught = error },
        )
        val speaker = CloudSpeaker(StandardTestDispatcher(testScheduler))
        var spoke: Boolean? = null

        scope.launch { spoke = speaker.speak("Pehla. Doosra.", "test") { throw IOException("HTTP 429") } }
        testScheduler.advanceUntilIdle()

        assertNull(uncaught)
        assertEquals(false, spoke)
    }

    @Test
    fun unreadableAudioAlsoMeansTryTheNextVoice() = runTest {
        val speaker = CloudSpeaker(StandardTestDispatcher(testScheduler))

        assertFalse(speaker.speak("Ek.", "test", bySentence = false) { WavAudio.parse(ByteArray(40)) })
    }
}
