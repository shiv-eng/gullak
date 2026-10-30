package com.gullak.feature.goaldetail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gullak.core.designsystem.component.CardDivider
import com.gullak.core.designsystem.component.GullakImage
import com.gullak.core.designsystem.component.OutlinePillButton
import com.gullak.core.designsystem.component.PaytmCard
import com.gullak.core.designsystem.component.PillButton
import com.gullak.core.designsystem.component.SavingsProgressBar
import com.gullak.core.designsystem.component.SectionHeader
import com.gullak.core.designsystem.component.StatusChip
import com.gullak.core.designsystem.component.StatusTone
import com.gullak.core.designsystem.theme.GullakTheme
import com.gullak.core.domain.usecase.GoalDetail
import com.gullak.core.model.goal.Debit
import com.gullak.core.model.goal.DebitKind
import com.gullak.core.model.goal.DebitStatus
import com.gullak.core.model.goal.Destination
import com.gullak.core.model.goal.Frequency
import com.gullak.core.model.money.IndianMoneyFormat
import com.gullak.core.model.money.Money
import com.gullak.core.model.plan.GoalStatus
import com.gullak.core.model.plan.ReplanChoice
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun GoalDetailEntry(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GoalDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val latestState by rememberUpdatedState(state)
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            val message = when (effect) {
                is GoalDetailEffect.DebitPosted -> if (effect.debit.status == DebitStatus.SUCCEEDED) {
                    resources.getString(
                        R.string.detail_debit_success,
                        money(effect.debit.amount),
                        date(effect.debit.date),
                    )
                } else {
                    resources.getString(R.string.detail_debit_failed, date(effect.debit.date))
                }

                GoalDetailEffect.NothingDue -> resources.getString(R.string.detail_nothing_due)

                GoalDetailEffect.PlanUpdated -> resources.getString(R.string.detail_plan_updated)

                GoalDetailEffect.ActionFailed -> resources.getString(R.string.detail_action_failed)
            }
            if (latestState.detail != null) snackbarHostState.showSnackbar(message)
        }
    }
    GoalDetailScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/** A Goal SIP in the style of Paytm Gold's "Your gold in locker" page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalDetailScreen(
    state: GoalDetailUiState,
    onEvent: (GoalDetailEvent) -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val detail = state.detail
    Scaffold(
        modifier = modifier,
        containerColor = GullakTheme.colors.page,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = detail?.summary?.goal?.name ?: stringResource(R.string.detail_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.detail_back))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = GullakTheme.colors.page,
                    titleContentColor = GullakTheme.colors.textPrimary,
                    navigationIconContentColor = GullakTheme.colors.textPrimary,
                ),
            )
        },
    ) { padding ->
        if (detail == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.isLoading) CircularProgressIndicator(color = GullakTheme.colors.action)
            }
            return@Scaffold
        }
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
            state.unresolvedFailure?.let { failed ->
                item { FixPlanBanner(failed = failed, onFix = { onEvent(GoalDetailEvent.OpenReplan(failed.id)) }) }
            }
            item { SavedCard(detail = detail) }
            if (state.demoControls) {
                item { DemoControlsCard(isPosting = state.isPosting, onEvent = onEvent) }
            }
            item { HistoryCard(debits = detail.debits) }
        }
    }

    state.replan?.let { replan ->
        ReplanSheet(replan = replan, onEvent = onEvent)
    }
}

@Composable
private fun FixPlanBanner(
    failed: Debit,
    onFix: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(GullakTheme.colors.errorContainer, MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onFix)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = GullakTheme.colors.errorText)
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.detail_failed_banner, date(failed.date)),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = GullakTheme.colors.errorText,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.detail_fix_plan),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = GullakTheme.colors.link,
        )
    }
}

/** "Your gold in locker": saved amount, the gullak with a coin dropping in, status and next SIP. */
@Composable
private fun SavedCard(
    detail: GoalDetail,
    modifier: Modifier = Modifier,
) {
    val summary = detail.summary
    val goal = summary.goal
    val schedule = goal.schedule
    val progress = if (goal.target.paise == 0L) 0f else summary.saved.paise.toFloat() / goal.target.paise
    val savedText = money(summary.saved)
    val targetText = money(goal.target)
    val jarDescription = stringResource(R.string.detail_jar_description, savedText, targetText)
    PaytmCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.detail_saved_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GullakTheme.colors.textBody,
                )
                Text(
                    text = savedText,
                    style = GullakTheme.typography.amountLarge.copy(fontSize = 34.sp),
                    color = if (goal.destination ==
                        Destination.PAYTM_GOLD
                    ) {
                        GullakTheme.colors.gold
                    } else {
                        GullakTheme.colors.textPrimary
                    },
                )
                Text(
                    stringResource(R.string.detail_of_target, targetText, date(goal.targetDate)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GullakTheme.colors.textBody,
                )
            }
            CoinDropJar(
                savedPaise = summary.saved.paise,
                modifier = Modifier
                    .size(width = 104.dp, height = 120.dp)
                    .semantics { contentDescription = jarDescription },
            )
        }
        Spacer(Modifier.height(12.dp))
        SavingsProgressBar(progress = progress)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.detail_progress, (progress.coerceIn(0f, 1f) * PERCENT).toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = GullakTheme.colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            GoalStatusChip(summary.status)
        }
        CardDivider()
        DetailRow(
            label = stringResource(R.string.detail_goes_to),
            value = stringResource(
                if (goal.destination ==
                    Destination.PAYTM_GOLD
                ) {
                    R.string.detail_destination_gold
                } else {
                    R.string.detail_destination_fund
                },
            ),
        )
        if (schedule != null) {
            DetailRow(
                label = stringResource(R.string.detail_autopay),
                value = stringResource(
                    R.string.detail_autopay_value,
                    money(schedule.amountPerDebit),
                    frequency(schedule.frequency),
                ),
            )
        }
        summary.nextDebit?.let { next ->
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GullakTheme.colors.field, MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Event, null, tint = GullakTheme.colors.iconNavy, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.detail_next_sip, money(next.amount), date(next.date)),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = GullakTheme.colors.textPrimary,
                )
            }
        }
    }
}

