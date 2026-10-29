package com.gullak.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG contrast for every text/background pair the theme uses: 4.5:1 for body text. */
class ContrastTest {

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun assertReadable(
        name: String,
        text: Color,
        background: Color,
    ) {
        val ratio = contrast(text, background)
        assertTrue("$name is %.2f:1, needs 4.5:1".format(ratio), ratio >= BODY_TEXT_MIN)
    }

    @Test
    fun lightThemePairsMeetBodyTextContrast() {
        val c = LightGullakColors
        val s = LightColorScheme
        assertReadable("onPrimary on primary", s.onPrimary, s.primary)
        assertReadable("onSecondary on secondary", s.onSecondary, s.secondary)
        assertReadable("textPrimary on background", c.textPrimary, s.background)
        assertReadable("textBody on background", c.textBody, s.background)
        assertReadable("textMuted on background", c.textMuted, s.background)
        assertReadable("textMuted on surfaceVariant", c.textMuted, s.surfaceVariant)
        assertReadable("emphasis on background", c.emphasis, s.background)
        assertReadable("success chip", c.successText, c.successContainer)
        assertReadable("warning chip", c.warningText, c.warningContainer)
        assertReadable("error chip", c.errorText, c.errorContainer)
        assertReadable("error text on background", s.error, s.background)
        assertReadable("Gullak bubble", c.onGullakBubble, c.gullakBubble)
    }

    @Test
    fun darkThemePairsMeetBodyTextContrast() {
        val c = DarkGullakColors
        val s = DarkColorScheme
        assertReadable("onPrimary on primary", s.onPrimary, s.primary)
        assertReadable("textPrimary on background", c.textPrimary, s.background)
        assertReadable("textBody on card", c.textBody, s.surfaceContainer)
        assertReadable("textMuted on card", c.textMuted, s.surfaceContainer)
        assertReadable("emphasis on card", c.emphasis, s.surfaceContainer)
        assertReadable("success chip", c.successText, c.successContainer)
        assertReadable("warning chip", c.warningText, c.warningContainer)
        assertReadable("error chip", c.errorText, c.errorContainer)
        assertReadable("Gullak bubble", c.onGullakBubble, c.gullakBubble)
    }

    @Test
    fun whiteOnPaytmBlueIsBelowLargeTextMinimum() {
        // Documents why onPrimary is navy rather than the spec's white.
        assertTrue(contrast(White, PaytmBlue) < LARGE_TEXT_MIN)
    }

    private companion object {
        const val BODY_TEXT_MIN = 4.5
        const val LARGE_TEXT_MIN = 3.0
    }
}
