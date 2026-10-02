package com.clementine.panacea.ui.edit

import com.clementine.panacea.data.db.IngredientEntity
import com.clementine.panacea.data.db.MedicationCounts
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.data.db.MedicationName
import com.clementine.panacea.model.Category
import com.clementine.panacea.model.MedicationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationDraftTest {
    private val existing = listOf(MedicationName(1, "Sertraline"), MedicationName(2, "Ibuprofen"))

    @Test
    fun numbersAreReadAsPeopleTypeThem() {
        assertEquals(0.5, Drafts.parse("0.5")!!, 0.0)
        assertEquals(0.5, Drafts.parse(" 0,5 ")!!, 0.0)
        assertEquals(50.0, Drafts.parse("50")!!, 0.0)
        assertNull(Drafts.parse("-1"))
        assertNull(Drafts.parse("abc"))
        assertNull(Drafts.parse("NaN"))
    }

    @Test
    fun aNameIsNeededAndMustBeNew() {
        assertEquals("Give it a name.", Drafts.check(MedicationDraft(name = "  "), existing).name)
        assertEquals("You already have a medication called sertraline.", Drafts.check(MedicationDraft(name = "sertraline"), existing).name)
        // Its own name is fine when editing.
        assertNull(Drafts.check(MedicationDraft(id = 1, name = "Sertraline"), existing).name)
        assertTrue(Drafts.check(MedicationDraft(name = "Melatonin"), existing).none)
    }

    @Test
    fun doseIsOptionalWeightMustBeAboveZero() {
        assertNull(Drafts.check(MedicationDraft(name = "A", dose = ""), existing).dose)
        assertEquals("Enter a number, such as 50 or 0.5.", Drafts.check(MedicationDraft(name = "A", dose = "fifty"), existing).dose)
        assertNull(Drafts.check(MedicationDraft(name = "A", weight = ""), existing).weight)
        assertEquals("Enter a weight above 0, or leave it empty.", Drafts.check(MedicationDraft(name = "A", weight = "0"), existing).weight)
    }

    @Test
    fun emptyIngredientRowsAreIgnoredHalfFilledOnesAreNot() {
        val d = MedicationDraft(
            name = "A",
            ingredients = listOf(IngredientDraft(), IngredientDraft(amount = "5"), IngredientDraft("Caffeine", "x")),
        )
        assertEquals(mapOf(1 to "Give this ingredient a name.", 2 to "Enter a number, such as 50 or 0.5."), Drafts.check(d, existing).ingredients)
        val saved = Drafts.toIngredients(d.copy(ingredients = listOf(IngredientDraft(), IngredientDraft(" Caffeine ", "65"))), 7)
        assertEquals(listOf(IngredientEntity(medicationId = 7, position = 0, name = "Caffeine", amount = 65.0, unit = "mg")), saved)
    }

    @Test
    fun savingKeepsWhatTheFormDoesNotShow() {
        val before = MedicationEntity(
            id = 3, name = "Old", dose = 10.0, doseUnit = "mg", category = "OTC", type = "ORAL_TABLET", sortOrder = 4,
            lastMultiplier = 2.0, learnRoutine = true, mutedUntil = 99,
        )
        val draft = Drafts.from(before, emptyList()).copy(name = " New ", dose = "0,25", weight = "0.3", weightUnit = "oz", category = Category.PRESCRIBED, type = MedicationType.ORAL_CAPSULE)
        val after = Drafts.toEntity(draft, before, sortOrder = 0)
        assertEquals(before.copy(name = "New", dose = 0.25, weight = 0.3, weightUnit = "oz", category = "Prescribed", type = "ORAL_CAPSULE"), after)
    }

    @Test
    fun loadingShowsNumbersPlainly() {
        val med = MedicationEntity(id = 1, name = "A", dose = 0.0, doseUnit = "mg", weight = 0.20, category = "Uncategorized", type = "EDIBLE", sortOrder = 0)
        val d = Drafts.from(med, listOf(IngredientEntity(medicationId = 1, position = 0, name = "Zinc", amount = 12.5, unit = "mg")))
        assertEquals("", d.dose)
        assertEquals("0.2", d.weight)
        assertEquals(Category.UNCATEGORIZED, d.category)
        assertEquals(listOf(IngredientDraft("Zinc", "12.5", "mg")), d.ingredients)
    }

    @Test
    fun removeQuestionSaysWhatGoes() {
        assertEquals("It has no logged doses or reminders yet.", Drafts.removeQuestion(MedicationCounts(0, 0)))
        assertEquals("Its 1 logged dose goes with it. This can't be undone.", Drafts.removeQuestion(MedicationCounts(1, 0)))
        assertEquals("Its 42 logged doses and 1 reminder go with it. This can't be undone.", Drafts.removeQuestion(MedicationCounts(42, 1)))
    }
}
