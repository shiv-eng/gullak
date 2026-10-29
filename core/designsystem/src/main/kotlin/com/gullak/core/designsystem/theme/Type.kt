package com.gullak.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.gullak.core.designsystem.R

/** Inter, bundled under the SIL Open Font License (see third_party/inter). */
val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val Base = Typography()

private fun TextStyle.inter(weight: FontWeight? = null) =
    copy(fontFamily = InterFontFamily, fontWeight = weight ?: fontWeight)

internal val GullakMaterialTypography = Typography(
    displayLarge = Base.displayLarge.inter(FontWeight.Bold),
    displayMedium = Base.displayMedium.inter(FontWeight.Bold),
    displaySmall = Base.displaySmall.inter(FontWeight.Bold),
    headlineLarge = Base.headlineLarge.inter(FontWeight.Bold),
    headlineMedium = Base.headlineMedium.inter(FontWeight.Bold),
    headlineSmall = Base.headlineSmall.inter(FontWeight.SemiBold),
    titleLarge = Base.titleLarge.inter(FontWeight.SemiBold),
    titleMedium = Base.titleMedium.inter(FontWeight.SemiBold),
    titleSmall = Base.titleSmall.inter(FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.inter(FontWeight.Normal),
    bodyMedium = Base.bodyMedium.inter(FontWeight.Normal),
    bodySmall = Base.bodySmall.inter(FontWeight.Normal),
    // Button labels: 16sp semibold, so they stay readable on the blue fill.
    labelLarge = Base.labelLarge.inter(FontWeight.SemiBold).copy(fontSize = 16.sp),
    labelMedium = Base.labelMedium.inter(FontWeight.Medium),
    labelSmall = Base.labelSmall.inter(FontWeight.Medium),
)

/** Amount styles: semibold or bold with tabular figures so columns of rupees line up. */
@Immutable
data class GullakTypography(
    val amountLarge: TextStyle,
    val amountMedium: TextStyle,
    val amountSmall: TextStyle,
)

private const val TABULAR_FIGURES = "tnum"

internal val DefaultGullakTypography = GullakTypography(
    amountLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    amountMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    amountSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
)

internal val LocalGullakTypography = staticCompositionLocalOf { DefaultGullakTypography }
