package com.clementine.panacea.ui.components

import com.clementine.panacea.ui.today.Fixtures.at
import com.clementine.panacea.ui.today.Fixtures.now
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class TimeDialogTest {
    @Test
    fun aDoseIsTodayUnlessYesterdayIsPickedOrTheTimeHasntComeYet() {
        // It's 13:16.
        assertEquals(at(10, 2, 9, 30), takenAt(LocalTime.of(9, 30), yesterday = false, now))
        assertEquals(at(10, 1, 9, 30), takenAt(LocalTime.of(9, 30), yesterday = true, now))
        assertEquals(at(10, 1, 22, 0), takenAt(LocalTime.of(22, 0), yesterday = false, now))
        assertEquals(at(10, 2, 13, 16), takenAt(LocalTime.of(13, 16), yesterday = false, now))
    }
}