/** The team's gullak; a gold coin drops into the slot whenever the saved amount goes up. */
@Composable
private fun CoinDropJar(
    savedPaise: Long,
    modifier: Modifier = Modifier,
) {
    var shownPaise by remember { mutableLongStateOf(savedPaise) }
    val drop = remember { Animatable(1f) }
    LaunchedEffect(savedPaise) {
        if (savedPaise > shownPaise) {
            drop.snapTo(0f)
            drop.animateTo(1f, tween(durationMillis = COIN_DROP_MS))
        }
        shownPaise = savedPaise
    }
    val coinColor = GullakTheme.colors.gold
    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        GullakImage(modifier = Modifier.size(width = 96.dp, height = 104.dp))
        if (drop.value < 1f) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (drop.value * COIN_TRAVEL_DP).dp)
                    .size(20.dp)
                    .alpha(1f - drop.value),
            ) {
                drawCircle(color = coinColor)
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = GullakTheme.colors.textMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = GullakTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun GoalStatusChip(
    status: GoalStatus,
    modifier: Modifier = Modifier,
) {
    when (status) {
        GoalStatus.OnTrack -> StatusChip(stringResource(R.string.detail_status_on_track), StatusTone.SUCCESS, modifier)

        GoalStatus.Reached -> StatusChip(stringResource(R.string.detail_status_reached), StatusTone.SUCCESS, modifier)

        is GoalStatus.Behind -> StatusChip(
            stringResource(R.string.detail_status_behind, money(status.by)),
            StatusTone.WARNING,
            modifier,
        )
    }
}

@Composable
private fun DemoControlsCard(
    isPosting: Boolean,
    onEvent: (GoalDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    PaytmCard(modifier = modifier) {
        SectionHeader(title = stringResource(R.string.detail_demo_title))
        Text(
            stringResource(R.string.detail_demo_body),
            style = MaterialTheme.typography.bodySmall,
            color = GullakTheme.colors.textMuted,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinePillButton(
                text = stringResource(R.string.detail_simulate_next),
                onClick = { if (!isPosting) onEvent(GoalDetailEvent.SimulateNextDebit) },
                modifier = Modifier.weight(1f),
            )
            OutlinePillButton(
                text = stringResource(R.string.detail_simulate_failed),
                onClick = { if (!isPosting) onEvent(GoalDetailEvent.SimulateFailedDebit) },
                modifier = Modifier.weight(1f),
            )
        }
        if (isPosting) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = GullakTheme.colors.action,
                trackColor = GullakTheme.colors.field,
            )
        }
    }
}

