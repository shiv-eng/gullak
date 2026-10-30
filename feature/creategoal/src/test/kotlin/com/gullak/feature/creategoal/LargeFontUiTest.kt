package com.gullak.feature.creategoal

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import com.gullak.core.designsystem.theme.GullakTheme
import com.gullak.feature.creategoal.voice.TalkPhase
import com.gullak.feature.creategoal.voice.VoiceGoalScreen
import com.gullak.feature.creategoal.voice.VoiceGoalUiState
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The main voice and form screens still show their main controls at the 200% font size the brief asks for. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class LargeFontUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                GullakTheme(darkTheme = false) { content() }
            }
        }
    }

    @Test
    fun theVoiceScreenKeepsItsWayOut() {
        show {
            VoiceGoalScreen(
                state = VoiceGoalUiState(phase = TalkPhase.WAITING, gullakSaid = "Namaste"),
                onEvent = {},
                onBack = {},
                onTypeInstead = {},
                onOpenVoiceSettings = {},
            )
        }

        compose.onNodeWithText("Namaste").assertIsDisplayed()
        compose.onNodeWithText("Type instead").assertIsDisplayed()
    }

    @Test
    fun theFormKeepsItsMainButton() {
        show {
            CreateGoalScreen(
                state = CreateGoalUiState(today = LocalDate.of(2026, 10, 31)),
                onEvent = {},
                onBack = {},
                onSpeak = {},
                onOpenVoiceSettings = {},
            )
        }

        compose.onNodeWithText("Goal name").assertIsDisplayed()
    }
}
