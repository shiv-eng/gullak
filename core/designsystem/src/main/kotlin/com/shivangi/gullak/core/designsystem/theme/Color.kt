package com.shivangi.gullak.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Paytm app theme, taken from the app's Home and Paytm Gold screens (screenshots shared 30 Oct 2026).
// Only these colours are used; Gullak's jar is drawn in Paytm navy and blue.

/** Primary pill buttons ("Start with ₹11", "Upgrade SIP"). White text is 8.4:1. */
internal val ActionBlue = Color(0xFF193E8E)

/** Headings on brand surfaces, the UPI icon circles and the jar outline. */
internal val PaytmNavy = Color(0xFF002970)
internal val IconNavy = Color(0xFF0A3D91)

/** Paytm cyan: small accents and the jar's fill. Never behind text. */
internal val PaytmCyan = Color(0xFF00BAF2)

/** Text links ("View All →", "Learn about Gold Security"). 4.9:1 on white. */
internal val LinkBlue = Color(0xFF1A6FD6)

internal val White = Color(0xFFFFFFFF)
internal val PageBackground = Color(0xFFF5F5F5)
internal val FieldBackground = Color(0xFFF2F3F5)
internal val Divider = Color(0xFFE6E8EC)
internal val ChipBorder = Color(0xFFDCDFE4)
internal val ChipSelected = Color(0xFFE7F1FA)
internal val ChipSelectedBorder = Color(0xFF2F7BBE)
internal val ChipSelectedText = Color(0xFF1A6FB8)

internal val TextPrimary = Color(0xFF242424)
internal val TextBody = Color(0xFF3C4043)
internal val TextMuted = Color(0xFF666B73)

/** Gold values (grams, "your gold can be worth"); darkened from the screenshot's #A8862E to reach 4.9:1. */
internal val GoldText = Color(0xFF8C6D1F)

/** Returns and "+0.29%" (green) and LIVE / failures (red), as in Paytm Gold. */
internal val Gain = Color(0xFF188038)
internal val Loss = Color(0xFFD93025)

/** Yellow "New" / "Popular" badges. */
internal val Badge = Color(0xFFFFD15C)
internal val OnBadge = Color(0xFF4A3800)

// Status chips: light tint with a darker text of the same hue (all >= 5.2:1).
internal val SuccessText = Color(0xFF137333)
internal val SuccessTint = Color(0xFFE6F4EA)
internal val WarningText = Color(0xFF7A5900)
internal val WarningTint = Color(0xFFFFF4D6)
internal val ErrorText = Color(0xFFB3261E)
internal val ErrorTint = Color(0xFFFCE8E6)

// Dark theme: navy surfaces with the same accents.
internal val BackgroundDark = Color(0xFF0B1A33)
internal val CardDark = Color(0xFF13254A)
internal val FieldDark = Color(0xFF1D3A70)
internal val DividerDark = Color(0xFF2A3D63)
internal val TextBodyDark = Color(0xFFC5CFDF)
internal val TextMutedDark = Color(0xFF9AA8C0)
internal val LinkDark = Color(0xFF7CC4FF)
internal val GoldDark = Color(0xFFE2C46B)
internal val SuccessDark = Color(0xFF7FDCA5)
internal val SuccessTintDark = Color(0xFF173B35)
internal val WarningDark = Color(0xFFF5C77E)
internal val WarningTintDark = Color(0xFF3A3222)
internal val ErrorDark = Color(0xFFF59A9A)
internal val ErrorTintDark = Color(0xFF3D2333)
