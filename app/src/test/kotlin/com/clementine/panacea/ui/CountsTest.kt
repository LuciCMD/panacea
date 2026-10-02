package com.clementine.panacea.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CountsTest {
    @Test
    fun bigNumbersAreGroupedTheLocalWay() {
        assertEquals("1 dose", counted(1, "dose", locale = Locale.US))
        assertEquals("0 doses", counted(0, "dose", locale = Locale.US))
        assertEquals("4,145 doses", counted(4145, "dose", locale = Locale.US))
        assertEquals("4.145 doses", counted(4145, "dose", locale = Locale.GERMANY))
    }
}