/** Paytm's "View Order Details": every attempted debit, newest first. */
@Composable
private fun HistoryCard(
    debits: List<Debit>,
    modifier: Modifier = Modifier,
) {
    PaytmCard(modifier = modifier) {
        SectionHeader(title = stringResource(R.string.detail_history_title))
        if (debits.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.detail_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = GullakTheme.colors.textMuted,
            )
        }
        debits.forEachIndexed { index, debit ->
            if (index > 0) CardDivider() else Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(kindLabel(debit.kind)),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = GullakTheme.colors.textPrimary,
                    )
                    Text(
                        date(debit.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = GullakTheme.colors.textMuted,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        money(debit.amount),
                        style = GullakTheme.typography.amountSmall,
                        color = GullakTheme.colors.textPrimary,
                    )
                    if (debit.status == DebitStatus.SUCCEEDED) {
                        Text(
                            stringResource(R.string.detail_success),
                            style = MaterialTheme.typography.labelMedium,
                            color = GullakTheme.colors.gain,
                        )
                    } else {
                        Text(
                            stringResource(R.string.detail_failed),
                            style = MaterialTheme.typography.labelMedium,
                            color = GullakTheme.colors.loss,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplanSheet(
    replan: ReplanUi,
    onEvent: (GoalDetailEvent) -> Unit,
) {
    val offer = replan.offer
    val behind = (offer.status as? GoalStatus.Behind)?.by ?: offer.failedDebit.amount
    ModalBottomSheet(
        onDismissRequest = { onEvent(GoalDetailEvent.DismissReplan) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GullakTheme.colors.card,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(
                stringResource(R.string.replan_title, date(offer.failedDebit.date)),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                color = GullakTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.replan_behind, money(behind)),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = GullakTheme.colors.warningText,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.replan_body),
                style = MaterialTheme.typography.bodySmall,
                color = GullakTheme.colors.textMuted,
            )
            Spacer(Modifier.height(12.dp))
            offer.choices.forEachIndexed { index, choice ->
                ChoiceCard(
                    choice = choice,
                    selected = index == replan.selected,
                    onSelect = { onEvent(GoalDetailEvent.ChoiceSelected(index)) },
                )
                Spacer(Modifier.height(8.dp))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GullakTheme.colors.chipSelected, MaterialTheme.shapes.small)
                    .padding(12.dp),
            ) {
                Text(
                    stringResource(R.string.replan_why),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = GullakTheme.colors.iconNavy,
                )
                Spacer(Modifier.height(4.dp))
                if (replan.isExplaining || replan.explanation == null) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = GullakTheme.colors.action,
                        trackColor = GullakTheme.colors.card,
                    )
                } else {
                    Text(
                        replan.explanation,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = GullakTheme.colors.textPrimary,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            PillButton(
                text = stringResource(R.string.replan_apply),
                onClick = { onEvent(GoalDetailEvent.ApplyReplan) },
                enabled = !replan.isApplying,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ChoiceCard(
    choice: ReplanChoice,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GullakTheme.colors
    val (title, body) = when (choice) {
        is ReplanChoice.CatchUp -> stringResource(R.string.replan_catch_up_title) to stringResource(
            R.string.replan_catch_up_body,
            money(choice.debits.first().amount),
            date(choice.debits.first().date),
            date(choice.debits.last().date),
        )

        is ReplanChoice.Spread -> stringResource(R.string.replan_spread_title) to stringResource(
            R.string.replan_spread_body,
            money(choice.newAmountPerDebit),
            choice.remainingDebits,
        )

        is ReplanChoice.Extend -> stringResource(R.string.replan_extend_title) to stringResource(
            R.string.replan_extend_body,
            date(choice.newTargetDate),
        )
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(
                BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.action else colors.chipBorder),
                MaterialTheme.shapes.small,
            )
            .background(if (selected) colors.chipSelected else colors.card, MaterialTheme.shapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colors.action),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
            )
            Text(body, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = colors.textBody)
        }
    }
}

private fun kindLabel(kind: DebitKind): Int = when (kind) {
    DebitKind.SCHEDULED -> R.string.detail_kind_sip
    DebitKind.CATCH_UP -> R.string.detail_kind_catch_up
    DebitKind.LUMP_SUM -> R.string.detail_kind_lump_sum
}

@Composable
private fun frequency(frequency: Frequency): String = stringResource(
    when (frequency) {
        Frequency.DAILY -> R.string.detail_daily
        Frequency.WEEKLY -> R.string.detail_weekly
        Frequency.MONTHLY -> R.string.detail_monthly
    },
)

private fun money(amount: Money): String = IndianMoneyFormat.format(amount)

private fun date(value: LocalDate): String = DATE.format(value)

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private const val PERCENT = 100
private const val COIN_DROP_MS = 700
private const val COIN_TRAVEL_DP = 40f
