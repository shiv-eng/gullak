package com.shivangi.gullak.feature.paytmhome

import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shivangi.gullak.core.designsystem.component.CardDivider
import com.shivangi.gullak.core.designsystem.component.GoalSummaryRow
import com.shivangi.gullak.core.designsystem.component.GullakImage
import com.shivangi.gullak.core.designsystem.component.OneLineLabel
import com.shivangi.gullak.core.designsystem.component.PaytmBadge
import com.shivangi.gullak.core.designsystem.component.PaytmCard
import com.shivangi.gullak.core.designsystem.component.SectionHeader
import com.shivangi.gullak.core.designsystem.component.ServiceTile
import com.shivangi.gullak.core.designsystem.component.StatusTone
import com.shivangi.gullak.core.designsystem.theme.GullakTheme
import com.shivangi.gullak.core.model.money.IndianMoneyFormat
import com.shivangi.gullak.core.model.money.Money
import com.shivangi.gullak.core.model.plan.GoalStatus
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun PaytmHomeEntry(
    onOpenGullak: () -> Unit,
    onNewGoal: () -> Unit,
    onOpenGoal: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaytmHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val demoOnly = stringResource(R.string.paytm_demo_only)
    // The effect outlives recompositions; always call the latest navigation lambdas.
    val openGullak by rememberUpdatedState(onOpenGullak)
    val newGoal by rememberUpdatedState(onNewGoal)
    val openGoal by rememberUpdatedState(onOpenGoal)
    val openSettings by rememberUpdatedState(onOpenSettings)
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                PaytmHomeEffect.ShowDemoOnly -> snackbarHostState.showSnackbar(demoOnly)
                PaytmHomeEffect.OpenGullak -> openGullak()
                PaytmHomeEffect.OpenNewGoal -> newGoal()
                is PaytmHomeEffect.OpenGoal -> openGoal(effect.goalId)
                PaytmHomeEffect.OpenSettings -> openSettings()
            }
        }
    }
    PaytmHomeScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/** A stand-in for the Paytm app's home screen, with Gullak attached to the Gold & Silver card. */
@Composable
internal fun PaytmHomeScreen(
    state: PaytmHomeUiState,
    onEvent: (PaytmHomeEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val demo = { onEvent(PaytmHomeEvent.DemoServiceClicked) }
    val openGullak = { onEvent(PaytmHomeEvent.GullakClicked) }
    val newGoal = { onEvent(PaytmHomeEvent.NewGoalClicked) }
    Scaffold(
        modifier = modifier,
        containerColor = GullakTheme.colors.page,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { ScanQrButton(onClick = demo) },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                HomeTopBar(
                    onAvatar = { onEvent(PaytmHomeEvent.SettingsClicked) },
                    onPromo = openGullak,
                    onDemo = demo,
                )
            }
            item { GullakBanner(onClick = newGoal) }
            item { ServicesCard(R.string.paytm_section_upi, UPI_SERVICES, onClick = demo, filledCircles = true) }
            item { ServicesCard(R.string.paytm_section_recharge, RECHARGE_SERVICES, onClick = demo, viewAll = true) }
            item {
                GoldAndSilverCard(
                    state = state,
                    onGullak = openGullak,
                    onNewGoal = newGoal,
                    onDemoService = demo,
                )
            }
            if (state.goals.isNotEmpty()) {
                item { GullakGoalsCard(state = state, onEvent = onEvent) }
            }
            item { ServicesCard(R.string.paytm_section_financial, FINANCIAL_SERVICES, onClick = demo, viewAll = true) }
            item {
                Text(
                    text = stringResource(R.string.paytm_demo_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = GullakTheme.colors.textMuted,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    onAvatar: () -> Unit,
    onPromo: () -> Unit,
    onDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsLabel = stringResource(R.string.paytm_settings)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(GullakTheme.colors.chipSelected, CircleShape)
                .clickable(role = Role.Button, onClick = onAvatar)
                .semantics { contentDescription = settingsLabel },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.paytm_avatar_initials),
                style = MaterialTheme.typography.titleSmall,
                color = GullakTheme.colors.iconNavy,
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onPromo)
                .heightIn(min = 48.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.paytm_header_promo),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = GullakTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(GullakTheme.colors.action, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = GullakTheme.colors.onAction,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        IconButton(onClick = onDemo) {
            Icon(Icons.Outlined.Search, stringResource(R.string.paytm_search), tint = GullakTheme.colors.textPrimary)
        }
        IconButton(onClick = onDemo) {
            Icon(
                Icons.Outlined.Notifications,
                stringResource(R.string.paytm_notifications),
                tint = GullakTheme.colors.textPrimary,
            )
        }
    }
}

/** The promo banner slot, used for the Gullak launch. */
@Composable
private fun GullakBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = GullakTheme.colors.chipSelected,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.gullak_banner_eyebrow),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        ),
                        color = GullakTheme.colors.iconNavy,
                    )
                    Spacer(Modifier.width(8.dp))
                    PaytmBadge(stringResource(R.string.paytm_badge_new_ai))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.gullak_banner_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = GullakTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.gullak_banner_body),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = GullakTheme.colors.textBody,
                )
                Spacer(Modifier.height(10.dp))
                SmallPillButton(text = stringResource(R.string.gullak_banner_action), onClick = onClick)
            }
            Spacer(Modifier.width(8.dp))
            GullakImage(modifier = Modifier.size(84.dp))
        }
    }
}

