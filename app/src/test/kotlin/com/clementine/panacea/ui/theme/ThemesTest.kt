package com.clementine.panacea.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.clementine.panacea.data.Settings
import com.clementine.panacea.resolveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Every theme must read at least as well as the house theme does, by its contrast table */
class ThemesTest {

    private fun luminance(c: Color): Double {
        fun channel(v: Float) = if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun check(theme: Themes, what: String, a: Color, b: Color, min: Double) {
        val ratio = contrast(a, b)
        assertTrue("${theme.label}: $what is %.1f:1, needs %.1f:1".format(ratio, min), ratio >= min)
    }

    @Test
    fun everyThemeIsReadable() {
        for (t in Themes.entries) {
            val p = t.palette
            for (bg in listOf(p.ground, p.surface, p.raised)) check(t, "ink", p.ink, bg, 7.0)
            check(t, "ink on field", p.ink, p.field, 7.0)
            check(t, "muted on surface", p.muted, p.surface, 4.5)
            check(t, "placeholder (muted) on field", p.muted, p.field, 4.5)
            check(t, "faint on surface", p.faint, p.surface, 3.5)
            check(t, "faint icon on field", p.faint, p.field, 3.0)
            check(t, "text on accent", p.onAccent, p.accent, 4.5)
            check(t, "accent on surface", p.accent, p.surface, 4.5)
            // Tonal Take, selected chips and menu rows: the accent on its own wash, over whatever is behind
            for (bg in listOf(p.ground, p.surface, p.raised)) check(t, "accent on its wash", p.accent, p.accentWash.compositeOver(bg), 4.5)
            check(t, "warn on surface", p.warn, p.surface, 4.5)
            check(t, "good on surface", p.good, p.surface, 3.0)
            check(t, "text on the delete button", p.onAccent, p.bad, 4.5)
            for (bg in listOf(p.ground, p.surface, p.raised)) check(t, "bad text", p.bad, bg, 4.5)
            // A light theme's switch track is the accent, which must show on the surface around it.
            if (p.isLight) check(t, "switch track on surface", p.accent, p.surface, 3.0)
        }
    }

    @Test
    fun keysAreUnique() {
        assertEquals(Themes.entries.size, Themes.entries.map { it.key }.toSet().size)
        assertTrue(Themes.entries.none { it.key == Settings.THEME_SYSTEM })
    }

    @Test
    fun matchSystemFollowsThePhone() {
        assertEquals(Themes.SLATE, resolveTheme(Settings.THEME_SYSTEM, systemDark = true))
        assertEquals(Themes.DAYLIGHT, resolveTheme(Settings.THEME_SYSTEM, systemDark = false))
        assertEquals(Themes.OCEAN, resolveTheme("ocean", systemDark = false))
        assertEquals(Themes.DEFAULT, resolveTheme("gone", systemDark = true))
    }

    @Test
    fun every34ThemeHasAHome() {
        assertEquals(
            listOf(Themes.SLATE, Themes.MOSS, Themes.SLATE, Themes.OCEAN, Themes.EMBER, Themes.EMBER, Themes.MIDNIGHT, Themes.DAYLIGHT),
            (0..7).map(Themes::from34),
        )
        assertEquals(Themes.SLATE, Themes.from34(42))
    }
}
