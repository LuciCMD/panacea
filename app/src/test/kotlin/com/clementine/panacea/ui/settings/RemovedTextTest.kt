package com.clementine.panacea.ui.settings

import com.clementine.panacea.ui.today.Fixtures.ms
import com.clementine.panacea.ui.today.Fixtures.now
import org.junit.Assert.assertEquals
import org.junit.Test

class RemovedTextTest {
    @Test
    fun saysWhenItWentAndWhenItGoesForGood() {
        assertEquals("Removed today · deleted in 30 days", RemovedText.status(ms(10, 2, 9, 0), now))
        assertEquals("Removed yesterday · deleted in 29 days", RemovedText.status(ms(10, 1, 9, 0), now))
        assertEquals("Removed 30 days ago · deleted within a day", RemovedText.status(ms(9, 2, 14, 0), now))
    }
}
