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

/**
 * Paytm's compact scale (measured from the app's screens): body 13-14sp, card titles 16-18sp. Sizes are set
 * here, not left at Material's larger defaults.
 */
private fun TextStyle.sized(
    size: Int,
    line: Int,
    weight: FontWeight,
) = inter(weight).copy(fontSize = size.sp, lineHeight = line.sp)

internal val GullakMaterialTypography = Typography(
    displayLarge = Base.displayLarge.sized(40, 48, FontWeight.Bold),
    displayMedium = Base.displayMedium.sized(32, 40, FontWeight.Bold),
    displaySmall = Base.displaySmall.sized(26, 32, FontWeight.Bold),
    headlineLarge = Base.headlineLarge.sized(24, 30, FontWeight.Bold),
    headlineMedium = Base.headlineMedium.sized(22, 28, FontWeight.Bold),
    headlineSmall = Base.headlineSmall.sized(20, 26, FontWeight.SemiBold),
    titleLarge = Base.titleLarge.sized(18, 24, FontWeight.Bold),
    titleMedium = Base.titleMedium.sized(15, 20, FontWeight.SemiBold),
    titleSmall = Base.titleSmall.sized(14, 18, FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.sized(14, 20, FontWeight.Normal),
    bodyMedium = Base.bodyMedium.sized(13, 18, FontWeight.Normal),
    bodySmall = Base.bodySmall.sized(12, 16, FontWeight.Normal),
    labelLarge = Base.labelLarge.sized(14, 18, FontWeight.SemiBold),
    labelMedium = Base.labelMedium.sized(12, 16, FontWeight.Medium),
    labelSmall = Base.labelSmall.sized(10, 14, FontWeight.Medium),
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
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    amountMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    amountSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
)

internal val LocalGullakTypography = staticCompositionLocalOf { DefaultGullakTypography }
