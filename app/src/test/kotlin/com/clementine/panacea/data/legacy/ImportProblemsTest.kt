package com.clementine.panacea.data.legacy

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportProblemsTest {
    @Test
    fun shownUntilSeen() {
        val stored = "A reminder for \"X\" was skipped: that medication no longer exists.\n\n1 dose of \"Y\" could not be read.\n"
        assertEquals(
            listOf("A reminder for \"X\" was skipped: that medication no longer exists.", "1 dose of \"Y\" could not be read."),
            importProblems(stored, seen = null),
        )
        assertEquals(emptyList<String>(), importProblems(stored, seen = "1790000000000"))
        assertEquals(emptyList<String>(), importProblems(null, seen = null))
    }
}
