package com.notecraft.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {

    private fun stroke(id: String = "stroke") = Stroke(
        id = id,
        tool = ToolType.BALL_PEN,
        color = InkBlack,
        width = 3f,
        points = listOf(Pt(1f, 2f), Pt(3f, 4f)),
    )

    private fun text(id: String = "text") = TextItem(
        id = id,
        x = 10f,
        y = 20f,
        text = "hello",
        color = InkBlack,
        width = 200f,
    )

    private fun sticky(id: String = "sticky") = StickyNote(
        id = id,
        x = 30f,
        y = 40f,
        text = "note",
    )

    @Test
    fun newNoteStartsUnpinnedWithOnePage() {
        val note = newNote()

        assertFalse(note.pinned)
        assertEquals(1, note.pageCount)
        assertTrue(note.pages.single().isEmpty)
    }

    @Test
    fun duplicateNoteKeepsPinnedAndCoverButRegeneratesIds() {
        val original = newNote().copy(
            title = "Lecture",
            coverTone = "Navy Leather",
            pinned = true,
            pages = listOf(
                blankPage(PageBackground.GRID).copy(
                    bookmarked = true,
                    strokes = listOf(stroke()),
                    texts = listOf(text()),
                    stickies = listOf(sticky()),
                ),
            ),
        )

        val copy = original.duplicate()

        assertNotEquals(original.id, copy.id)
        assertNotEquals(original.pages.single().id, copy.pages.single().id)
        assertEquals("Lecture copy", copy.title)
        assertEquals("Navy Leather", copy.coverTone)
        assertTrue(copy.pinned)

        val originalPage = original.pages.single()
        val copiedPage = copy.pages.single()
        assertTrue(copiedPage.bookmarked)
        assertEquals(PageBackground.GRID, copiedPage.background)
        assertNotEquals(originalPage.strokes.single().id, copiedPage.strokes.single().id)
        assertNotEquals(originalPage.texts.single().id, copiedPage.texts.single().id)
        assertNotEquals(originalPage.stickies.single().id, copiedPage.stickies.single().id)
    }

    @Test
    fun numberedListsSkipBlankLinesAndKeepNumbering() {
        val formatting = TextFormatting(listStyle = TextListStyle.NUMBERED)

        assertEquals(
            "1.  first\n\n2.  second",
            formatting.decorate("first\n\nsecond"),
        )
    }

    @Test
    fun bulletListsDoNotNumberLines() {
        val formatting = TextFormatting(listStyle = TextListStyle.BULLET)

        assertEquals(
            "•  first\n•  second",
            formatting.decorate("first\nsecond"),
        )
    }

    @Test
    fun plainFormattingLeavesTextUnchanged() {
        val raw = "first\nsecond"

        assertEquals(raw, TextFormatting().decorate(raw))
    }
}
