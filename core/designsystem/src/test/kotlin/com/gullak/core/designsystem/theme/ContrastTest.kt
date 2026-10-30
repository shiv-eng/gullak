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

    private fun assertPairs(c: GullakColors) {
        assertReadable("action button", c.onAction, c.action)
        assertReadable("primary text on card", c.textPrimary, c.card)
        assertReadable("body text on card", c.textBody, c.card)
        assertReadable("muted text on card", c.textMuted, c.card)
        assertReadable("muted text on page", c.textMuted, c.page)
        assertReadable("link on card", c.link, c.card)
        assertReadable("text on field", c.textPrimary, c.field)
        assertReadable("selected chip", c.chipSelectedText, c.chipSelected)
        assertReadable("gold value", c.gold, c.card)
        assertReadable("gain", c.gain, c.card)
        assertReadable("loss", c.loss, c.card)
        assertReadable("badge", c.onBadge, c.badge)
        assertReadable("success chip", c.successText, c.successContainer)
        assertReadable("warning chip", c.warningText, c.warningContainer)
        assertReadable("error chip", c.errorText, c.errorContainer)
    }

    @Test
    fun lightThemePairsMeetBodyTextContrast() = assertPairs(LightGullakColors)

    @Test
    fun darkThemePairsMeetBodyTextContrast() = assertPairs(DarkGullakColors)

    @Test
    fun materialSchemeButtonsAreReadable() {
        assertReadable("light onPrimary", LightColorScheme.onPrimary, LightColorScheme.primary)
        assertReadable("dark onPrimary", DarkColorScheme.onPrimary, DarkColorScheme.primary)
        assertReadable("light onSecondary", LightColorScheme.onSecondary, LightColorScheme.secondary)
    }

    private companion object {
        const val BODY_TEXT_MIN = 4.5
    }
}
