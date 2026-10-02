package com.clementine.panacea.ui.rearrange

import org.junit.Assert.assertEquals
import org.junit.Test

class MovedTest {
    private val abcd = listOf("a", "b", "c", "d")

    @Test
    fun theOthersCloseUpAroundTheMovedOne() {
        assertEquals(listOf("b", "c", "a", "d"), moved(abcd, 0, 2))
        assertEquals(listOf("d", "a", "b", "c"), moved(abcd, 3, 0))
        assertEquals(listOf("a", "c", "b", "d"), moved(abcd, 1, 2))
    }

    @Test
    fun movesOffTheEndChangeNothing() {
        assertEquals(abcd, moved(abcd, 0, -1))
        assertEquals(abcd, moved(abcd, 3, 4))
        assertEquals(abcd, moved(abcd, 2, 2))
    }
}
