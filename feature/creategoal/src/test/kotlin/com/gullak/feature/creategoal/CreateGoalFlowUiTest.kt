package com.gullak.feature.creategoal

import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.gullak.core.designsystem.theme.GullakTheme
import com.gullak.core.domain.testing.DomainTestEnvironment
import com.gullak.core.domain.testing.TestData
import com.gullak.core.domain.testing.TestData.rupees
import com.gullak.core.model.goal.Frequency
import com.gullak.feature.creategoal.voice.TalkIo
import java.time.Duration
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Walks the real screen and ViewModel from the form to the success page, as a person would. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class CreateGoalFlowUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val env = DomainTestEnvironment()

    private fun text(
        id: Int,
        vararg args: Any,
    ): String = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id, *args)

    /** The ViewModel debounces explanations with `delay`, which a paused Robolectric looper never reaches alone. */
    private fun settle() {
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        compose.waitForIdle()
    }

    private fun tap(label: String) {
        settle()
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(label).performClick()
        settle()
    }

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) = CreateGoalViewModel(
        savedState = savedState,
        understandGoal = env.understandGoal,
        buildPlan = env.buildPlan,
        createGoal = env.createGoal,
        explainer = env.explanationService,
        analytics = env.analytics,
        io = TalkIo(env.voice, env.speech, env.settings, env.bargeIn, env.analytics),
        clock = env.clock,
    )

    private fun show(viewModel: CreateGoalViewModel) {
        compose.setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            GullakTheme(darkTheme = false) {
                CreateGoalScreen(state = state, onEvent = viewModel::onEvent, onBack = {
                }, onSpeak = {}, onOpenVoiceSettings = {})
            }
        }
    }

    private fun dayNode(day: String) =
        compose.onAllNodes(hasText(day, substring = true) or hasContentDescription(day, substring = true)).onFirst()

    /** Dates up to the demo date cannot be picked, so the calendar must open on a month that has dates to pick. */
    @Test
    fun theCalendarOpensOnAMonthWithDatesAndADayCanBePicked() {
        val viewModel = viewModel()
        show(viewModel)

        // At this size the field is behind the bottom button until the list is scrolled.
        compose.onNodeWithText(text(R.string.create_date_hint)).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        // Six days from the demo date is too close for a plan; seven is the first date offered.
        dayNode("November 6, 2026").assertIsNotEnabled()
        dayNode("November 7, 2026").assertIsEnabled()
        val day = "November 30, 2026"
        dayNode(day).performClick()
        tap(text(R.string.create_date_ok))

        assertEquals(LocalDate.of(2026, 11, 30), viewModel.uiState.value.targetDate)
    }

    @Test
    fun typingANameSuggestsGoalsAndATapFillsIt() {
        val viewModel = viewModel()
        show(viewModel)

        compose.onAllNodes(hasSetTextAction())[0].performTextInput("lap")
        tap("Laptop for college")

        assertEquals("Laptop for college", viewModel.uiState.value.name)
    }

    @Test
    fun aTypedGoalGoesFromFormThroughApprovalToASavedGullak() {
        val viewModel = viewModel()
        show(viewModel)

        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("Laptop")
        fields[1].performTextInput("36000")
        // The date is set through the event: the Material date picker is not what this test is about.
        viewModel.onEvent(CreateGoalEvent.DateSelected(TestData.LAPTOP_DATE))

        tap(text(R.string.create_start_with, "₹4,500"))
        tap(text(R.string.mandate_confirm))
        tap(text(R.string.bank_proceed))
        listOf("1", "2", "3", "4").forEach(::tap)

        compose.waitUntil(TIMEOUT_MS) {
            settle()
            compose.onAllNodes(hasText(text(R.string.success_title))).fetchSemanticsNodes().isNotEmpty()
        }

        val goal = env.goals.goals.single()
        assertEquals("Laptop", goal.name)
        assertEquals(rupees(36_000), goal.target)
        assertEquals(Frequency.MONTHLY, goal.schedule?.frequency)
        assertEquals(rupees(4_500), goal.schedule?.amountPerDebit)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
