package com.shivangi.gullak.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

internal val LightColorScheme: ColorScheme = lightColorScheme(
    primary = ActionBlue,
    onPrimary = White,
    primaryContainer = ChipSelected,
    onPrimaryContainer = ChipSelectedText,
    secondary = PaytmNavy,
    onSecondary = White,
    secondaryContainer = ChipSelected,
    onSecondaryContainer = PaytmNavy,
    tertiary = PaytmCyan,
    onTertiary = PaytmNavy,
    background = PageBackground,
    onBackground = TextPrimary,
    surface = White,
    onSurface = TextPrimary,
    surfaceVariant = FieldBackground,
    onSurfaceVariant = TextBody,
    surfaceContainerLowest = White,
    surfaceContainerLow = White,
    surfaceContainer = White,
    surfaceContainerHigh = White,
    surfaceContainerHighest = FieldBackground,
    outline = ChipBorder,
    outlineVariant = Divider,
    error = ErrorText,
    onError = White,
    errorContainer = ErrorTint,
    onErrorContainer = ErrorText,
)

internal val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = PaytmCyan,
    onPrimary = PaytmNavy,
    primaryContainer = FieldDark,
    onPrimaryContainer = White,
    secondary = FieldDark,
    onSecondary = White,
    secondaryContainer = FieldDark,
    onSecondaryContainer = White,
    tertiary = PaytmCyan,
    onTertiary = PaytmNavy,
    background = BackgroundDark,
    onBackground = White,
    surface = CardDark,
    onSurface = White,
    surfaceVariant = FieldDark,
    onSurfaceVariant = TextBodyDark,
    surfaceContainerLowest = BackgroundDark,
    surfaceContainerLow = CardDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = CardDark,
    surfaceContainerHighest = FieldDark,
    outline = DividerDark,
    outlineVariant = DividerDark,
    error = ErrorDark,
    onError = BackgroundDark,
    errorContainer = ErrorTintDark,
    onErrorContainer = ErrorDark,
)

/** Gullak's Material 3 theme in the Paytm app's colours. Dynamic colour is off: the brand palette is fixed. */
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
