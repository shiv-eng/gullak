package com.gullak.feature.creategoal.voice

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gullak.core.designsystem.component.GullakImage
import com.gullak.core.designsystem.component.OutlinePillButton
import com.gullak.core.designsystem.component.PaytmCard
import com.gullak.core.designsystem.component.PillButton
import com.gullak.core.designsystem.theme.GullakTheme
import com.gullak.core.model.goal.Destination
import com.gullak.core.model.goal.Frequency
import com.gullak.core.model.money.IndianMoneyFormat
import com.gullak.core.model.plan.PlanOption
import com.gullak.feature.creategoal.DemoBankAccount
import com.gullak.feature.creategoal.R
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun VoiceGoalEntry(
    onBack: () -> Unit,
    onTypeInstead: () -> Unit,
    onFinish: () -> Unit,
    onOpenGoals: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VoiceGoalViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val finish by rememberUpdatedState(onFinish)
    val openGoals by rememberUpdatedState(onOpenGoals)
    val openPhoneSpeech by rememberUpdatedState(rememberPhoneSpeech(viewModel::onEvent))

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                VoiceGoalEffect.Finish -> finish()
                VoiceGoalEffect.OpenGoals -> openGoals()
                VoiceGoalEffect.OpenPhoneSpeech -> openPhoneSpeech()
            }
        }
    }

    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onEvent(if (granted) VoiceGoalEvent.MicReady else VoiceGoalEvent.MicDenied)
    }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.onEvent(VoiceGoalEvent.MicReady) else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    VoiceGoalScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onTypeInstead = onTypeInstead,
        modifier = modifier,
    )
}

