package com.gullak.feature.creategoal

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gullak.core.designsystem.component.AmountChip
import com.gullak.core.designsystem.component.CardDivider
import com.gullak.core.designsystem.component.GullakImage
import com.gullak.core.designsystem.component.OneLineLabel
import com.gullak.core.designsystem.component.OutlinePillButton
import com.gullak.core.designsystem.component.PaytmCard
import com.gullak.core.designsystem.component.PillButton
import com.gullak.core.designsystem.component.SectionHeader
import com.gullak.core.designsystem.component.SuggestionBubble
import com.gullak.core.designsystem.theme.GullakTheme
import com.gullak.core.domain.ai.FollowUpQuestion
import com.gullak.core.domain.ai.FollowUpReason
import com.gullak.core.domain.plan.PlanEngine
import com.gullak.core.model.ai.GoalLanguage
import com.gullak.core.model.goal.Destination
import com.gullak.core.model.goal.Frequency
import com.gullak.core.model.money.IndianMoneyFormat
import com.gullak.core.model.money.Money
import com.gullak.core.model.plan.DestinationReason
import com.gullak.core.model.plan.PlanOption
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun CreateGoalEntry(
    startWithVoice: Boolean,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onOpenGoals: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateGoalViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val finish by rememberUpdatedState(onFinish)
    val openGoals by rememberUpdatedState(onOpenGoals)

    val openPhoneSpeech by rememberUpdatedState(rememberPhoneSpeech(state.speechLanguageTag, viewModel::onEvent))
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                CreateGoalEffect.Finish -> finish()
                CreateGoalEffect.OpenGoals -> openGoals()
                CreateGoalEffect.OpenPhoneSpeech -> openPhoneSpeech()
            }
        }
    }

    val context = LocalContext.current
    val listen = rememberMic(viewModel::onEvent)
    val openVoiceSettings = {
        openTextToSpeechSettings(context)
        viewModel.onEvent(CreateGoalEvent.VoiceHintDismissed)
    }
    var voiceStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startWithVoice) {
        if (startWithVoice && !voiceStarted) {
            voiceStarted = true
            listen()
        }
    }

    // The question text needs string resources, so the screen hands it to the ViewModel to read aloud.
    val followUp = state.followUp?.let { followUpText(it) }
    LaunchedEffect(followUp) {
        if (followUp != null) viewModel.onEvent(CreateGoalEvent.FollowUpShown(followUp))
    }
    val readback = state.readback?.let { readbackText(it, state.language) }
    LaunchedEffect(readback) {
        if (readback != null) viewModel.onEvent(CreateGoalEvent.ReadbackShown(readback))
    }

    CreateGoalScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onSpeak = listen,
        onOpenVoiceSettings = openVoiceSettings,
        modifier = modifier,
    )
}

/** The phone's own speech screen, for phones that cannot recognise speech inside the app. */
// No speech screen on the phone is an expected case: it is shown as a message, not lost.
@Suppress("SwallowedException")
@Composable
private fun rememberPhoneSpeech(
    languageTag: String,
    onEvent: (CreateGoalEvent) -> Unit,
): () -> Unit {
    val prompt = stringResource(R.string.create_voice_prompt)
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) {
            onEvent(CreateGoalEvent.GoalSpoken(text))
        }
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        try {
            speech.launch(intent)
        } catch (e: ActivityNotFoundException) {
            onEvent(CreateGoalEvent.VoiceUnavailable)
        }
    }
}

/** The mic button's action: asks for the microphone permission if needed, then starts or stops listening. */
@Composable
private fun rememberMic(onEvent: (CreateGoalEvent) -> Unit): () -> Unit {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onEvent(if (granted) CreateGoalEvent.ListenClicked else CreateGoalEvent.MicDenied)
    }
    return {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) onEvent(CreateGoalEvent.ListenClicked) else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
}

