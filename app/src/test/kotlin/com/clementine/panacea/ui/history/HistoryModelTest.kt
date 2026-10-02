package com.clementine.panacea.ui.history

import com.clementine.panacea.data.db.DoseEntity
import com.clementine.panacea.data.db.DoseRow
import com.clementine.panacea.ui.today.Fixtures.formats
import com.clementine.panacea.ui.today.Fixtures.ms
import com.clementine.panacea.ui.today.Fixtures.now
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryModelTest {
    private fun row(id: Long, at: Long, amount: Double = 200.0, multiplier: Double = 1.0, weight: Double? = null) = DoseRow(
        DoseEntity(id = id, medicationId = 1, takenAt = at, multiplier = multiplier, amount = amount, unit = "mg",
            weight = weight, weightUnit = weight?.let { "g" }),
        name = "Ibuprofen",
        type = "ORAL_TABLET",
    )

    @Test
    fun daysAreNamedAndNewestComesFirst() {
        val days = HistoryModel.build(
            listOf(
                row(1, ms(9, 28, 8, 0)),
                row(2, ms(10, 2, 9, 0)),
                row(3, ms(10, 1, 22, 10)),
                row(4, ms(10, 2, 12, 0)),
                row(5, ms(9, 1, 8, 0)),
            ),
            now, formats,
        )
        assertEquals(listOf("Today", "Yesterday", "Monday", "Tuesday, 1 September"), days.map { it.title })
        assertEquals(listOf(4L, 2L), days[0].doses.map { it.id })
    }

    @Test
    fun amountShowsMultiplierAndWeightWhenTheyMatter() {
        val days = HistoryModel.build(listOf(row(1, ms(10, 2, 9, 0)), row(2, ms(10, 2, 8, 0), 400.0, 2.0, 0.62)), now, formats)
        assertEquals(listOf("200 mg", "400 mg · × 2 · 0.62 g"), days.single().doses.map { it.amount })
    }

    @Test
    fun removeQuestionSaysWhichDose() {
        val days = HistoryModel.build(listOf(row(1, ms(10, 1, 22, 10), 400.0, 2.0), row(2, ms(9, 21, 8, 0))), now, formats)
        assertEquals("Ibuprofen 400 mg taken yesterday at 22:10 will be removed from your history.", days[0].doses.single().removeQuestion)
        assertEquals("Ibuprofen 200 mg taken on 21 Sep at 8:00 will be removed from your history.", days[1].doses.single().removeQuestion)
    }
}