/** The phone's own speech screen, for phones that cannot recognise speech inside the app. */
// No speech screen on the phone is an expected case: the conversation carries on as if nothing was heard.
@Suppress("SwallowedException")
@Composable
private fun rememberPhoneSpeech(onEvent: (VoiceGoalEvent) -> Unit): () -> Unit {
    val prompt = stringResource(R.string.create_voice_prompt)
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) {
            onEvent(VoiceGoalEvent.PhoneSpeechHeard(text))
        } else {
            onEvent(VoiceGoalEvent.PhoneSpeechFailed)
        }
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        try {
            speech.launch(intent)
        } catch (e: ActivityNotFoundException) {
            onEvent(VoiceGoalEvent.PhoneSpeechFailed)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoiceGoalScreen(
    state: VoiceGoalUiState,
    onEvent: (VoiceGoalEvent) -> Unit,
    onBack: () -> Unit,
    onTypeInstead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = GullakTheme.colors.page,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.voice_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.create_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = GullakTheme.colors.page),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))
            TalkOrb(phase = state.phase, level = state.micLevel, onClick = { onEvent(VoiceGoalEvent.OrbTapped) })
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(statusText(state)),
                style = MaterialTheme.typography.labelLarge,
                color = GullakTheme.colors.textMuted,
            )
            Spacer(Modifier.height(16.dp))
            Captions(state)
            GoalFacts(state)
            if (state.step == TalkStep.CONFIRM_AUTOPAY || state.step == TalkStep.DONE) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.voice_demo),
                    style = MaterialTheme.typography.bodySmall,
                    color = GullakTheme.colors.textMuted,
                )
            }
            Spacer(Modifier.height(16.dp))
            if (state.step == TalkStep.DONE) {
                PillButton(
                    text = stringResource(R.string.success_done),
                    onClick = { onEvent(VoiceGoalEvent.DoneClicked) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinePillButton(
                    text = stringResource(R.string.success_view_goals),
                    onClick = { onEvent(VoiceGoalEvent.ViewGoalsClicked) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                TextButton(onClick = onTypeInstead) { Text(stringResource(R.string.voice_type_instead)) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun statusText(state: VoiceGoalUiState): Int = when {
    state.micDenied -> R.string.create_mic_denied
    state.phase == TalkPhase.SPEAKING -> R.string.voice_status_speaking
    state.phase == TalkPhase.LISTENING -> R.string.create_listening
    state.phase == TalkPhase.THINKING -> R.string.create_understanding
    else -> R.string.voice_status_waiting
}

/** Gullak's face in the conversation: breathes while speaking, follows the voice while listening. */
@Composable
private fun TalkOrb(
    phase: TalkPhase,
    level: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val breathing = rememberInfiniteTransition(label = "orb")
    val pulse by breathing.animateFloat(
        initialValue = 1f,
        targetValue = PULSE_SCALE,
        animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
        label = "pulse",
    )
    val voice by animateFloatAsState(1f + level * MAX_LEVEL_GROWTH, label = "voice")
    val ring = when (phase) {
        TalkPhase.SPEAKING, TalkPhase.THINKING -> pulse
        TalkPhase.LISTENING -> voice
        TalkPhase.WAITING -> 1f
    }
    val label = stringResource(R.string.voice_status_waiting)
    Box(
        modifier = modifier
            .size(ORB_SIZE.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(RING_SIZE.dp)
                .graphicsLayer {
                    scaleX = ring
                    scaleY = ring
                }
                .background(
                    if (phase == TalkPhase.WAITING) GullakTheme.colors.field else GullakTheme.colors.chipSelected,
                    CircleShape,
                ),
        )
        GullakImage(modifier = Modifier.size(IMAGE_SIZE.dp))
    }
}

@Composable
private fun Captions(
    state: VoiceGoalUiState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        state.gullakSaid?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                color = GullakTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
        }
        state.youSaid?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.create_you_said, it),
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = GullakTheme.colors.textBody,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** What has been understood so far, filling in as the user talks. */
@Composable
private fun GoalFacts(
    state: VoiceGoalUiState,
    modifier: Modifier = Modifier,
) {
    if (state.name == null && state.target == null && state.date == null) return
    PaytmCard(modifier = modifier.padding(top = 16.dp).fillMaxWidth()) {
        state.name?.let { Fact(stringResource(R.string.create_name_label), it) }
        state.target?.let { Fact(stringResource(R.string.create_amount_label), IndianMoneyFormat.format(it)) }
        state.date?.let { Fact(stringResource(R.string.create_date_label), DATE.format(it)) }
        if (state.oneTime.isPositive) {
            Fact(stringResource(R.string.mandate_lump), IndianMoneyFormat.format(state.oneTime))
        }
        state.plan?.options?.forEach { option ->
            Fact(stringResource(periodLabel(option.frequency)), amountLine(option), bold = option == state.chosen)
        }
        state.plan?.let { plan ->
            Fact(
                stringResource(R.string.voice_goes_to),
                stringResource(
                    when (plan.destination) {
                        Destination.PAYTM_GOLD -> R.string.destination_gold
                        Destination.PAYTM_MONEY_FUND -> R.string.destination_fund
                    },
                ),
            )
        }
        if (state.chosen != null) Fact(stringResource(R.string.bank_section), stringResource(bankName(state.bank)))
    }
}

private fun amountLine(option: PlanOption): String = IndianMoneyFormat.format(option.amountPerDebit)

private fun periodLabel(frequency: Frequency): Int = when (frequency) {
    Frequency.DAILY -> R.string.voice_period_daily
    Frequency.WEEKLY -> R.string.voice_period_weekly
    Frequency.MONTHLY -> R.string.voice_period_monthly
}

private fun bankName(bank: DemoBankAccount): Int = when (bank) {
    DemoBankAccount.SBI_SAVINGS -> R.string.bank_sbi_name
    DemoBankAccount.HDFC_SAVINGS -> R.string.bank_hdfc_name
    DemoBankAccount.ICICI_SAVINGS -> R.string.bank_icici_name
}

@Composable
private fun Fact(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = GullakTheme.colors.textMuted)
        Spacer(Modifier.size(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            ),
            color = if (bold) GullakTheme.colors.action else GullakTheme.colors.textPrimary,
            textAlign = TextAlign.End,
        )
    }
}

private const val ORB_SIZE = 200
private const val RING_SIZE = 150
private const val IMAGE_SIZE = 110
private const val PULSE_SCALE = 1.12f
private const val PULSE_MS = 700
private const val MAX_LEVEL_GROWTH = 0.3f
private val DATE: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