private fun openTextToSpeechSettings(context: Context) {
    try {
        context.startActivity(Intent(TTS_SETTINGS_ACTION))
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No text-to-speech settings screen", e)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateGoalScreen(
    state: CreateGoalUiState,
    onEvent: (CreateGoalEvent) -> Unit,
    onBack: () -> Unit,
    onSpeak: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = state.stage == Stage.BANK || state.stage == Stage.APPROVAL) {
        onEvent(CreateGoalEvent.StepBack)
    }
    when (state.stage) {
        Stage.BANK -> BankScreen(state = state, onEvent = onEvent, modifier = modifier)

        Stage.APPROVAL -> ApprovalScreen(state = state, onEvent = onEvent, modifier = modifier)

        Stage.PROCESSING -> ProcessingScreen(modifier)

        Stage.DONE -> SuccessScreen(state = state, onEvent = onEvent, modifier = modifier)

        Stage.EDITING -> Scaffold(
            modifier = modifier,
            containerColor = GullakTheme.colors.page,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.create_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.create_back))
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = GullakTheme.colors.page,
                        titleContentColor = GullakTheme.colors.textPrimary,
                        navigationIconContentColor = GullakTheme.colors.textPrimary,
                    ),
                )
            },
            bottomBar = { StartBar(state = state, onStart = { onEvent(CreateGoalEvent.StartClicked) }) },
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
                item {
                    VoiceCard(
                        state = state,
                        onSpeak = onSpeak,
                        onOpenVoiceSettings = onOpenVoiceSettings,
                        onDismissVoiceHint = { onEvent(CreateGoalEvent.VoiceHintDismissed) },
                    )
                }
                item { GoalDetailsCard(state = state, onEvent = onEvent) }
                state.plan?.let { plan ->
                    item { PlanCard(state = state, plan = plan, onEvent = onEvent) }
                }
            }
        }
    }

    if (state.showMandate) {
        state.chosen?.let { chosen ->
            MandateSheet(
                state = state,
                chosen = chosen,
                onConfirm = { onEvent(CreateGoalEvent.MandateConfirmed) },
                onDismiss = { onEvent(CreateGoalEvent.MandateDismissed) },
            )
        }
    }
}

@Composable
private fun VoiceCard(
    state: CreateGoalUiState,
    onSpeak: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onDismissVoiceHint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PaytmCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GullakImage(modifier = Modifier.size(52.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(if (state.isListening) R.string.create_listening else R.string.create_voice_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = GullakTheme.colors.textPrimary,
                )
                Text(
                    state.partialTranscript ?: stringResource(R.string.create_voice_example),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.partialTranscript != null) {
                        GullakTheme.colors.textBody
                    } else {
                        GullakTheme.colors.textMuted
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            MicButton(isListening = state.isListening, level = state.micLevel, onClick = onSpeak)
        }
        state.transcript?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.create_you_said, it),
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = GullakTheme.colors.textBody,
            )
        }
        if (state.isUnderstanding) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = GullakTheme.colors.action,
                trackColor = GullakTheme.colors.field,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.create_understanding),
                style = MaterialTheme.typography.bodySmall,
                color = GullakTheme.colors.textMuted,
            )
        }
        state.readback?.let {
            Spacer(Modifier.height(10.dp))
            InfoBox(text = readbackText(it, state.language))
        }
        val note = voiceNote(state)
        if (note != null) {
            Spacer(Modifier.height(10.dp))
            InfoBox(text = note)
        }
        if (state.showVoiceHint) {
            Spacer(Modifier.height(10.dp))
            InfoBox(text = stringResource(R.string.create_voice_hint))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismissVoiceHint) { Text(stringResource(R.string.create_voice_hint_dismiss)) }
                TextButton(onClick = onOpenVoiceSettings) { Text(stringResource(R.string.create_voice_hint_action)) }
            }
        }
    }
}

