package com.shivangi.gullak.core.domain.testing

import com.shivangi.gullak.core.domain.plan.PlanEngine
import com.shivangi.gullak.core.domain.usecase.AddMoneyUseCase
import com.shivangi.gullak.core.domain.usecase.ApplyReplanUseCase
import com.shivangi.gullak.core.domain.usecase.BuildPlanUseCase
import com.shivangi.gullak.core.domain.usecase.CheckTopUpUseCase
import com.shivangi.gullak.core.domain.usecase.CreateGoalUseCase
import com.shivangi.gullak.core.domain.usecase.DebitPoster
import com.shivangi.gullak.core.domain.usecase.FastForwardUseCase
import com.shivangi.gullak.core.domain.usecase.GetReplanOptionsUseCase
import com.shivangi.gullak.core.domain.usecase.GoalMoneyUseCases
import com.shivangi.gullak.core.domain.usecase.NewGoalRequest
import com.shivangi.gullak.core.domain.usecase.ObserveGoalDetailUseCase
import com.shivangi.gullak.core.domain.usecase.ObserveGoalsUseCase
import com.shivangi.gullak.core.domain.usecase.PayOutGoalUseCase
import com.shivangi.gullak.core.domain.usecase.PostNextDebitUseCase
import com.shivangi.gullak.core.domain.usecase.ResetDemoDataUseCase
import com.shivangi.gullak.core.domain.usecase.UnderstandGoalUseCase
import java.time.LocalDate

/** The domain layer wired to fakes, as Hilt would wire it to real implementations. */
class DomainTestEnvironment(
    today: LocalDate = TestData.TODAY,
    seedRequests: List<NewGoalRequest> = listOf(TestData.weddingRequest, TestData.laptopRequest),
) {
    val clock = MutableClock(today)
    val demoClock = FakeDemoClockController(clock)
    val goals = FakeGoalRepository()
    val debits = FakeDebitRepository()
    val settings = FakeSettingsRepository()
    val autoPay = FakeAutoPayGateway()
    val destinations = FakeSavingsDestinationGateway()
    val ids = SequentialIdGenerator()
    val analytics = RecordingAnalytics()
    val festivals = FakeFestivalCalendar()
    val understandingService = FakeGoalUnderstandingService()
    val explanationService = FakePlanExplanationService()
    val seedSource = FakeDemoSeedSource(seedRequests)
    val aiInfo = FakeAiInfo()
    val voice = FakeVoiceOutput()
    val speech = FakeSpeechInput()
    val bargeIn = FakeBargeIn()
    val brain = FakeConversationBrain()

    val engine = PlanEngine(clock)
    val poster = DebitPoster(engine, goals, debits, autoPay, ids, analytics)

    val understandGoal = UnderstandGoalUseCase(understandingService, festivals, analytics, clock)
    val buildPlan = BuildPlanUseCase(engine, analytics)
    val createGoal = CreateGoalUseCase(engine, goals, debits, autoPay, destinations, ids, analytics)
    val observeGoals = ObserveGoalsUseCase(goals, debits, demoClock, engine)
    val observeGoalDetail = ObserveGoalDetailUseCase(goals, debits, demoClock, engine)
    val postNextDebit = PostNextDebitUseCase(engine, goals, debits, demoClock, poster)
    val getReplanOptions = GetReplanOptionsUseCase(engine, goals, debits, analytics)
    val applyReplan = ApplyReplanUseCase(engine, goals, debits, analytics)
    val addMoney = AddMoneyUseCase(engine, goals, debits, ids, analytics)
    val payOut = PayOutGoalUseCase(engine, goals, debits, destinations, ids, analytics)
    val checkTopUp = CheckTopUpUseCase(engine, goals, debits, analytics)
    val money = GoalMoneyUseCases(addMoney, payOut, checkTopUp)
    val fastForward = FastForwardUseCase(engine, demoClock, poster)
    val resetDemoData = ResetDemoDataUseCase(goals, debits, demoClock, seedSource, createGoal)
}
