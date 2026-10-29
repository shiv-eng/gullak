package com.gullak.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Brand tokens from the Paytm-style palette (prompt section 9).
internal val PaytmBlue = Color(0xFF00BAF2)
internal val PaytmNavy = Color(0xFF002970)
internal val White = Color(0xFFFFFFFF)
internal val SurfaceVariantLight = Color(0xFFF7F9FC)
internal val OutlineLight = Color(0xFFE4E9F0)
internal val TextPrimaryLight = Color(0xFF101010)
internal val TextBodyLight = Color(0xFF505050)
internal val TextMutedLight = Color(0xFF707070)
internal val Success = Color(0xFF27AE60)
internal val Warning = Color(0xFFE8A33D)
internal val Error = Color(0xFFD64545)
internal val GullakClay = Color(0xFFB8572E)
internal val Gold = Color(0xFFD9A930)

// Contrast adjustments. White on Paytm blue is 2.26:1, so text on blue is navy (6.0:1).
// Status hues fail as text on white (success 2.87, warning 2.16, error 4.38), so chips and
// status text use a light tint with a darker shade of the same hue (all >= 5.2:1).
internal val PrimaryTint = Color(0xFFE5F8FE)
internal val SuccessText = Color(0xFF1B7340)
internal val SuccessTint = Color(0xFFE6F5EC)
internal val WarningText = Color(0xFF7A4F00)
internal val WarningTint = Color(0xFFFDF1DC)
internal val ErrorText = Color(0xFFA82E2E)
internal val ErrorTint = Color(0xFFFCEAEA)

// Dark theme: navy-tinted surfaces.
internal val BackgroundDark = Color(0xFF0B1A33)
internal val CardDark = Color(0xFF13254A)
internal val BubbleDark = Color(0xFF1D3A70)
internal val OutlineDark = Color(0xFF2A3D63)
internal val TextBodyDark = Color(0xFFC5CFDF)
internal val TextMutedDark = Color(0xFF9AA8C0)
internal val SuccessDark = Color(0xFF7FDCA5)
internal val SuccessTintDark = Color(0xFF173B35)
internal val WarningDark = Color(0xFFF5C77E)
internal val WarningTintDark = Color(0xFF3A3222)
internal val ErrorDark = Color(0xFFF59A9A)
internal val ErrorTintDark = Color(0xFF3D2333)
