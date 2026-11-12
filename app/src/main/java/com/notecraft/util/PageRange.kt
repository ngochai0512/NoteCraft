package com.notecraft.util

/** Validates a 1-based inclusive page range against a note's page count. */
fun validPageRange(start: Int, end: Int, pageCount: Int): IntRange? {
    if (pageCount <= 0 || start < 1 || end < start || end > pageCount) return null
    return start..end
}
