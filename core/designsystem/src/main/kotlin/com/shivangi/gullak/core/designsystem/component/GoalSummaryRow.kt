package com.shivangi.gullak.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivangi.gullak.core.designsystem.theme.GullakTheme

/** One Gullak in a list: gullak, name and status, saved of target, progress and the next SIP. */
@Composable
fun GoalSummaryRow(
    name: String,
    savedOfTarget: String,
    progress: Float,
    statusText: String,
    statusTone: StatusTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    nextSip: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.Top,
    ) {
        GullakImage(modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                    color = GullakTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(statusText, statusTone)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = savedOfTarget,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = GullakTheme.colors.textMuted,
            )
            Spacer(Modifier.height(6.dp))
            SavingsProgressBar(progress = progress)
            if (nextSip != null) {
                Spacer(Modifier.height(6.dp))
                Text(text = nextSip, style = MaterialTheme.typography.bodySmall, color = GullakTheme.colors.textBody)
            }
        }
    }
}
