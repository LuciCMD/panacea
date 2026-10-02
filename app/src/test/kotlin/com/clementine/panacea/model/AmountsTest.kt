package com.clementine.panacea.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountsTest {
    @Test
    fun presetsNeverSavedAreTheDefaults() {
        assertEquals(Amounts.DEFAULT_PRESETS, Amounts.parsePresets(null))
    }

    @Test
    fun storedPresetsAreCleanedAndSorted() {
        assertEquals(listOf(0.5, 1.0, 3.0), Amounts.parsePresets("3,0.5,abc,-1,1.0,0.5"))
        assertEquals(emptyList<Double>(), Amounts.parsePresets(""))
    }

    @Test
    fun addingAPresetKeepsOrderAndSkipsRepeats() {
        assertEquals(listOf(0.5, 1.5, 2.0), Amounts.withPreset(listOf(0.5, 2.0), 1.5))
        assertEquals(listOf(0.5, 2.0), Amounts.withPreset(listOf(0.5, 2.0), 2.0))
        val full = (1..Amounts.MAX_PRESETS).map { it.toDouble() }
        assertEquals(full, Amounts.withPreset(full, 0.5))
    }

    @Test
    fun stepsAreHalvesAboveAHalfAndQuartersBelow() {
        assertEquals(1.5, Amounts.more(1.0), 0.0)
        assertEquals(0.5, Amounts.more(0.25), 0.0)
        assertEquals(0.5, Amounts.less(1.0), 0.0)
        assertEquals(0.25, Amounts.less(0.5), 0.0)
        assertEquals(Amounts.MIN, Amounts.less(0.25), 0.0)
        assertEquals(Amounts.MAX, Amounts.more(Amounts.MAX), 0.0)
    }
}
