package com.shivangi.gullak.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shivangi.gullak.core.designsystem.component.CardDivider
import com.shivangi.gullak.core.designsystem.component.GoalSummaryRow
import com.shivangi.gullak.core.designsystem.component.GullakImage
import com.shivangi.gullak.core.designsystem.component.PaytmCard
import com.shivangi.gullak.core.designsystem.component.PillButton
import com.shivangi.gullak.core.designsystem.component.StatusTone
import com.shivangi.gullak.core.designsystem.theme.GullakTheme
import com.shivangi.gullak.core.domain.usecase.GoalSummary
import com.shivangi.gullak.core.model.goal.Destination
import com.shivangi.gullak.core.model.money.IndianMoneyFormat
import com.shivangi.gullak.core.model.plan.GoalStatus
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun HomeEntry(
    onBack: () -> Unit,
    onNewGoal: (withVoice: Boolean) -> Unit,
    onOpenGoal: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onBack = onBack,
        onNewGoal = onNewGoal,
        onOpenGoal = onOpenGoal,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    )
}

/** Gullak's page as a Paytm sub-page (like Paytm Gold): summary, Gullaks, and Start a Gullak. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    state: HomeUiState,
    onBack: () -> Unit,
    onNewGoal: (withVoice: Boolean) -> Unit,
    onOpenGoal: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = GullakTheme.colors.page,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.home_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.home_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.home_settings))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = GullakTheme.colors.page,
                    titleContentColor = GullakTheme.colors.textPrimary,
                    navigationIconContentColor = GullakTheme.colors.textPrimary,
                    actionIconContentColor = GullakTheme.colors.textPrimary,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SummaryCard(state = state, onNewGoal = onNewGoal) }
            if (state.goals.isNotEmpty()) {
                item { GoalsCard(goals = state.goals, onOpenGoal = onOpenGoal) }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    state: HomeUiState,
    onNewGoal: (withVoice: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val overview = state.overview
    PaytmCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                if (overview == null || overview.goals.isEmpty()) {
                    Text(
                        stringResource(R.string.home_empty_prompt),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = GullakTheme.colors.textPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.home_empty_hint),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = GullakTheme.colors.textBody,
                    )
                } else {
                    Text(
                        stringResource(R.string.home_total_saved),
                        style = MaterialTheme.typography.bodyMedium,
                        color = GullakTheme.colors.textMuted,
                    )
                    Text(
                        IndianMoneyFormat.format(overview.totalSaved),
                        style = GullakTheme.typography.amountLarge.copy(fontSize = 22.sp),
                        color = GullakTheme.colors.textPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(
                            R.string.home_split,
                            IndianMoneyFormat.format(overview.savedInGold),
                            IndianMoneyFormat.format(overview.savedInFund),
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = GullakTheme.colors.textBody,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            GullakImage(modifier = Modifier.size(88.dp))
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillButton(
                text = stringResource(R.string.home_new_goal),
                leadingIcon = Icons.Filled.Add,
                onClick = { onNewGoal(false) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            FilledIconButton(
                onClick = { onNewGoal(true) },
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = GullakTheme.colors.chipSelected,
                    contentColor = GullakTheme.colors.action,
                ),
            ) {
                Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.home_mic_description))
            }
        }
    }
}

@Composable
private fun GoalsCard(
    goals: List<GoalSummary>,
    onOpenGoal: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PaytmCard(modifier = modifier) {
        Text(
            stringResource(R.string.home_goals_title),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            color = GullakTheme.colors.textPrimary,
        )
        goals.forEachIndexed { index, summary ->
            if (index > 0) CardDivider() else Spacer(Modifier.height(12.dp))
            GoalItem(summary = summary, onClick = { onOpenGoal(summary.goal.id) })
        }
    }
}

@Composable
private fun GoalItem(
    summary: GoalSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (statusText, tone) = when (val status = summary.status) {
        GoalStatus.OnTrack -> stringResource(R.string.home_status_on_track) to StatusTone.SUCCESS

        GoalStatus.Reached -> stringResource(R.string.home_status_reached) to StatusTone.SUCCESS

        is GoalStatus.Behind ->
            stringResource(R.string.home_status_behind, IndianMoneyFormat.format(status.by)) to StatusTone.WARNING

        is GoalStatus.CatchingUp ->
            stringResource(R.string.home_status_catching_up, IndianMoneyFormat.format(status.by)) to StatusTone.WARNING
    }
    val destination = stringResource(
        if (summary.goal.destination ==
            Destination.PAYTM_GOLD
        ) {
            R.string.home_destination_gold
        } else {
            R.string.home_destination_fund
        },
    )
    val next = summary.nextDebit?.let {
        stringResource(R.string.home_next_sip, IndianMoneyFormat.format(it.amount), DATE.format(it.date), destination)
    }
    GoalSummaryRow(
        name = summary.goal.name,
        savedOfTarget = stringResource(
            R.string.home_saved_of_target,
            IndianMoneyFormat.format(summary.saved),
            IndianMoneyFormat.format(summary.goal.target),
            DATE.format(summary.goal.targetDate),
        ),
        progress = if (summary.goal.target.paise ==
            0L
        ) {
            0f
        } else {
            summary.saved.paise.toFloat() / summary.goal.target.paise
        },
        statusText = statusText,
        statusTone = tone,
        nextSip = next,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    )
}

private val DATE: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
