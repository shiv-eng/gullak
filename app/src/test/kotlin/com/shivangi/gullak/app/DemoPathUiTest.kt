package com.shivangi.gullak.app

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The demo path through the real app and Hilt graph, in demo mode: laptop goal, monthly, AutoPay steps, goal detail.
 * Only the settings file is replaced ([InMemoryPreferencesModule]).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp")
class DemoPathUiTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    /** Debounces and gateway delays use `delay`, which a paused Robolectric looper never reaches alone. */
    private fun settle() {
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        compose.waitForIdle()
    }

    private fun waitForText(
        label: String,
        substring: Boolean = false,
    ) {
        compose.waitUntil(TIMEOUT_MS) {
            settle()
            compose.onAllNodesWithText(label, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tap(label: String) {
        waitForText(label)
        compose.onAllNodesWithText(label).onFirst().performClick()
        settle()
    }

    private fun enterTargetDate() {
        waitForText("Select date")
        // At this size the field is behind the bottom button until the list is scrolled.
        compose.onAllNodesWithText("Select date").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onAllNodesWithContentDescription("text input mode", substring = true).onFirst().performClick()
        settle()
        compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("06302027")
        tap("OK")
    }

    @Test
    fun laptopGoalReachesGoalDetailOnTrackWithNothingSaved() {
        waitForText("Start a Gullak")
        compose.onAllNodesWithText("Start a Gullak").onFirst().performScrollTo().performClick()
        settle()

        // The voice conversation is the way in; this test goes on by typing.
        tap("Type instead")
        waitForText("Goal name")
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("Laptop")
        fields[1].performTextInput("36000")
        enterTargetDate()

        tap("Start with ₹4,500")
        tap("Continue")
        tap("Proceed to approve")
        listOf("1", "2", "3", "4").forEach(::tap)

        waitForText("Gullak started")
        tap("View Gullaks")
        tap("Laptop")

        waitForText("Saved so far")
        compose.onNode(hasText("₹0")).assertExists()
        compose.onNode(hasText("of ₹36,000", substring = true)).assertExists()
        compose.onAllNodesWithText("On track").onFirst().assertExists()
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
