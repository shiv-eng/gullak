package com.shivangi.gullak.feature.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shivangi.gullak.core.designsystem.component.AmountChip
import com.shivangi.gullak.core.designsystem.component.CardDivider
import com.shivangi.gullak.core.designsystem.component.GullakImage
import com.shivangi.gullak.core.designsystem.component.OutlinePillButton
import com.shivangi.gullak.core.designsystem.component.PaytmCard
import com.shivangi.gullak.core.designsystem.component.PillButton
import com.shivangi.gullak.core.designsystem.component.SectionHeader
import com.shivangi.gullak.core.designsystem.theme.GullakTheme
import com.shivangi.gullak.core.model.ai.AiPath
import com.shivangi.gullak.core.model.settings.SpeechLanguage
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun SettingsEntry(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            val message = when (effect) {
                is SettingsEffect.FastForwarded -> resources.getString(
                    R.string.settings_moved_to,
                    DATE.format(effect.date),
                )

                SettingsEffect.DateReset -> resources.getString(R.string.settings_date_reset)

                is SettingsEffect.DemoReset -> resources.getString(
                    if (effect.seeded) R.string.settings_reset_seeded else R.string.settings_reset_empty,
                )

                SettingsEffect.Failed -> resources.getString(R.string.settings_failed)
            }
            snackbarHostState.showSnackbar(message)
        }
    }
    SettingsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        appLanguage = currentAppLanguage(),
        onAppLanguage = ::setAppLanguage,
        modifier = modifier,
    )
}

private fun currentAppLanguage(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags().ifEmpty {
    ENGLISH
}

private fun setAppLanguage(tag: String) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    appLanguage: String,
    onAppLanguage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = GullakTheme.colors.page,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
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
            item { DemoCard(state = state, onEvent = onEvent) }
            item { DemoDataCard(isWorking = state.isWorking, onEvent = onEvent) }
            item {
                LanguageCard(
                    appLanguage = appLanguage,
                    speechLanguage = state.settings.speechLanguage,
                    voiceReplies = state.settings.voiceReplies,
                    interruptBySpeaking = state.settings.interruptBySpeaking,
                    onInterruptBySpeaking = { onEvent(SettingsEvent.InterruptBySpeakingChanged(it)) },
                    onVoiceReplies = { onEvent(SettingsEvent.VoiceRepliesChanged(it)) },
                    onAppLanguage = onAppLanguage,
                    onSpeechLanguage = { onEvent(SettingsEvent.SpeechLanguageChanged(it)) },
                )
            }
            item { AiCard(state = state) }
            item { AboutFooter() }
        }
    }
}

