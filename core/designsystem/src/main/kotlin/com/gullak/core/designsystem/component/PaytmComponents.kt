package com.gullak.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullak.core.designsystem.theme.GullakTheme

/** The Paytm app's large white rounded card. */
@Composable
fun PaytmCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = GullakTheme.colors.card,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp), content = content)
    }
}

/** A card title ("Gold & Silver") with an optional "View All →" link. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
            color = GullakTheme.colors.textPrimary,
        )
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            PaytmBadge(badge)
        }
        Spacer(Modifier.weight(1f))
        if (actionLabel != null) {
            Row(
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onAction)
                    .heightIn(min = 48.dp)
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    color = GullakTheme.colors.link,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = GullakTheme.colors.link,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * A service shortcut: navy line icon (or white icon in a navy circle) over a label of up to two lines that
 * shrinks to fit rather than breaking a word, as in the Paytm app. A badge overlaps the top of the icon.
 */
@Composable
fun ServiceTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    filledCircle: Boolean = false,
) {
    val colors = GullakTheme.colors
    Column(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(width = 64.dp, height = 52.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .then(if (filledCircle) Modifier.background(colors.iconNavy, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (filledCircle) colors.onAction else colors.iconNavy,
                    modifier = Modifier.size(if (filledCircle) 24.dp else 30.dp),
                )
            }
            if (badge != null) {
                PaytmBadge(badge, modifier = Modifier.align(Alignment.TopCenter).offset(y = (-10).dp))
            }
        }
        Spacer(Modifier.size(6.dp))
        BasicText(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                color = colors.textBody,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp,
            ),
            maxLines = 2,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 12.sp),
        )
    }
}

/** Yellow "New" / "Popular" badge. */
@Composable
fun PaytmBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .background(GullakTheme.colors.badge, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
        maxLines = 1,
        softWrap = false,
        color = GullakTheme.colors.onBadge,
    )
}

/** Paytm's primary pill button ("Start with ₹11"). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = CircleShape,
        contentPadding = BUTTON_PADDING,
        colors = ButtonDefaults.buttonColors(
            containerColor = GullakTheme.colors.action,
            contentColor = GullakTheme.colors.onAction,
        ),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        OneLineLabel(
            text,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

/** Secondary pill ("View More", "+ New goal"): blue text on a light-blue outline. */
@Composable
fun OutlinePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = CircleShape,
        contentPadding = BUTTON_PADDING,
        border = BorderStroke(1.dp, GullakTheme.colors.chipSelectedBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = GullakTheme.colors.chipSelected,
            contentColor = GullakTheme.colors.chipSelectedText,
        ),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        OneLineLabel(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

/**
 * A button or chip label: always one line. A long label or a large phone font shrinks the text instead of wrapping it.
 */
@Composable
fun OneLineLabel(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = MIN_LABEL_SIZE, maxFontSize = style.fontSize),
    )
}

/** Amount chip (₹11 / ₹21 / ₹51): outline, light blue when selected. */
@Composable
fun AmountChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GullakTheme.colors
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(if (selected) colors.chipSelected else colors.card, CircleShape)
            .border(1.dp, if (selected) colors.chipSelectedBorder else colors.chipBorder, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        OneLineLabel(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) colors.chipSelectedText else colors.textBody,
        )
    }
}

/** A small tappable hint, such as a goal name suggestion. Small on purpose, so several fit on a line. */
@Composable
fun SuggestionBubble(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GullakTheme.colors
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.card)
            .border(1.dp, colors.chipBorder, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        OneLineLabel(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = colors.textBody)
    }
}

enum class StatusTone { SUCCESS, WARNING, ERROR }

/** Status chip ("On track", "Behind by ₹4,500", "Goal reached"). */
@Composable
fun StatusChip(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val colors = GullakTheme.colors
    val (container, content) = when (tone) {
        StatusTone.SUCCESS -> colors.successContainer to colors.successText
        StatusTone.WARNING -> colors.warningContainer to colors.warningText
        StatusTone.ERROR -> colors.errorContainer to colors.errorText
    }
    OneLineLabel(
        text,
        modifier = modifier
            .background(container, CircleShape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = content,
    )
}

/** Savings progress in Paytm cyan on the field grey. */
@Composable
fun SavingsProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 8.dp),
        color = GullakTheme.colors.jarFill,
        trackColor = GullakTheme.colors.field,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/** Thin divider used between rows inside a card. */
@Composable
fun CardDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier.padding(vertical = 12.dp), color = GullakTheme.colors.divider)
}

private val MIN_LABEL_SIZE = 9.sp
private val BUTTON_PADDING = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
