package com.clementine.panacea.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun takenIngredientsComeBackAsStored() {
        val taken = listOf(TakenIngredient("Acetaminophen", 487.5, "mg"), TakenIngredient("Caffeine", 0.1 + 0.2, "mg"))
        assertEquals(taken, converters.textToTaken(converters.takenToText(taken)))
        assertEquals(emptyList<TakenIngredient>(), converters.textToTaken(converters.takenToText(emptyList())))
        // A tab or line break typed into a name can't split the record.
        assertEquals(
            listOf(TakenIngredient("Vitamin B6", 1.5, "mg")),
            converters.textToTaken(converters.takenToText(listOf(TakenIngredient("Vitamin\tB6", 1.5, "mg")))),
        )
    }
}
