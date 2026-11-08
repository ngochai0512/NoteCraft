package com.notecraft.util

import com.notecraft.model.Page
import com.notecraft.model.Pt
import com.notecraft.model.ShapeType
import com.notecraft.model.StickyNote
import com.notecraft.model.Stroke
import com.notecraft.model.TextItem
import com.notecraft.model.ToolType
import com.notecraft.model.newId
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class Bounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val center: Pt get() = Pt(centerX, centerY)

    fun expand(amount: Float): Bounds = Bounds(
        left - amount,
        top - amount,
        right + amount,
        bottom + amount,
    )

    fun contains(x: Float, y: Float): Boolean =
        x in left..right && y in top..bottom

    fun intersects(other: Bounds): Boolean =
        left <= other.right && right >= other.left && top <= other.bottom && bottom >= other.top

    companion object {
        fun fromPoints(points: List<Pt>): Bounds? {
            if (points.isEmpty()) return null
            var left = Float.POSITIVE_INFINITY
            var top = Float.POSITIVE_INFINITY
            var right = Float.NEGATIVE_INFINITY
            var bottom = Float.NEGATIVE_INFINITY
            points.forEach { point ->
                left = min(left, point.x)
                top = min(top, point.y)
                right = max(right, point.x)
                bottom = max(bottom, point.y)
            }
            return Bounds(left, top, right, bottom)
        }

        fun combine(a: Bounds?, b: Bounds?): Bounds? = when {
            a == null -> b
            b == null -> a
            else -> Bounds(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom))
        }
    }
}

fun distancePointToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared <= 0.0001f) return hypot(px - ax, py - ay)
    var t = ((px - ax) * dx + (py - ay) * dy) / lengthSquared
    t = t.coerceIn(0f, 1f)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}

fun Stroke.bounds(): Bounds? {
    if (points.isEmpty()) return null
    if (tool == ToolType.SHAPE && shapeType != null && points.size >= 2) {
        val start = points.first()
        val end = points.last()
        if (shapeType == ShapeType.RECTANGLE || shapeType == ShapeType.ELLIPSE) {
            return Bounds(
                min(start.x, end.x),
                min(start.y, end.y),
                max(start.x, end.x),
                max(start.y, end.y),
            )
        }
    }
    return Bounds.fromPoints(points)
}

/** True when [x], [y] is close enough to this stroke to be erased. */
fun Stroke.hitTest(x: Float, y: Float, extraRadius: Float): Boolean {
    val threshold = width / 2f + extraRadius
    if (tool == ToolType.SHAPE && shapeType != null) {
        return shapeHitTest(x, y, threshold)
    }
    if (points.size == 1) {
        val point = points.first()
        return hypot(x - point.x, y - point.y) <= threshold
    }
    points.forEachIndexed { index, point ->
        if (index == 0) {
            if (hypot(x - point.x, y - point.y) <= threshold) return true
        } else {
            val previous = points[index - 1]
            if (distancePointToSegment(x, y, previous.x, previous.y, point.x, point.y) <= threshold) {
                return true
            }
        }
    }
    return false
}

/**
 * Removes the portion of this stroke inside the eraser circle and returns the remaining pieces.
 * Shapes and single-point strokes disappear as a whole. The first remaining piece keeps the
 * original id so selection and history stay stable.
 */
fun Stroke.partialErase(x: Float, y: Float, extraRadius: Float): List<Stroke> {
    if (tool == ToolType.SHAPE) {
        return if (hitTest(x, y, extraRadius)) emptyList() else listOf(this)
    }
    val threshold = width / 2f + extraRadius
    if (points.size == 1) {
        val point = points.first()
        return if (hypot(x - point.x, y - point.y) <= threshold) emptyList() else listOf(this)
    }

    val erasedSegments = BooleanArray(points.size - 1)
    var anyErased = false
    for (index in 0 until points.size - 1) {
        val start = points[index]
        val end = points[index + 1]
        val hit = distancePointToSegment(x, y, start.x, start.y, end.x, end.y) <= threshold
        erasedSegments[index] = hit
        if (hit) anyErased = true
    }
    if (!anyErased) return listOf(this)

    val runs = mutableListOf<MutableList<Pt>>()
    var run = mutableListOf(points.first())
    for (index in erasedSegments.indices) {
        val next = points[index + 1]
        if (erasedSegments[index]) {
            if (run.size >= 2) runs.add(run)
            run = mutableListOf(next)
        } else {
            run.add(next)
        }
    }
    if (run.size >= 2) runs.add(run)
    if (runs.isEmpty()) return emptyList()

    return runs.mapIndexed { index, runPoints ->
        copy(
            id = if (index == 0) id else newId(),
            points = runPoints.toList(),
        )
    }
}

private fun Stroke.shapeHitTest(x: Float, y: Float, threshold: Float): Boolean {
    val start = points.firstOrNull() ?: return false
    val end = points.lastOrNull() ?: start
    if (points.size < 2) return hypot(x - start.x, y - start.y) <= threshold
    return when (shapeType) {
        ShapeType.LINE, ShapeType.ARROW ->
            distancePointToSegment(x, y, start.x, start.y, end.x, end.y) <= threshold

        ShapeType.RECTANGLE -> {
            val left = min(start.x, end.x)
            val top = min(start.y, end.y)
            val right = max(start.x, end.x)
            val bottom = max(start.y, end.y)
            val insideHorizontal = x in left..right &&
                (abs(y - top) <= threshold || abs(y - bottom) <= threshold)
            val insideVertical = y in top..bottom &&
                (abs(x - left) <= threshold || abs(x - right) <= threshold)
            insideHorizontal || insideVertical
        }

        ShapeType.ELLIPSE -> {
            val left = min(start.x, end.x)
            val top = min(start.y, end.y)
            val right = max(start.x, end.x)
            val bottom = max(start.y, end.y)
            val radiusX = max((right - left) / 2f, 0.001f)
            val radiusY = max((bottom - top) / 2f, 0.001f)
            val centerX = (left + right) / 2f
            val centerY = (top + bottom) / 2f
            val normalized = hypot((x - centerX) / radiusX, (y - centerY) / radiusY)
            val tolerance = threshold / min(radiusX, radiusY)
            abs(normalized - 1f) <= tolerance
        }

        null -> false
    }
}

