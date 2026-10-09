package com.clementine.panacea.ui.edit

import com.clementine.panacea.data.catalog.Catalog
import com.clementine.panacea.data.catalog.CatalogEntry
import com.clementine.panacea.data.catalog.CatalogForm
import com.clementine.panacea.data.catalog.CatalogHit
import com.clementine.panacea.data.catalog.CatalogKind
import com.clementine.panacea.data.catalog.OtherActive
import com.clementine.panacea.model.Category
import com.clementine.panacea.model.MedicationType
import org.junit.Assert.assertEquals
import org.junit.Test

class FindTextTest {
    private val tablet = CatalogForm(MedicationType.ORAL_TABLET, Category.OTC, 200.0, "mg", null, emptyList())
    private val liquid = CatalogForm(MedicationType.LIQUID_SYRUP, Category.OTC, 100.0, "mg", "5 mL", emptyList())
    private val percocet = CatalogForm(MedicationType.ORAL_TABLET, Category.PRESCRIBED, 5.0, "mg", null, listOf(OtherActive("Acetaminophen", 325.0, "mg")))

    @Test
    fun formsReadAsOnTheBox() {
        assertEquals("200 mg tablet", FindText.formLine(tablet))
        assertEquals("100 mg per 5 mL liquid", FindText.formLine(liquid))
        assertEquals("5 mg tablet with acetaminophen 325 mg", FindText.formLine(percocet))
        assertEquals("Prescription", FindText.formCategory(percocet))
    }

    @Test
    fun hitsSayWhatTheyAre() {
        val advil = CatalogEntry(0, CatalogKind.BRAND, "Advil", emptyList(), listOf("Ibuprofen"), listOf(3672), listOf(tablet))
        assertEquals("Brand of ibuprofen", FindText.hitLine(CatalogHit(advil, null)))
        val acetaminophen = CatalogEntry(1, CatalogKind.GENERIC, "Acetaminophen", listOf("paracetamol"), listOf("acetaminophen"), listOf(1983), listOf(tablet))
        assertEquals("Generic, also called paracetamol", FindText.hitLine(CatalogHit(acetaminophen, "paracetamol")))
    }

    @Test
    fun aPickFillsInTheForm() {
        val entry = CatalogEntry(2, CatalogKind.BRAND, "Percocet", emptyList(), listOf("oxycodone"), listOf(5284603), listOf(percocet))
        val d = catalogDraft(entry, percocet)
        assertEquals("Percocet", d.name)
        assertEquals("5", d.dose)
        assertEquals(Category.PRESCRIBED, d.category)
        assertEquals(listOf(IngredientDraft("Acetaminophen", "325", "mg")), d.ingredients)
        // Another strength: the name, type and category, but no amount
        assertEquals("", catalogDraft(entry, null).dose)
        assertEquals(Category.PRESCRIBED, catalogDraft(entry, null).category)
    }

    @Test
    fun namesMatchWhateverTheCaseOrAccents() {
        assertEquals("cafe au lait", Catalog.fold("Café-au-Lait!".replace('-', ' ')))
        assertEquals("vitamin d3", Catalog.fold("  Vitamin  D3 "))
    }
}