/** A problem with speech, the open question, or the festival date that was looked up. */
@Composable
private fun voiceNote(state: CreateGoalUiState): String? = when {
    state.voiceProblem == VoiceProblem.FAILED -> stringResource(R.string.create_voice_failed)

    state.voiceProblem == VoiceProblem.NOT_HEARD -> stringResource(R.string.create_voice_not_heard)

    state.voiceProblem == VoiceProblem.MIC_DENIED -> stringResource(R.string.create_mic_denied)

    state.followUp != null -> followUpText(state.followUp)

    state.festival != null -> stringResource(
        R.string.create_festival_note,
        state.festival.name,
        DATE.format(state.festival.date),
    )

    else -> null
}

/** The mic, with a ring that grows with the speaker's voice while listening. */
@Composable
private fun MicButton(
    isListening: Boolean,
    level: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring by animateFloatAsState(
        targetValue = if (isListening) 1f + level * MAX_RING_GROWTH else 1f,
        label = "micRing",
    )
    Box(modifier = modifier.size(64.dp), contentAlignment = Alignment.Center) {
        if (isListening) {
            Box(
                Modifier
                    .size(52.dp)
                    .graphicsLayer {
                        scaleX = ring
                        scaleY = ring
                    }
                    .background(GullakTheme.colors.field, CircleShape),
            )
        }
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = GullakTheme.colors.action,
                contentColor = GullakTheme.colors.onAction,
            ),
        ) {
            Icon(
                if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = stringResource(
                    if (isListening) R.string.create_stop_listening else R.string.create_voice_title,
                ),
            )
        }
    }
}

private const val MAX_RING_GROWTH = 0.22f
private const val TTS_SETTINGS_ACTION = "com.android.settings.TTS_SETTINGS"
private const val TAG = "CreateGoal"

@Composable
private fun readbackText(
    readback: GoalReadback,
    language: GoalLanguage,
): String = stringResource(
    when (language) {
        GoalLanguage.ENGLISH -> R.string.create_readback_en
        GoalLanguage.HINGLISH -> R.string.create_readback_hinglish
        GoalLanguage.HINDI -> R.string.create_readback_hi
    },
    readback.name,
    IndianMoneyFormat.format(readback.target),
    DATE.format(readback.date),
)

@Composable
private fun followUpText(question: FollowUpQuestion): String = when (question) {
    is FollowUpQuestion.FromModel -> question.text

    is FollowUpQuestion.Standard -> stringResource(
        when (question.reason) {
            FollowUpReason.WHAT_TO_CHANGE -> R.string.create_ask_change
            FollowUpReason.MISSING_NAME -> R.string.create_ask_name
            FollowUpReason.MISSING_AMOUNT -> R.string.create_ask_amount
            FollowUpReason.MISSING_DATE -> R.string.create_ask_date
            FollowUpReason.AMOUNT_OUT_OF_RANGE -> R.string.create_problem_amount_range
            FollowUpReason.DATE_NOT_IN_FUTURE -> R.string.create_problem_date
            FollowUpReason.DATE_TOO_FAR -> R.string.create_problem_date_far
        },
    )
}

