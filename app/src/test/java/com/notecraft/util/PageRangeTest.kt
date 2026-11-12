package com.notecraft.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageRangeTest {

    @Test
    fun validRangeIsInclusive() {
        assertEquals(2..5, validPageRange(2, 5, 10))
        assertEquals(1..1, validPageRange(1, 1, 1))
    }

    @Test
    fun invalidRangesAreRejected() {
        assertNull(validPageRange(0, 3, 10))
        assertNull(validPageRange(4, 2, 10))
        assertNull(validPageRange(2, 11, 10))
        assertNull(validPageRange(1, 1, 0))
    }
}