fun Stroke.intersectsPolygon(polygon: List<Pt>): Boolean {
    if (polygon.size < 3) return false
    if (points.any { polygonContains(polygon, it.x, it.y) }) return true
    // Check segment midpoints too so a lasso between densely spaced points still selects.
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        if (polygonContains(polygon, (a.x + b.x) / 2f, (a.y + b.y) / 2f)) return true
    }
    return false
}

fun polygonContains(polygon: List<Pt>, x: Float, y: Float): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val pi = polygon[i]
        val pj = polygon[j]
        val intersects = ((pi.y > y) != (pj.y > y)) &&
            (x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y + 0.000001f) + pi.x)
        if (intersects) inside = !inside
        j = i
    }
    return inside
}

fun TextItem.approximateBounds(): Bounds {
    val lines = estimatedTextLines()
    val maxChars = lines.maxOfOrNull { it.length } ?: 0
    val boxWidth = if (width > 0f) width else maxChars * fontSize * 0.56f
    val lineHeight = fontSize * 1.28f * formatting.lineSpacing.coerceIn(0.8f, 2f)
    val height = max(lines.size, 1) * lineHeight
    return Bounds(x, y, x + boxWidth, y + height)
}

/**
 * Approximate the visible lines of a text box without using Android text APIs. Wrapping uses an
 * average glyph width, which is accurate enough for hit testing and selection bounds.
 */
fun TextItem.estimatedTextLines(): List<String> {
    val rendered = displayText
    val explicitLines = rendered.split('\n')
    if (width <= 0f) return explicitLines
    val maxChars = (width / (fontSize * 0.52f)).toInt().coerceAtLeast(1)
    return explicitLines.flatMap { line -> wrapLine(line, maxChars) }
}

private fun wrapLine(line: String, maxChars: Int): List<String> {
    if (line.isEmpty()) return listOf("")
    if (line.length <= maxChars) return listOf(line)
    val result = mutableListOf<String>()
    val current = StringBuilder()
    line.split(' ').forEach { word ->
        if (word.length > maxChars) {
            if (current.isNotEmpty()) {
                result.add(current.toString())
                current.clear()
            }
            var start = 0
            while (start < word.length) {
                val end = min(start + maxChars, word.length)
                result.add(word.substring(start, end))
                start = end
            }
        } else {
            val candidateLength = if (current.isEmpty()) word.length else current.length + 1 + word.length
            if (candidateLength > maxChars) {
                if (current.isNotEmpty()) result.add(current.toString())
                current.clear()
                current.append(word)
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(word)
            }
        }
    }
    if (current.isNotEmpty()) result.add(current.toString())
    return result.ifEmpty { listOf("") }
}

/** The resize handle sits at the bottom-right corner of the text box. */
fun TextItem.resizeHandle(radius: Float = 22f): Bounds {
    val bounds = approximateBounds()
    return Bounds(
        left = bounds.right - radius,
        top = bounds.bottom - radius,
        right = bounds.right + radius,
        bottom = bounds.bottom + radius,
    )
}

fun TextItem.hitTest(x: Float, y: Float, extraRadius: Float): Boolean =
    approximateBounds().expand(extraRadius).contains(x, y)

fun TextItem.hitHandle(x: Float, y: Float, tolerance: Float): Boolean =
    resizeHandle(tolerance).contains(x, y)

fun StickyNote.bounds(): Bounds = Bounds(x, y, x + width, y + height)

fun StickyNote.hitTest(x: Float, y: Float, extraRadius: Float = 0f): Boolean =
    bounds().expand(extraRadius).contains(x, y)

fun selectionBounds(
    page: Page,
    strokeIds: Set<String>,
    textIds: Set<String>,
): Bounds? {
    var bounds: Bounds? = null
    page.strokes.forEach { stroke ->
        if (stroke.id in strokeIds) bounds = Bounds.combine(bounds, stroke.bounds())
    }
    page.texts.forEach { text ->
        if (text.id in textIds) bounds = Bounds.combine(bounds, text.approximateBounds())
    }
    return bounds
}

fun selectInsidePolygon(page: Page, polygon: List<Pt>): Pair<Set<String>, Set<String>> {
    if (polygon.size < 3) return emptySet<String>() to emptySet()
    val strokeIds = page.strokes.filter { it.intersectsPolygon(polygon) }.map { it.id }.toSet()
    val textIds = page.texts.filter { polygonContains(polygon, it.approximateBounds().centerX, it.approximateBounds().centerY) }
        .map { it.id }
        .toSet()
    return strokeIds to textIds
}

fun arrowHeadPoints(start: Pt, end: Pt, size: Float = 20f): Pair<Pt, Pt> {
    val angle = atan2(end.y - start.y, end.x - start.x)
    val length = hypot(end.x - start.x, end.y - start.y)
    val head = min(size, max(length * 0.25f, 4f))
    val first = Pt(
        x = end.x - head * cos(angle - 0.5f),
        y = end.y - head * sin(angle - 0.5f),
    )
    val second = Pt(
        x = end.x - head * cos(angle + 0.5f),
        y = end.y - head * sin(angle + 0.5f),
    )
    return first to second
}
