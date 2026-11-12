package com.notecraft.util

import com.notecraft.model.Pt
import com.notecraft.model.ShapeType
import com.notecraft.model.StickyNote
import com.notecraft.model.Stroke
import com.notecraft.model.TextFormatting
import com.notecraft.model.TextItem
import com.notecraft.model.TextListStyle
import com.notecraft.model.ToolType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {

    private fun horizontalStroke(id: String = "stroke"): Stroke = Stroke(
        id = id,
        tool = ToolType.BALL_PEN,
        color = 0xFF000000,
        width = 2f,
        points = (0..20).map { index -> Pt(index * 10f, 100f) },
    )

    @Test
    fun erasingTheMiddleSplitsAStroke() {
        val stroke = horizontalStroke()
        val pieces = stroke.partialErase(x = 100f, y = 100f, extraRadius = 5f)

        assertEquals(2, pieces.size)
        assertEquals(stroke.id, pieces[0].id)
        assertNotEquals(stroke.id, pieces[1].id)
        assertTrue(pieces[0].points.all { it.x < 100f })
        assertTrue(pieces[1].points.all { it.x > 100f })
        assertTrue(pieces.all { it.points.size >= 2 })
    }

    @Test
    fun erasingEverythingRemovesTheStroke() {
        val stroke = horizontalStroke()
        assertTrue(stroke.partialErase(x = 100f, y = 100f, extraRadius = 500f).isEmpty())
    }

    @Test
    fun erasingFarAwayKeepsTheSameStroke() {
        val stroke = horizontalStroke()
        val pieces = stroke.partialErase(x = 1000f, y = 1000f, extraRadius = 5f)

        assertEquals(1, pieces.size)
        assertSame(stroke, pieces.first())
    }

    @Test
    fun singlePointAndShapeStrokesAreRemovedWhole() {
        val dot = Stroke(
            id = "dot",
            tool = ToolType.PENCIL,
            color = 0xFF000000,
            width = 4f,
            points = listOf(Pt(10f, 10f)),
        )
        assertTrue(dot.partialErase(10f, 10f, extraRadius = 2f).isEmpty())

        val shape = Stroke(
            id = "shape",
            tool = ToolType.SHAPE,
            color = 0xFF000000,
            width = 4f,
            points = listOf(Pt(0f, 100f), Pt(100f, 100f)),
            shapeType = ShapeType.LINE,
        )
        assertTrue(shape.partialErase(50f, 100f, extraRadius = 5f).isEmpty())
    }

    @Test
    fun textBoxWrapsInsideItsWidthAndReportsResizeHandle() {
        val text = TextItem(
            id = "text",
            x = 100f,
            y = 200f,
            text = "A long sentence that should wrap onto several lines inside the fixed text box width.",
            color = 0xFF000000,
            fontSize = 30f,
            width = 240f,
        )

        val bounds = text.approximateBounds()
        assertEquals(240f, bounds.width, 0.001f)
        assertTrue(text.estimatedTextLines().size > 1)

        val handleCenterX = bounds.right
        val handleCenterY = bounds.bottom
        assertTrue(text.hitHandle(handleCenterX, handleCenterY, tolerance = 20f))
        assertTrue(!text.hitHandle(bounds.left, bounds.top, tolerance = 4f))
    }

    @Test
    fun legacyTextWithoutWidthKeepsExplicitLines() {
        val text = TextItem(
            id = "legacy",
            x = 0f,
            y = 0f,
            text = "one\ntwo",
            color = 0xFF000000,
        )

        assertEquals(2f, text.approximateBounds().height / (text.fontSize * 1.28f), 0.001f)
        assertEquals(listOf("one", "two"), text.estimatedTextLines())
    }

    @Test
    fun storedTextHeightOverridesTheEstimate() {
        val estimated = TextItem(
            id = "estimated",
            x = 0f,
            y = 0f,
            text = "one\ntwo",
            color = 0xFF000000,
            width = 300f,
        )
        val measured = estimated.copy(height = 999f)

        assertEquals(999f, measured.approximateBounds().height, 0.001f)
        assertTrue(measured.approximateBounds().height > estimated.approximateBounds().height)
    }

    @Test
    fun numberedListsDecorateOnlyNonBlankLines() {
        val text = TextItem(
            id = "numbered",
            x = 0f,
            y = 0f,
            text = "one\n\ntwo",
            color = 0xFF000000,
            width = 300f,
            formatting = TextFormatting(listStyle = TextListStyle.NUMBERED),
        )

        assertEquals("1.  one\n\n2.  two", text.displayText)
    }

    @Test
    fun lineSpacingIncreasesTextHeight() {
        val base = TextItem(
            id = "base",
            x = 0f,
            y = 0f,
            text = "one\ntwo",
            color = 0xFF000000,
            width = 300f,
        )
        val spaced = base.copy(formatting = TextFormatting(lineSpacing = 2f))

        assertTrue(spaced.approximateBounds().height > base.approximateBounds().height)
        assertEquals(2f, spaced.approximateBounds().height / base.approximateBounds().height, 0.001f)
    }

    @Test
    fun stickyNoteGrowsWhenTextOverflows() {
        val note = StickyNote(
            id = "sticky-long",
            x = 0f,
            y = 0f,
            width = 200f,
            height = 120f,
            text = "This sticky note text is long enough to wrap over several lines and force the card to grow.",
        )

        assertTrue(note.effectiveHeight() > note.height)
        assertTrue(note.bounds().height > note.height)
    }

    @Test
    fun stickyNoteKeepsItsHeightForShortText() {
        val note = StickyNote(
            id = "sticky-short",
            x = 0f,
            y = 0f,
            width = 280f,
            height = 202f,
            text = "Short",
        )

        assertEquals(202f, note.effectiveHeight(), 0.001f)
    }

    @Test
    fun stickyNoteHitTestUsesItsBox() {
        val note = StickyNote(
            id = "sticky",
            x = 100f,
            y = 200f,
            width = 280f,
            height = 200f,
            text = "Remember this",
        )

        assertTrue(note.hitTest(150f, 250f))
        assertTrue(!note.hitTest(500f, 500f))
    }
}
