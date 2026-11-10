package com.notecraft.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PageReorderTest {

    @Test
    fun dragToTheRightMovesAcrossColumns() {
        assertEquals(
            2,
            pageReorderTarget(
                index = 1,
                dragX = 99f,
                dragY = 0f,
                columns = 3,
                cellWidthPx = 100f,
                rowHeightPx = 200f,
                lastIndex = 8,
            ),
        )
    }

    @Test
    fun dragDownMovesAcrossRows() {
        assertEquals(
            4,
            pageReorderTarget(
                index = 1,
                dragX = 0f,
                dragY = 190f,
                columns = 3,
                cellWidthPx = 100f,
                rowHeightPx = 200f,
                lastIndex = 8,
            ),
        )
    }

    @Test
    fun dragIsClampedToTheGrid() {
        assertEquals(
            0,
            pageReorderTarget(
                index = 0,
                dragX = -500f,
                dragY = -500f,
                columns = 3,
                cellWidthPx = 100f,
                rowHeightPx = 200f,
                lastIndex = 8,
            ),
        )
        assertEquals(
            8,
            pageReorderTarget(
                index = 7,
                dragX = 500f,
                dragY = 500f,
                columns = 3,
                cellWidthPx = 100f,
                rowHeightPx = 200f,
                lastIndex = 8,
            ),
        )
    }

    @Test
    fun invalidDimensionsKeepTheOriginalIndex() {
        assertEquals(
            3,
            pageReorderTarget(
                index = 3,
                dragX = 100f,
                dragY = 100f,
                columns = 3,
                cellWidthPx = 0f,
                rowHeightPx = 200f,
                lastIndex = 8,
            ),
        )
    }

    @Test
    fun movingTheCurrentPageReturnsItsNewIndex() {
        assertEquals(6, pageIndexAfterMove(currentIndex = 2, from = 2, to = 6))
    }

    @Test
    fun movingAPageAboveTheCurrentPageShiftsItUp() {
        assertEquals(2, pageIndexAfterMove(currentIndex = 3, from = 0, to = 5))
    }

    @Test
    fun movingAPageBelowTheCurrentPageShiftsItDown() {
        assertEquals(2, pageIndexAfterMove(currentIndex = 1, from = 5, to = 0))
    }

    @Test
    fun unrelatedMovesKeepTheCurrentPage() {
        assertEquals(4, pageIndexAfterMove(currentIndex = 4, from = 1, to = 2))
    }
}