/** Paytm's compact navy pill with an arrow ("Pay Now →"). */
@Composable
private fun SmallPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = GullakTheme.colors.action,
            contentColor = GullakTheme.colors.onAction,
        ),
    ) {
        OneLineLabel(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}

private class Service(
    @StringRes val label: Int,
    val icon: ImageVector,
    @StringRes val badge: Int? = null,
)

private val UPI_SERVICES = listOf(
    Service(R.string.paytm_scan_any_qr, Icons.Outlined.QrCodeScanner),
    Service(R.string.paytm_pay_anyone, Icons.AutoMirrored.Outlined.Send),
    Service(R.string.paytm_to_bank, Icons.Outlined.AccountBalance),
    Service(R.string.paytm_balance_history, Icons.AutoMirrored.Outlined.ReceiptLong),
)

private val RECHARGE_SERVICES = listOf(
    Service(R.string.paytm_mobile_recharge, Icons.Outlined.PhoneAndroid),
    Service(R.string.paytm_credit_card_bill, Icons.Outlined.CreditCard),
    Service(R.string.paytm_electricity_bill, Icons.Outlined.Lightbulb),
    Service(R.string.paytm_fastag, Icons.Outlined.Toll),
)

private val GOLD_SERVICES = listOf(
    Service(R.string.paytm_save_gold_daily, Icons.Outlined.EventRepeat),
    Service(R.string.paytm_save_silver_daily, Icons.Outlined.Diamond),
    Service(R.string.paytm_save_in_gold, Icons.Outlined.WorkspacePremium),
    Service(R.string.paytm_gold_round_up, Icons.Outlined.Autorenew),
)

private val FINANCIAL_SERVICES = listOf(
    Service(R.string.paytm_loan, Icons.Outlined.RequestQuote),
    Service(R.string.paytm_mutual_fund, Icons.AutoMirrored.Outlined.ShowChart, R.string.paytm_badge_sip),
    Service(R.string.paytm_car_insurance, Icons.Outlined.DirectionsCar),
    Service(R.string.paytm_bike_insurance, Icons.Outlined.TwoWheeler),
    Service(R.string.paytm_postpaid, Icons.Outlined.CalendarToday),
    Service(R.string.paytm_stocks, Icons.AutoMirrored.Outlined.TrendingUp),
    Service(R.string.paytm_get_credit_card, Icons.Outlined.CreditCard),
    Service(R.string.paytm_health_insurance, Icons.Outlined.FavoriteBorder),
)

@Composable
private fun ServiceGrid(
    services: List<Service>,
    onClick: () -> Unit,
    filledCircles: Boolean = false,
) {
    services.chunked(TILES_PER_ROW).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth()) {
            row.forEach { service ->
                ServiceTile(
                    icon = service.icon,
                    label = stringResource(service.label),
                    badge = service.badge?.let { stringResource(it) },
                    filledCircle = filledCircles,
                    onClick = onClick,
                    modifier = Modifier.weight(1f),
                )
            }
            repeat(TILES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ServicesCard(
    @StringRes title: Int,
    services: List<Service>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filledCircles: Boolean = false,
    viewAll: Boolean = false,
) {
    PaytmCard(modifier = modifier) {
        SectionHeader(
            title = stringResource(title),
            actionLabel = stringResource(R.string.paytm_view_all).takeIf { viewAll },
            onAction = onClick,
        )
        Spacer(Modifier.height(8.dp))
        ServiceGrid(services, onClick, filledCircles)
    }
}

/**
 * Paytm's Gold & Silver card with its four services, and Gullak attached at the bottom of the same card,
 * like the "Know more about Digital Gold?" strip on Paytm Gold.
 */
@Composable
private fun GoldAndSilverCard(
    state: PaytmHomeUiState,
    onGullak: () -> Unit,
    onNewGoal: () -> Unit,
    onDemoService: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PaytmCard(modifier = modifier) {
        SectionHeader(title = stringResource(R.string.paytm_section_gold_silver))
        Spacer(Modifier.height(8.dp))
        ServiceGrid(GOLD_SERVICES, onDemoService)
        Spacer(Modifier.height(8.dp))
        GullakStrip(
            state = state,
            onClick = if (state.goals.isEmpty()) onNewGoal else onGullak,
        )
    }
}

@Composable
private fun GullakStrip(
    state: PaytmHomeUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(GullakTheme.colors.chipSelected, MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GullakImage(modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.gullak_strip_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = GullakTheme.colors.textPrimary,
                )
                Spacer(Modifier.width(6.dp))
                PaytmBadge(stringResource(R.string.paytm_badge_new))
            }
            Text(
                text = if (state.goals.isEmpty()) {
                    stringResource(R.string.gullak_strip_body)
                } else {
                    pluralStringResource(
                        R.plurals.gullak_strip_summary,
                        state.goals.size,
                        state.goals.size,
                        IndianMoneyFormat.format(state.totalSaved),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = GullakTheme.colors.textBody,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = GullakTheme.colors.iconNavy,
        )
    }
}

/** The user's Gullaks, shown once they have any, below Gold & Silver. */
@Composable
private fun GullakGoalsCard(
    state: PaytmHomeUiState,
    onEvent: (PaytmHomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    PaytmCard(modifier = modifier) {
        SectionHeader(
            title = stringResource(R.string.gullak_card_title),
            actionLabel = stringResource(R.string.paytm_view_all),
            onAction = { onEvent(PaytmHomeEvent.GullakClicked) },
        )
        Text(
            text = stringResource(R.string.gullak_card_total, IndianMoneyFormat.format(state.totalSaved)),
            style = MaterialTheme.typography.bodyMedium,
            color = GullakTheme.colors.textMuted,
        )
        state.goals.forEach { goal ->
            CardDivider()
            GoalRow(goal = goal, onClick = { onEvent(PaytmHomeEvent.GoalClicked(goal.id)) })
        }
    }
}

@Composable
private fun GoalRow(
    goal: GoalCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (statusText, tone) = when (val status = goal.status) {
        GoalStatus.OnTrack -> stringResource(R.string.gullak_status_on_track) to StatusTone.SUCCESS

        GoalStatus.Reached -> stringResource(R.string.gullak_status_reached) to StatusTone.SUCCESS

        is GoalStatus.Behind ->
            stringResource(R.string.gullak_status_behind, IndianMoneyFormat.format(status.by)) to StatusTone.WARNING

        is GoalStatus.CatchingUp ->
            stringResource(R.string.gullak_status_catching_up, IndianMoneyFormat.format(status.by)) to
                StatusTone.WARNING
    }
    val nextSip = if (goal.nextDebitDate != null && goal.nextDebitAmount != null) {
        stringResource(
            R.string.gullak_next_sip,
            IndianMoneyFormat.format(goal.nextDebitAmount),
            NEXT_DATE.format(goal.nextDebitDate),
        )
    } else {
        null
    }
    GoalSummaryRow(
        name = goal.name,
        savedOfTarget = stringResource(
            R.string.gullak_saved_of_target,
            IndianMoneyFormat.format(goal.saved),
            IndianMoneyFormat.format(goal.target),
        ),
        progress = goal.progress,
        statusText = statusText,
        statusTone = tone,
        nextSip = nextSip,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun ScanQrButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        containerColor = GullakTheme.colors.iconNavy,
        contentColor = GullakTheme.colors.onAction,
        icon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null) },
        text = { Text(stringResource(R.string.paytm_scan_qr), style = MaterialTheme.typography.titleMedium) },
    )
}

private const val TILES_PER_ROW = 4
private val NEXT_DATE: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

@Preview(showBackground = true, heightDp = 1600)
@Composable
private fun PaytmHomePreview() {
    GullakTheme {
        PaytmHomeScreen(
            state = PaytmHomeUiState(
                isLoading = false,
                goals = listOf(
                    GoalCardUi(
                        "1",
                        "Laptop",
                        Money.fromRupees(18_000),
                        Money.fromRupees(36_000),
                        GoalStatus.Behind(Money.fromRupees(4_500)),
                        null,
                        null,
                    ),
                ),
                totalSaved = Money.fromRupees(18_000),
            ),
            onEvent = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
