package com.notecraft.util

import kotlin.math.roundToInt

/**
 * Maps a drag offset in pixels to a target page index for the page-drawer grid.
 * Kept pure so it can be unit tested without Compose.
 */
fun pageReorderTarget(
    index: Int,
    dragX: Float,
    dragY: Float,
    columns: Int,
    cellWidthPx: Float,
    rowHeightPx: Float,
    lastIndex: Int,
): Int {
    if (cellWidthPx <= 0f || rowHeightPx <= 0f || columns <= 0 || lastIndex < 0) return index
    val startColumn = index % columns
    val startRow = index / columns
    val column = (startColumn + (dragX / cellWidthPx).roundToInt())
        .coerceIn(0, columns - 1)
    val row = (startRow + (dragY / rowHeightPx).roundToInt()).coerceAtLeast(0)
    return (row * columns + column).coerceIn(0, lastIndex)
}

/** Index of the currently selected page after a page is moved from [from] to [to]. */
fun pageIndexAfterMove(currentIndex: Int, from: Int, to: Int): Int = when {
    currentIndex == from -> to
    from < currentIndex && to >= currentIndex -> currentIndex - 1
    from > currentIndex && to <= currentIndex -> currentIndex + 1
    else -> currentIndex
}
