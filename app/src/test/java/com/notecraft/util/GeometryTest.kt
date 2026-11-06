package com.notecraft.util

import com.notecraft.model.Pt
import com.notecraft.model.ShapeType
import com.notecraft.model.Stroke
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
}
