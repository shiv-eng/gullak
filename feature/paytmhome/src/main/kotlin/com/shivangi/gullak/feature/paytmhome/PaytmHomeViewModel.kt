package com.shivangi.gullak.feature.paytmhome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shivangi.gullak.core.domain.usecase.GoalSummary
import com.shivangi.gullak.core.domain.usecase.ObserveGoalsUseCase
import com.shivangi.gullak.core.model.money.Money
import com.shivangi.gullak.core.model.plan.GoalStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PaytmHomeUiState(
    val isLoading: Boolean = true,
    val goals: List<GoalCardUi> = emptyList(),
    val totalSaved: Money = Money.ZERO,
)

/** One goal on the Gullak card. Amounts and status come from the plan engine via the use case. */
data class GoalCardUi(
    val id: String,
    val name: String,
    val saved: Money,
    val target: Money,
    val status: GoalStatus,
    val nextDebitDate: LocalDate?,
    val nextDebitAmount: Money?,
) {
    val progress: Float get() = if (target.paise == 0L) 0f else saved.paise.toFloat() / target.paise
}

sealed interface PaytmHomeEvent {
    /** Any Paytm service other than Gullak: they are not part of the prototype. */
    data object DemoServiceClicked : PaytmHomeEvent

    data object GullakClicked : PaytmHomeEvent

    data object SettingsClicked : PaytmHomeEvent

    data object NewGoalClicked : PaytmHomeEvent

    data class GoalClicked(val goalId: String) : PaytmHomeEvent
}

sealed interface PaytmHomeEffect {
    data object ShowDemoOnly : PaytmHomeEffect

    data object OpenGullak : PaytmHomeEffect

    data object OpenSettings : PaytmHomeEffect

    data object OpenNewGoal : PaytmHomeEffect

    data class OpenGoal(val goalId: String) : PaytmHomeEffect
}

@HiltViewModel
class PaytmHomeViewModel @Inject constructor(observeGoals: ObserveGoalsUseCase) : ViewModel() {

    val uiState: StateFlow<PaytmHomeUiState> = observeGoals()
        .map { overview ->
            PaytmHomeUiState(
                isLoading = false,
                goals = overview.goals.map { it.toCard() },
                totalSaved = overview.totalSaved,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PaytmHomeUiState())

    private val effects = Channel<PaytmHomeEffect>(Channel.BUFFERED)
    val effect: Flow<PaytmHomeEffect> = effects.receiveAsFlow()

    fun onEvent(event: PaytmHomeEvent) {
        val effect = when (event) {
            PaytmHomeEvent.DemoServiceClicked -> PaytmHomeEffect.ShowDemoOnly
            PaytmHomeEvent.GullakClicked -> PaytmHomeEffect.OpenGullak
            PaytmHomeEvent.SettingsClicked -> PaytmHomeEffect.OpenSettings
            PaytmHomeEvent.NewGoalClicked -> PaytmHomeEffect.OpenNewGoal
            is PaytmHomeEvent.GoalClicked -> PaytmHomeEffect.OpenGoal(event.goalId)
        }
        viewModelScope.launch { effects.send(effect) }
    }

    private fun GoalSummary.toCard() = GoalCardUi(
        id = goal.id,
        name = goal.name,
        saved = saved,
        target = goal.target,
        status = status,
        nextDebitDate = nextDebit?.date,
        nextDebitAmount = nextDebit?.amount,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
