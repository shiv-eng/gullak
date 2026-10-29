package com.gullak.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

internal val LightColorScheme: ColorScheme = lightColorScheme(
    primary = PaytmBlue,
    onPrimary = PaytmNavy,
    primaryContainer = PrimaryTint,
    onPrimaryContainer = PaytmNavy,
    secondary = PaytmNavy,
    onSecondary = White,
    secondaryContainer = PrimaryTint,
    onSecondaryContainer = PaytmNavy,
    background = White,
    onBackground = TextPrimaryLight,
    surface = White,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextBodyLight,
    surfaceContainerLowest = White,
    surfaceContainerLow = White,
    surfaceContainer = White,
    surfaceContainerHigh = SurfaceVariantLight,
    surfaceContainerHighest = SurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineLight,
    error = ErrorText,
    onError = White,
    errorContainer = ErrorTint,
    onErrorContainer = ErrorText,
)

internal val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = PaytmBlue,
    onPrimary = PaytmNavy,
    primaryContainer = BubbleDark,
    onPrimaryContainer = White,
    secondary = BubbleDark,
    onSecondary = White,
    secondaryContainer = BubbleDark,
    onSecondaryContainer = White,
    background = BackgroundDark,
    onBackground = White,
    surface = BackgroundDark,
    onSurface = White,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextBodyDark,
    surfaceContainerLowest = BackgroundDark,
    surfaceContainerLow = CardDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = CardDark,
    surfaceContainerHighest = BubbleDark,
    outline = OutlineDark,
    outlineVariant = OutlineDark,
    error = ErrorDark,
    onError = BackgroundDark,
    errorContainer = ErrorTintDark,
    onErrorContainer = ErrorDark,
)

/** Gullak's Material 3 theme in the Paytm visual style. Dynamic colour is off: the brand palette is fixed. */
@Composable
fun GullakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalGullakColors provides if (darkTheme) DarkGullakColors else LightGullakColors,
        LocalGullakTypography provides DefaultGullakTypography,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = GullakMaterialTypography,
            shapes = GullakShapes,
            content = content,
        )
    }
}

/** Access to Gullak's extra tokens, alongside `MaterialTheme`. */
object GullakTheme {
    val colors: GullakColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGullakColors.current

    val typography: GullakTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalGullakTypography.current
}
