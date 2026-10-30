package com.gullak.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gullak.core.designsystem.component.PaytmCard
import com.gullak.core.designsystem.component.PillButton
import com.gullak.core.designsystem.theme.GullakTheme

/**
 * Gullak's home as a Paytm sub-page (back arrow, centred title, grey page, white cards), like Paytm Gold.
 * Phase 1 placeholder: the empty state only. Goal cards and totals arrive in phase 6.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    onBack: () -> Unit,
    onNewGoal: () -> Unit,
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
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = GullakTheme.colors.page,
                    titleContentColor = GullakTheme.colors.textPrimary,
                    navigationIconContentColor = GullakTheme.colors.textPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
        ) {
            PaytmCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FilledIconButton(
                        onClick = onNewGoal,
                        modifier = Modifier.size(88.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = GullakTheme.colors.action,
                            contentColor = GullakTheme.colors.onAction,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = stringResource(R.string.home_mic_description),
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.home_empty_prompt),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = GullakTheme.colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.home_empty_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = GullakTheme.colors.textBody,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    PillButton(
                        text = stringResource(R.string.home_new_goal),
                        leadingIcon = Icons.Filled.Add,
                        onClick = onNewGoal,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    GullakTheme {
        HomeScreen(onBack = {}, onNewGoal = {})
    }
}
