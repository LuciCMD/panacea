package com.clementine.panacea.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    @Test
    fun ounceAndPoundUseExactFactors() {
        assertEquals(28.349523125, WeightUnit.OUNCE.convert(1.0, WeightUnit.GRAM), 0.0)
        assertEquals(453.59237, WeightUnit.POUND.convert(1.0, WeightUnit.GRAM), 1e-12)
        assertEquals(16.0, WeightUnit.POUND.convert(1.0, WeightUnit.OUNCE), 1e-12)
    }

    @Test
    fun metricConversions() {
        assertEquals(250.0, WeightUnit.GRAM.convert(0.25, WeightUnit.MILLIGRAM), 1e-9)
        assertEquals(0.5, WeightUnit.MILLIGRAM.convert(500.0, WeightUnit.GRAM), 1e-12)
        assertEquals(1500.0, WeightUnit.KILOGRAM.convert(1.5, WeightUnit.GRAM), 1e-9)
        assertEquals(50.0, WeightUnit.MILLIGRAM.convert(0.05, WeightUnit.MICROGRAM), 1e-9)
    }

    @Test
    fun sameUnitReturnsTheValueUntouched() {
        assertEquals(0.1, WeightUnit.OUNCE.convert(0.1, WeightUnit.OUNCE), 0.0)
    }

    @Test
    fun roundTripDoesNotDrift() {
        var grams = 0.31
        repeat(100) { grams = WeightUnit.OUNCE.convert(WeightUnit.GRAM.convert(grams, WeightUnit.OUNCE), WeightUnit.GRAM) }
        assertEquals(0.31, grams, 1e-12)
    }

    @Test
    fun unknownWeightUnitFallsBackToGrams() {
        assertEquals(WeightUnit.GRAM, WeightUnit.fromKey(null))
        assertEquals(WeightUnit.GRAM, WeightUnit.fromKey("stone"))
        assertEquals(WeightUnit.OUNCE, WeightUnit.fromKey("oz"))
    }

    @Test
    fun formatsAmountsWithoutTrailingZeros() {
        assertEquals("50", formatAmount(50.0))
        assertEquals("0.25", formatAmount(0.25))
        assertEquals("28.35", formatAmount(28.349523125.let { Math.round(it * 100) / 100.0 }))
        assertEquals("0.333", formatAmount(1.0 / 3))
        assertEquals("?", formatAmount(Double.NaN))
    }
}
