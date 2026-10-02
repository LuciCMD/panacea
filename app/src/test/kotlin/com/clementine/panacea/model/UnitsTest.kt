package com.clementine.panacea.model

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertNull
import java.util.Locale

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

    @Test
    fun amountsOnScreenAreGroupedTheLocalWay() {
        assertEquals("1,050", formatAmount(1050.0, Locale.US))
        assertEquals("12,345.678", formatAmount(12345.6781, Locale.US))
        assertEquals("1.050", formatAmount(1050.0, Locale.GERMANY))
        assertEquals("0,25", formatAmount(0.25, Locale.GERMANY))
        assertEquals("999", formatAmount(999.0, Locale.US))
    }

    @Test
    fun amountsToEditOrSaveAreNeverGrouped() {
        assertEquals("1050", plainAmount(1050.0))
        assertEquals("0.25", plainAmount(0.25))
        assertEquals("0.333", plainAmount(1.0 / 3))
    }

    @Test
    fun typedAmountsReadTheWayTheyWereMeant() {
        assertEquals(1000.0, parseAmount("1,000", Locale.US)!!, 0.0)
        assertEquals(1234567.0, parseAmount("1,234,567", Locale.US)!!, 0.0)
        assertEquals(0.5, parseAmount("0,5", Locale.US)!!, 0.0)
        assertEquals(1.5, parseAmount("1,5", Locale.US)!!, 0.0)
        assertEquals(0.25, parseAmount("0,250", Locale.US)!!, 0.0)
        assertEquals(1000.5, parseAmount("1,000.5", Locale.US)!!, 0.0)
        assertEquals(1000.5, parseAmount("1.000,5", Locale.US)!!, 0.0)
        assertEquals(1000.0, parseAmount("1.000", Locale.GERMANY)!!, 0.0)
        assertEquals(2.5, parseAmount("2,5", Locale.GERMANY)!!, 0.0)
        assertEquals(1000.0, parseAmount("1 000", Locale.FRANCE)!!, 0.0)
        assertEquals(50.0, parseAmount(" 50 ", Locale.US)!!, 0.0)
        assertNull(parseAmount("-2", Locale.US))
        assertNull(parseAmount("abc", Locale.US))
    }
}