@Composable
private fun DemoCard(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    PaytmCard(modifier = modifier) {
        SectionHeader(title = stringResource(R.string.settings_demo_title))
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = stringResource(R.string.settings_demo_mode),
            body = stringResource(R.string.settings_demo_mode_body),
            checked = state.settings.demoMode,
            onChange = { onEvent(SettingsEvent.DemoModeChanged(it)) },
        )
        CardDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_demo_date),
                style = MaterialTheme.typography.bodyMedium,
                color = GullakTheme.colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                state.today?.let(DATE::format).orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                color = GullakTheme.colors.textPrimary,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.settings_fast_forward),
            style = MaterialTheme.typography.bodyMedium,
            color = GullakTheme.colors.textBody,
        )
        Text(
            stringResource(R.string.settings_fast_forward_body),
            style = MaterialTheme.typography.bodySmall,
            color = GullakTheme.colors.textMuted,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.fastForwardMonths.forEach { month ->
                AmountChip(
                    label = MONTH.format(month),
                    selected = false,
                    onClick = { if (!state.isWorking) onEvent(SettingsEvent.FastForward(month)) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinePillButton(
            text = stringResource(R.string.settings_back_to_today),
            onClick = { if (!state.isWorking) onEvent(SettingsEvent.BackToToday) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.isWorking) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = GullakTheme.colors.action,
                trackColor = GullakTheme.colors.field,
            )
        }
    }
}

@Composable
private fun DemoDataCard(
    isWorking: Boolean,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    PaytmCard(modifier = modifier) {
        SectionHeader(title = stringResource(R.string.settings_data_title))
        Text(
            stringResource(R.string.settings_data_body),
            style = MaterialTheme.typography.bodySmall,
            color = GullakTheme.colors.textMuted,
        )
        Spacer(Modifier.height(12.dp))
        PillButton(
            text = stringResource(R.string.settings_reset_seed),
            onClick = { if (!isWorking) onEvent(SettingsEvent.ResetDemo(seed = true)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinePillButton(
            text = stringResource(R.string.settings_reset_empty_action),
            onClick = { if (!isWorking) onEvent(SettingsEvent.ResetDemo(seed = false)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LanguageCard(
    appLanguage: String,
    speechLanguage: SpeechLanguage,
    voiceReplies: Boolean,
    interruptBySpeaking: Boolean,
    onInterruptBySpeaking: (Boolean) -> Unit,
    onVoiceReplies: (Boolean) -> Unit,
    onAppLanguage: (String) -> Unit,
    onSpeechLanguage: (SpeechLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    PaytmCard(modifier = modifier) {
        SectionHeader(title = stringResource(R.string.settings_language_title))
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_app_language),
            style = MaterialTheme.typography.bodyMedium,
            color = GullakTheme.colors.textBody,
        )
        RadioRow(stringResource(R.string.settings_english), appLanguage.startsWith(ENGLISH)) { onAppLanguage(ENGLISH) }
        RadioRow(stringResource(R.string.settings_hindi), appLanguage.startsWith(HINDI)) { onAppLanguage(HINDI) }
        CardDivider()
        Text(
            stringResource(R.string.settings_speech_language),
            style = MaterialTheme.typography.bodyMedium,
            color = GullakTheme.colors.textBody,
        )
        Text(
            stringResource(R.string.settings_speech_language_body),
            style = MaterialTheme.typography.bodySmall,
            color = GullakTheme.colors.textMuted,
        )
        RadioRow(stringResource(R.string.settings_speech_english), speechLanguage == SpeechLanguage.ENGLISH_INDIA) {
            onSpeechLanguage(SpeechLanguage.ENGLISH_INDIA)
        }
        RadioRow(stringResource(R.string.settings_speech_hindi), speechLanguage == SpeechLanguage.HINDI_INDIA) {
            onSpeechLanguage(SpeechLanguage.HINDI_INDIA)
        }
        CardDivider()
        SwitchRow(
            title = stringResource(R.string.settings_voice_replies),
            body = stringResource(R.string.settings_voice_replies_body),
            checked = voiceReplies,
            onChange = onVoiceReplies,
        )
        CardDivider()
        SwitchRow(
            title = stringResource(R.string.settings_interrupt),
            body = stringResource(R.string.settings_interrupt_body),
            checked = interruptBySpeaking,
            onChange = onInterruptBySpeaking,
        )
    }
}

@Composable
private fun AiCard(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
) {
    PaytmCard(modifier = modifier) {
        SectionHeader(title = stringResource(R.string.settings_ai_title))
        Spacer(Modifier.height(8.dp))
        InfoRow(
            label = stringResource(R.string.settings_ai_last_path),
            value = stringResource(
                when (state.settings.lastAiPath) {
                    AiPath.DEMO -> R.string.settings_ai_path_demo
                    AiPath.GEMINI -> R.string.settings_ai_path_gemini
                    AiPath.RULES -> R.string.settings_ai_path_rules
                    null -> R.string.settings_ai_path_none
                },
            ),
        )
        InfoRow(
            label = stringResource(R.string.settings_ai_model),
            value = if (state.liveModelConfigured) {
                state.modelId
            } else {
                stringResource(
                    R.string.settings_ai_not_configured,
                )
            },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.settings_ai_body),
            style = MaterialTheme.typography.bodySmall,
            color = GullakTheme.colors.textMuted,
        )
    }
}

@Composable
private fun AboutFooter(modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        GullakImage(modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                stringResource(R.string.settings_about),
                style = MaterialTheme.typography.bodySmall,
                color = GullakTheme.colors.textBody,
            )
            Text(
                stringResource(R.string.settings_about_demo),
                style = MaterialTheme.typography.bodySmall,
                color = GullakTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    body: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = GullakTheme.colors.textPrimary)
            Text(body, style = MaterialTheme.typography.bodySmall, color = GullakTheme.colors.textMuted)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = GullakTheme.colors.action),
        )
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = GullakTheme.colors.action),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = GullakTheme.colors.textPrimary)
    }
}

@Composable
private fun InfoRow(
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
        Text(value, style = MaterialTheme.typography.titleSmall, color = GullakTheme.colors.textPrimary)
    }
}

private const val ENGLISH = "en"
private const val HINDI = "hi"
private val DATE: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private val MONTH: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())
