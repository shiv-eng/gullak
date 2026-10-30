package com.gullak.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gullak.core.domain.usecase.GoalSummary
import com.gullak.core.domain.usecase.GoalsOverview
import com.gullak.core.domain.usecase.ObserveGoalsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val isLoading: Boolean = true,
    val overview: GoalsOverview? = null,
) {
    val goals: List<GoalSummary> get() = overview?.goals.orEmpty()
}

/** Gullak's own page: every Goal SIP with saved amount, status and next SIP. */
@HiltViewModel
class HomeViewModel @Inject constructor(observeGoals: ObserveGoalsUseCase) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = observeGoals()
        .map { HomeUiState(isLoading = false, overview = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
