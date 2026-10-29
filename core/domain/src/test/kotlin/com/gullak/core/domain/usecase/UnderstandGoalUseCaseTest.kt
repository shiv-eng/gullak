package com.gullak.core.domain.usecase

import com.gullak.core.domain.ai.FestivalMatch
import com.gullak.core.domain.ai.FollowUpQuestion
import com.gullak.core.domain.ai.FollowUpReason
import com.gullak.core.domain.ai.Understanding
import com.gullak.core.domain.analytics.AnalyticsEvent
import com.gullak.core.domain.testing.FakeFestivalCalendar
import com.gullak.core.domain.testing.FakeGoalUnderstandingService
import com.gullak.core.domain.testing.MutableClock
import com.gullak.core.domain.testing.RecordingAnalytics
import com.gullak.core.domain.testing.TestData.TODAY
import com.gullak.core.domain.testing.TestData.WEDDING_DATE
import com.gullak.core.domain.testing.TestData.rupees
import com.gullak.core.model.ai.ConversationTurn
import com.gullak.core.model.ai.GoalLanguage
import com.gullak.core.model.ai.ParsedGoal
import com.gullak.core.model.ai.Speaker
import com.gullak.core.model.money.Money
import com.gullak.core.model.result.AppError
import com.gullak.core.model.result.AppResult
import com.gullak.core.model.result.getOrNull
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnderstandGoalUseCaseTest {

    private val diwali2026 = LocalDate.of(2026, 11, 8)
    private val service = FakeGoalUnderstandingService()
    private val analytics = RecordingAnalytics()
    private val useCase = UnderstandGoalUseCase(
        service,
        FakeFestivalCalendar(mapOf("Diwali" to diwali2026)),
        analytics,
        MutableClock(TODAY),
    )
    private val conversation = listOf(ConversationTurn(Speaker.USER, "Bhai ki shaadi, agle December tak, 50 hazaar"))

    private fun parsed(
        name: String? = "Bhai ki shaadi",
        amount: Money? = rupees(50_000),
        date: LocalDate? = WEDDING_DATE,
        dateHint: String? = null,
        followUp: String? = null,
    ) = ParsedGoal(name, amount, date, dateHint, GoalLanguage.HINGLISH, followUp)

    private suspend fun understand(parsed: ParsedGoal): Understanding {
        service.result = AppResult.Success(parsed)
        return requireNotNull(useCase(conversation).getOrNull())
    }

    @Test
    fun completeGoalIsReadyToConfirm() = runTest {
        val result = understand(parsed())

        assertTrue(result is Understanding.ReadyToConfirm)
        result as Understanding.ReadyToConfirm
        assertEquals("Bhai ki shaadi", result.goalName)
        assertEquals(rupees(50_000), result.target)
        assertEquals(WEDDING_DATE, result.targetDate)
        assertEquals(GoalLanguage.HINGLISH, result.language)
        assertNull(result.festival)
        assertTrue(analytics.events.isEmpty())
        assertEquals(conversation, service.requests.single())
    }

    @Test
    fun amountBoundariesAreInclusive() = runTest {
        assertTrue(understand(parsed(amount = rupees(500))) is Understanding.ReadyToConfirm)
        assertTrue(understand(parsed(amount = rupees(50_00_000))) is Understanding.ReadyToConfirm)
    }

    @Test
    fun amountOutsideTheRangeBecomesAFollowUp() = runTest {
        for (amount in listOf(rupees(499), rupees(50_00_001))) {
            val result = understand(parsed(amount = amount)) as Understanding.NeedsFollowUp

            assertEquals(FollowUpQuestion.Standard(FollowUpReason.AMOUNT_OUT_OF_RANGE), result.question)
            assertNull(result.parsed.targetAmount)
        }
        assertEquals(2, analytics.events.count { it == AnalyticsEvent.FollowUpAsked })
    }

    @Test
    fun dateMustBeAfterTodayAndWithinTenYears() = runTest {
        val past = understand(parsed(date = TODAY)) as Understanding.NeedsFollowUp
        val tooFar = understand(parsed(date = TODAY.plusYears(10).plusDays(1))) as Understanding.NeedsFollowUp

        assertEquals(FollowUpQuestion.Standard(FollowUpReason.DATE_NOT_IN_FUTURE), past.question)
        assertEquals(FollowUpQuestion.Standard(FollowUpReason.DATE_TOO_FAR), tooFar.question)
        assertNull(tooFar.parsed.targetDate)
        assertTrue(understand(parsed(date = TODAY.plusYears(10))) is Understanding.ReadyToConfirm)
    }

    @Test
    fun missingFieldUsesTheModelsOwnQuestion() = runTest {
        val question = "Kitne paise aur kab tak?"
        val result = understand(parsed(amount = null, date = null, followUp = question))

        assertEquals(FollowUpQuestion.FromModel(question), (result as Understanding.NeedsFollowUp).question)
    }

    @Test
    fun missingFieldWithoutModelQuestionAsksForTheFirstMissing() = runTest {
        val noName = understand(parsed(name = " ")) as Understanding.NeedsFollowUp
        val noAmount = understand(parsed(amount = null)) as Understanding.NeedsFollowUp
        val noDate = understand(parsed(date = null)) as Understanding.NeedsFollowUp

        assertEquals(FollowUpQuestion.Standard(FollowUpReason.MISSING_NAME), noName.question)
        assertEquals(FollowUpQuestion.Standard(FollowUpReason.MISSING_AMOUNT), noAmount.question)
        assertEquals(FollowUpQuestion.Standard(FollowUpReason.MISSING_DATE), noDate.question)
    }

    @Test
    fun knownFestivalIsLookedUpForConfirmation() = runTest {
        val result = understand(parsed(name = "Diwali", amount = rupees(20_000), date = null, dateHint = "Diwali"))

        result as Understanding.ReadyToConfirm
        assertEquals(diwali2026, result.targetDate)
        assertEquals(FestivalMatch("Diwali", diwali2026), result.festival)
        assertNull(result.parsed.targetDate)
    }

    @Test
    fun unknownFestivalAsksForTheDate() = runTest {
        val result = understand(parsed(date = null, dateHint = "Pongal")) as Understanding.NeedsFollowUp

        assertEquals(FollowUpQuestion.Standard(FollowUpReason.MISSING_DATE), result.question)
        assertNull(result.festival)
    }

    @Test
    fun serviceFailureIsReturned() = runTest {
        service.result = AppResult.Failure(AppError.Timeout)

        assertEquals(AppResult.Failure(AppError.Timeout), useCase(conversation))
    }
}
