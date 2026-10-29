package com.gullak.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gullak.core.designsystem.theme.GullakTheme

/** Placeholder Home (phase 1): the empty state only. Goal cards and totals arrive in phase 6. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    onNewGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    titleContentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewGoal,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_new_goal)) },
            )
        },
    ) { padding ->
        EmptyHome(
            onMicClick = onNewGoal,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        )
    }
}

@Composable
private fun EmptyHome(
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FilledIconButton(
            onClick = onMicClick,
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = stringResource(R.string.home_mic_description),
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.home_empty_prompt),
            style = MaterialTheme.typography.headlineSmall,
            color = GullakTheme.colors.emphasis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_empty_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = GullakTheme.colors.textBody,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    GullakTheme {
        HomeScreen(onNewGoal = {})
    }
}
