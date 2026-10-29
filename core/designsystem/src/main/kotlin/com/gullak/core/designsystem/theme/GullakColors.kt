package com.gullak.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colours Material 3's scheme has no slot for: text tiers, status states and the gullak illustration. */
@Immutable
data class GullakColors(
    val textPrimary: Color,
    val textBody: Color,
    val textMuted: Color,
    /** Text links and emphasised amounts on the page background. */
    val emphasis: Color,
    val success: Color,
    val successText: Color,
    val successContainer: Color,
    val warning: Color,
    val warningText: Color,
    val warningContainer: Color,
    val error: Color,
    val errorText: Color,
    val errorContainer: Color,
    /** Gullak's chat bubbles; user bubbles use `surfaceVariant`. */
    val gullakBubble: Color,
    val onGullakBubble: Color,
    val gullakClay: Color,
    val gold: Color,
)

internal val LightGullakColors = GullakColors(
    textPrimary = TextPrimaryLight,
    textBody = TextBodyLight,
    textMuted = TextMutedLight,
    emphasis = PaytmNavy,
    success = Success,
    successText = SuccessText,
    successContainer = SuccessTint,
    warning = Warning,
    warningText = WarningText,
    warningContainer = WarningTint,
    error = Error,
    errorText = ErrorText,
    errorContainer = ErrorTint,
    gullakBubble = PaytmNavy,
    onGullakBubble = White,
    gullakClay = GullakClay,
    gold = Gold,
)

internal val DarkGullakColors = GullakColors(
    textPrimary = White,
    textBody = TextBodyDark,
    textMuted = TextMutedDark,
    emphasis = PaytmBlue,
    success = SuccessDark,
    successText = SuccessDark,
    successContainer = SuccessTintDark,
    warning = WarningDark,
    warningText = WarningDark,
    warningContainer = WarningTintDark,
    error = ErrorDark,
    errorText = ErrorDark,
    errorContainer = ErrorTintDark,
    gullakBubble = BubbleDark,
    onGullakBubble = White,
    gullakClay = GullakClay,
    gold = Gold,
)

internal val LocalGullakColors = staticCompositionLocalOf { LightGullakColors }