@Composable
private fun InfoBox(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(GullakTheme.colors.chipSelected, MaterialTheme.shapes.small)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Info, null, tint = GullakTheme.colors.iconNavy, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = GullakTheme.colors.textPrimary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDetailsCard(
    state: CreateGoalUiState,
    onEvent: (CreateGoalEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    PaytmCard(modifier = modifier) {
        SectionHeader(title = stringResource(R.string.create_details_title))
        Spacer(Modifier.height(12.dp))
        FieldLabel(stringResource(R.string.create_name_label))
        OutlinedTextField(
            value = state.name,
            onValueChange = { onEvent(CreateGoalEvent.NameChanged(it)) },
            placeholder = {
                Text(stringResource(R.string.create_name_hint), style = MaterialTheme.typography.bodyMedium)
            },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            colors = fieldColors(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
            modifier = Modifier.fillMaxWidth(),
        )
        val suggestions = goalSuggestions(state.name, stringArrayResource(R.array.create_goal_suggestions).asList())
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            // Two rows that scroll sideways: many options in little height.
            Column(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(0, 1).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.filterIndexed { index, _ -> index % 2 == row }.forEach { suggestion ->
                            SuggestionBubble(
                                label = suggestion,
                                onClick = { onEvent(CreateGoalEvent.NameChanged(suggestion)) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        FieldLabel(stringResource(R.string.create_amount_label))
        OutlinedTextField(
            value = state.amountText,
            onValueChange = { onEvent(CreateGoalEvent.AmountChanged(it)) },
            leadingIcon = {
                Text(
                    stringResource(R.string.create_rupee_symbol),
                    style = MaterialTheme.typography.titleLarge,
                    color = GullakTheme.colors.textPrimary,
                )
            },
            placeholder = {
                Text(stringResource(R.string.create_amount_hint), style = MaterialTheme.typography.bodyMedium)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = CircleShape,
            colors = fieldColors(),
            textStyle = GullakTheme.typography.amountMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AMOUNT_CHIPS.forEach { rupees ->
                AmountChip(
                    label = IndianMoneyFormat.format(Money.fromRupees(rupees)),
                    selected = state.amountText == rupees.toString(),
                    onClick = { onEvent(CreateGoalEvent.AmountChanged(rupees.toString())) },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        FieldLabel(stringResource(R.string.create_lump_label))
        OutlinedTextField(
            value = state.lumpSumText,
            onValueChange = { onEvent(CreateGoalEvent.LumpSumChanged(it)) },
            leadingIcon = {
                Text(
                    stringResource(R.string.create_rupee_symbol),
                    style = MaterialTheme.typography.titleLarge,
                    color = GullakTheme.colors.textPrimary,
                )
            },
            placeholder = {
                Text(stringResource(R.string.create_lump_hint), style = MaterialTheme.typography.bodyMedium)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = CircleShape,
            colors = fieldColors(),
            textStyle = GullakTheme.typography.amountSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        FieldLabel(stringResource(R.string.create_date_label))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .background(GullakTheme.colors.field, MaterialTheme.shapes.small)
                .clickable(role = Role.Button) { pickingDate = true }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.targetDate?.let(DATE::format) ?: stringResource(R.string.create_date_hint),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                color = if (state.targetDate == null) GullakTheme.colors.textMuted else GullakTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Outlined.CalendarMonth, null, tint = GullakTheme.colors.iconNavy)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.create_date_today, DATE.format(state.today)),
            style = MaterialTheme.typography.bodySmall,
            color = GullakTheme.colors.textMuted,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.create_fixed_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GullakTheme.colors.textPrimary,
                )
                Text(
                    stringResource(R.string.create_fixed_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = GullakTheme.colors.textMuted,
                )
            }
            Switch(
                checked = state.dateIsFixed,
                onCheckedChange = { onEvent(CreateGoalEvent.DateFixedChanged(it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = GullakTheme.colors.action),
            )
        }
        state.problem?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (it == InputProblem.TOO_CLOSE_FOR_SIP) {
                    stringResource(R.string.create_problem_too_close, DATE.format(state.today))
                } else {
                    stringResource(problemText(it))
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = GullakTheme.colors.errorText,
            )
        }
        if (state.createFailed) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.create_failed),
                style = MaterialTheme.typography.bodySmall,
                color = GullakTheme.colors.errorText,
            )
        }
    }

    if (pickingDate) {
        val today = state.today
        // Closer dates are too near for a recurring plan, so the calendar does not offer them.
        val firstDate = today.plusDays(PlanEngine.MIN_DAYS_FOR_RECURRING)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.targetDate?.toUtcMillis(),
            // Open where a date can be picked: the demo date can be months away from the phone's real date.
            initialDisplayedMonthMillis = (state.targetDate ?: firstDate).toUtcMillis(),
            yearRange = today.year..today.year + MAX_YEARS,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis.toLocalDate() >= firstDate

                override fun isSelectableYear(year: Int): Boolean = year in today.year..today.year + MAX_YEARS
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedDateMillis != null,
                    onClick = {
                        pickerState.selectedDateMillis?.let { onEvent(CreateGoalEvent.DateSelected(it.toLocalDate())) }
                        pickingDate = false
                    },
                ) { Text(stringResource(R.string.create_date_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.create_date_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
        color = GullakTheme.colors.textBody,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = GullakTheme.colors.field,
    unfocusedContainerColor = GullakTheme.colors.field,
    focusedBorderColor = GullakTheme.colors.chipSelectedBorder,
    unfocusedBorderColor = GullakTheme.colors.field,
    cursorColor = GullakTheme.colors.action,
)

private fun problemText(problem: InputProblem): Int = when (problem) {
    InputProblem.AMOUNT_TOO_LOW -> R.string.create_problem_amount_low
    InputProblem.AMOUNT_TOO_HIGH -> R.string.create_problem_amount_range
    InputProblem.DATE_NOT_IN_FUTURE -> R.string.create_problem_date
    InputProblem.TOO_CLOSE_FOR_SIP -> R.string.create_problem_too_close
    InputProblem.LUMP_SUM_TOO_HIGH -> R.string.create_problem_lump
}

/** Paytm Gold's SIP card: Daily / Weekly / Monthly radios, the amount, and where the money goes. */
@Composable
private fun PlanCard(
    state: CreateGoalUiState,
    plan: com.gullak.core.model.plan.Plan,
    onEvent: (CreateGoalEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chosen = state.chosen ?: return
    PaytmCard(modifier = modifier) {
        SectionHeader(title = stringResource(R.string.create_plan_title))
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            plan.options.forEach { option ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = option.frequency == state.frequency,
                            role = Role.RadioButton,
                            onClick = { onEvent(CreateGoalEvent.FrequencySelected(option.frequency)) },
                        )
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = option.frequency == state.frequency,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = GullakTheme.colors.action),
                    )
                    Spacer(Modifier.width(6.dp))
                    OneLineLabel(
                        stringResource(frequencyLabel(option.frequency)),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = if (option.frequency ==
                            state.frequency
                        ) {
                            GullakTheme.colors.action
                        } else {
                            GullakTheme.colors.textPrimary
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(GullakTheme.colors.field, MaterialTheme.shapes.medium)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = IndianMoneyFormat.format(chosen.amountPerDebit),
                        style = GullakTheme.typography.amountLarge.copy(fontSize = 22.sp),
                        color = GullakTheme.colors.textPrimary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(perLabel(chosen.frequency)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = GullakTheme.colors.textMuted,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Text(
                    text = stringResource(
                        R.string.create_plan_schedule,
                        chosen.numberOfDebits,
                        DATE.format(chosen.firstDebit),
                        DATE.format(chosen.lastDebit),
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = GullakTheme.colors.textBody,
                )
                if (chosen.total > plan.remaining) {
                    Text(
                        text = stringResource(R.string.create_plan_total, IndianMoneyFormat.format(chosen.total)),
                        style = MaterialTheme.typography.bodySmall,
                        color = GullakTheme.colors.textMuted,
                    )
                }
            }
        }
        CardDivider()
        Text(
            text = stringResource(R.string.create_goes_to, stringResource(destinationLabel(plan.destination))),
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = GullakTheme.colors.textPrimary,
        )
        Text(
            text = stringResource(
                when (plan.destinationReason) {
                    DestinationReason.SHORT_GOAL -> R.string.create_reason_short
                    DestinationReason.LONG_GOAL -> R.string.create_reason_long
                },
            ),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = GullakTheme.colors.textMuted,
        )
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GullakTheme.colors.chipSelected, MaterialTheme.shapes.small)
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.create_why_title),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = GullakTheme.colors.iconNavy,
                    modifier = Modifier.weight(1f),
                )
                state.explanation?.let { explanation ->
                    IconButton(onClick = { onEvent(CreateGoalEvent.Speak(explanation)) }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.VolumeUp,
                            contentDescription = stringResource(R.string.create_listen),
                            tint = GullakTheme.colors.iconNavy,
                        )
                    }
                }
            }
            if (state.isExplaining || state.explanation == null) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = GullakTheme.colors.action,
                    trackColor = GullakTheme.colors.card,
                )
            } else {
                Text(
                    state.explanation,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = GullakTheme.colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun StartBar(
    state: CreateGoalUiState,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chosen = state.chosen
    Surface(modifier = modifier.fillMaxWidth(), color = GullakTheme.colors.card, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PillButton(
                text = if (chosen != null && state.canStart) {
                    stringResource(R.string.create_start_with, IndianMoneyFormat.format(chosen.amountPerDebit))
                } else {
                    stringResource(R.string.create_start)
                },
                onClick = onStart,
                enabled = state.canStart,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.create_demo_note),
                style = MaterialTheme.typography.bodySmall,
                color = GullakTheme.colors.textMuted,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MandateSheet(
    state: CreateGoalUiState,
    chosen: PlanOption,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val plan = state.plan ?: return
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = GullakTheme.colors.card) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.mandate_title),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                color = GullakTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.mandate_subtitle, state.name.trim()),
                style = MaterialTheme.typography.bodySmall,
                color = GullakTheme.colors.textMuted,
            )
            Spacer(Modifier.height(12.dp))
            MandateRow(stringResource(R.string.mandate_amount), IndianMoneyFormat.format(chosen.amountPerDebit))
            if (state.lumpSum.isPositive) {
                MandateRow(stringResource(R.string.mandate_lump), IndianMoneyFormat.format(state.lumpSum))
            }
            MandateRow(stringResource(R.string.mandate_frequency), stringResource(frequencyLabel(chosen.frequency)))
            MandateRow(stringResource(R.string.mandate_first), DATE.format(chosen.firstDebit))
            MandateRow(stringResource(R.string.mandate_last), DATE.format(chosen.lastDebit))
            MandateRow(stringResource(R.string.mandate_destination), stringResource(destinationLabel(plan.destination)))
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.mandate_demo),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = GullakTheme.colors.warningText,
                modifier = Modifier
                    .background(GullakTheme.colors.warningContainer, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            PillButton(
                text = stringResource(R.string.mandate_confirm),
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MandateRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
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

/** Paytm's "Processing your request" step while the mandate and destination are set up. */
@Composable
private fun ProcessingScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GullakTheme.colors.page)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.processing_title),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
            color = GullakTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(28.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(96.dp),
            color = GullakTheme.colors.action,
            trackColor = GullakTheme.colors.field,
            strokeWidth = 8.dp,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.processing_step_plan),
            style = MaterialTheme.typography.bodyMedium,
            color = GullakTheme.colors.textBody,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.processing_step_autopay),
            style = MaterialTheme.typography.bodyMedium,
            color = GullakTheme.colors.textBody,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.mandate_demo),
            style = MaterialTheme.typography.bodySmall,
            color = GullakTheme.colors.textMuted,
        )
    }
}

@Composable
private fun SuccessScreen(
    state: CreateGoalUiState,
    onEvent: (CreateGoalEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val goal = state.createdGoal
    val schedule = goal?.schedule
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GullakTheme.colors.page)
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            GullakImage(modifier = Modifier.size(140.dp))
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = GullakTheme.colors.gain,
                modifier = Modifier
                    .size(40.dp)
                    .background(GullakTheme.colors.card, CircleShape),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.success_title),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            color = GullakTheme.colors.textPrimary,
        )
        if (goal != null && schedule != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.success_body,
                    goal.name,
                    IndianMoneyFormat.format(schedule.amountPerDebit),
                    stringResource(frequencyLabel(schedule.frequency)).lowercase(Locale.ROOT),
                    DATE.format(schedule.startDate),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = GullakTheme.colors.textBody,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.success_destination, stringResource(destinationLabel(goal.destination))),
                style = MaterialTheme.typography.bodySmall,
                color = GullakTheme.colors.textMuted,
            )
        }
        Spacer(Modifier.height(28.dp))
        PillButton(
            text = stringResource(R.string.success_done),
            onClick = { onEvent(CreateGoalEvent.DoneClicked) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinePillButton(
            text = stringResource(R.string.success_view_goals),
            onClick = { onEvent(CreateGoalEvent.ViewGoalsClicked) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun frequencyLabel(frequency: Frequency): Int = when (frequency) {
    Frequency.DAILY -> R.string.frequency_daily
    Frequency.WEEKLY -> R.string.frequency_weekly
    Frequency.MONTHLY -> R.string.frequency_monthly
}

private fun perLabel(frequency: Frequency): Int = when (frequency) {
    Frequency.DAILY -> R.string.per_day
    Frequency.WEEKLY -> R.string.per_week
    Frequency.MONTHLY -> R.string.per_month
}

private fun destinationLabel(destination: Destination): Int = when (destination) {
    Destination.PAYTM_GOLD -> R.string.destination_gold
    Destination.PAYTM_MONEY_FUND -> R.string.destination_fund
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private val AMOUNT_CHIPS = listOf(10_000L, 25_000L, 50_000L, 1_00_000L)
private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

private const val MAX_YEARS = 10

/** The AutoPay bank step: made-up demo accounts only, in the style of Paytm's account picker. */
@Composable
private fun BankScreen(
    state: CreateGoalUiState,
    onEvent: (CreateGoalEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chosen = state.chosen ?: return
    Scaffold(
        modifier = modifier,
        containerColor = GullakTheme.colors.page,
        topBar = { StepTopBar(stringResource(R.string.bank_title), onBack = { onEvent(CreateGoalEvent.StepBack) }) },
        bottomBar = {
            Surface(color = GullakTheme.colors.card, shadowElevation = 8.dp) {
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    PillButton(
                        text = stringResource(R.string.bank_proceed),
                        onClick = { onEvent(CreateGoalEvent.BankProceed) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PaytmCard {
                Text(
                    stringResource(R.string.bank_paying),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GullakTheme.colors.textMuted,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        IndianMoneyFormat.format(chosen.amountPerDebit),
                        style = GullakTheme.typography.amountLarge,
                        color = GullakTheme.colors.textPrimary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(perLabel(chosen.frequency)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = GullakTheme.colors.textMuted,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Text(
                    stringResource(R.string.bank_payee, state.name.trim()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GullakTheme.colors.textBody,
                )
            }
            PaytmCard {
                SectionHeader(title = stringResource(R.string.bank_section))
                Spacer(Modifier.height(8.dp))
                DemoBankAccount.entries.forEachIndexed { index, account ->
                    if (index > 0) CardDivider()
                    BankRow(
                        account = account,
                        selected = account == state.bankAccount,
                        onSelect = { onEvent(CreateGoalEvent.BankSelected(account)) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.mandate_demo),
                style = MaterialTheme.typography.labelMedium,
                color = GullakTheme.colors.warningText,
                modifier = Modifier
                    .background(GullakTheme.colors.warningContainer, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun BankRow(
    account: DemoBankAccount,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val labels = bankLabels(account)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(GullakTheme.colors.chipSelected, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.AccountBalance,
                null,
                tint = GullakTheme.colors.iconNavy,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(labels.name, style = MaterialTheme.typography.titleSmall, color = GullakTheme.colors.textPrimary)
            Text(labels.number, style = MaterialTheme.typography.bodySmall, color = GullakTheme.colors.textMuted)
            Text(labels.upi, style = MaterialTheme.typography.bodySmall, color = GullakTheme.colors.textMuted)
        }
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = GullakTheme.colors.action),
        )
    }
}

private class BankLabels(
    val name: String,
    val number: String,
    val upi: String,
)

@Composable
private fun bankLabels(account: DemoBankAccount): BankLabels = when (account) {
    DemoBankAccount.SBI_SAVINGS -> BankLabels(
        stringResource(R.string.bank_sbi_name),
        stringResource(R.string.bank_sbi_number),
        stringResource(R.string.bank_sbi_upi),
    )

    DemoBankAccount.HDFC_SAVINGS -> BankLabels(
        stringResource(R.string.bank_hdfc_name),
        stringResource(R.string.bank_hdfc_number),
        stringResource(R.string.bank_hdfc_upi),
    )

    DemoBankAccount.ICICI_SAVINGS -> BankLabels(
        stringResource(R.string.bank_icici_name),
        stringResource(R.string.bank_icici_number),
        stringResource(R.string.bank_icici_upi),
    )
}

/**
 * The approval step. Deliberately not a copy of the UPI PIN screen: it says it is a demo, takes any digits,
 * and only counts them; nothing is stored or sent.
 */
@Composable
private fun ApprovalScreen(
    state: CreateGoalUiState,
    onEvent: (CreateGoalEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chosen = state.chosen ?: return
    val bank = bankLabels(state.bankAccount)
    Scaffold(
        modifier = modifier,
        containerColor = GullakTheme.colors.card,
        topBar = {
            StepTopBar(stringResource(R.string.approval_title), onBack = { onEvent(CreateGoalEvent.StepBack) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.approval_payee),
                style = MaterialTheme.typography.bodyMedium,
                color = GullakTheme.colors.textMuted,
            )
            Text(
                IndianMoneyFormat.format(chosen.amountPerDebit),
                style = GullakTheme.typography.amountLarge,
                color = GullakTheme.colors.textPrimary,
            )
            Text(
                stringResource(
                    R.string.approval_details,
                    stringResource(frequencyLabel(chosen.frequency)),
                    bank.name,
                    bank.number,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = GullakTheme.colors.textBody,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.approval_enter),
                style = MaterialTheme.typography.titleSmall,
                color = GullakTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(CreateGoalUiState.APPROVAL_LENGTH) { index ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                if (index <
                                    state.approvalDigits
                                ) {
                                    GullakTheme.colors.iconNavy
                                } else {
                                    GullakTheme.colors.field
                                },
                                CircleShape,
                            ),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.approval_demo_note),
                style = MaterialTheme.typography.bodySmall,
                color = GullakTheme.colors.warningText,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .background(GullakTheme.colors.warningContainer, MaterialTheme.shapes.small)
                    .padding(10.dp),
            )
            Spacer(Modifier.weight(1f))
            ApprovalKeypad(
                onDigit = { onEvent(CreateGoalEvent.ApprovalDigit) },
                onDelete = { onEvent(CreateGoalEvent.ApprovalDelete) },
            )
        }
    }
}

@Composable
private fun ApprovalKeypad(
    onDigit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deleteLabel = stringResource(R.string.approval_delete)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(GullakTheme.colors.page)
            .navigationBarsPadding()
            .padding(8.dp),
    ) {
        KEYPAD_ROWS.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    val keyModifier = when (key) {
                        BLANK_KEY -> Modifier

                        else ->
                            Modifier
                                .background(GullakTheme.colors.card, MaterialTheme.shapes.small)
                                .clickable(role = Role.Button) { if (key == DELETE_KEY) onDelete() else onDigit() }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .padding(4.dp)
                            .then(keyModifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (key == DELETE_KEY) deleteLabel else key,
                            style = if (key == DELETE_KEY) {
                                MaterialTheme.typography.labelLarge
                            } else {
                                GullakTheme.typography.amountMedium
                            },
                            color = GullakTheme.colors.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepTopBar(
    title: String,
    onBack: () -> Unit,
) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.create_back))
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = GullakTheme.colors.page,
            titleContentColor = GullakTheme.colors.textPrimary,
            navigationIconContentColor = GullakTheme.colors.textPrimary,
        ),
    )
}

private const val DELETE_KEY = "delete"
private const val BLANK_KEY = ""
private val KEYPAD_ROWS = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf(BLANK_KEY, "0", DELETE_KEY),
)
