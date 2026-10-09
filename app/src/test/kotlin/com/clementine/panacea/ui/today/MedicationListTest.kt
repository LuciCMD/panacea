package com.clementine.panacea.ui.today

import com.clementine.panacea.data.ListLayout
import com.clementine.panacea.data.db.MedicationEntity
import com.clementine.panacea.model.Category
import com.clementine.panacea.model.MedicationType
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationListTest {
    private fun card(id: Long, category: Category, due: String? = null, overdue: Boolean = false, dueNow: Boolean = overdue) = CardState(
        medication = MedicationEntity(id = id, name = "M$id", dose = 1.0, doseUnit = "mg", category = category.key, type = "ORAL_TABLET", sortOrder = id.toInt()),
        type = MedicationType.ORAL_TABLET, category = category, doseLine = "", lastTakenLine = "", dueLine = due,
        overdue = overdue, dueNow = dueNow, progress = 0f,
    )

    private val cards = listOf(
        card(1, Category.OTC),
        card(2, Category.PRESCRIBED, "Next at 9:00", overdue = true),
        card(3, Category.PRESCRIBED, "Next at 21:00"),
        card(4, Category.OTC, "Next at 14:00", dueNow = true),
    )

    @Test
    fun groupedSplitsByCategoryInTheirUsualOrder() {
        val sections = listSections(cards, ListLayout.GROUPED)
        assertEquals(listOf("Prescribed", "OTC"), sections.map { it.label })
        // Today's own order is kept inside each
        assertEquals(listOf(1L, 4L), sections[1].cards.map { it.medication.id })
    }

    @Test
    fun oneCategoryNeedsNoLabel() {
        val sections = listSections(cards.filter { it.category == Category.OTC }, ListLayout.COMPACT)
        assertEquals(listOf<String?>(null), sections.map { it.label })
    }

    @Test
    fun whenDuePutsWhatNeedsYouFirst() {
        val sections = listSections(cards, ListLayout.WHEN_DUE)
        assertEquals(listOf("Missed", "Due Now", "Later", "As Needed"), sections.map { it.label })
        assertEquals(listOf(listOf(2L), listOf(4L), listOf(3L), listOf(1L)), sections.map { s -> s.cards.map { it.medication.id } })
    }
}
