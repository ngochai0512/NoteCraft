package com.notecraft.ui.editor

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import com.notecraft.model.PAGE_HEIGHT
import com.notecraft.model.PAGE_WIDTH
import com.notecraft.model.Page
import com.notecraft.model.PageBackground
import com.notecraft.model.ShapeType
import com.notecraft.model.Stroke as InkStroke
import com.notecraft.model.TextItem
import com.notecraft.model.ToolType
import com.notecraft.util.Bounds
import com.notecraft.util.approximateBounds
import com.notecraft.util.selectionBounds
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val PaperColor = Color(0xFFFCFCFA)
private val PaperWarm = Color(0xFFFBF8F1)
private val RuleColor = Color(0xFFE2E8F0)
private val GridColor = Color(0xFFE2E8F0)
private val MarginColor = Color(0xFFEF4444)
private val DotColor = Color(0xFF94A3B8)
private val SelectionColor = Color(0xFF2563EB)

fun DrawScope.drawPageContent(
    page: Page,
    scale: Float,
    origin: Offset,
    selectedStrokeIds: Set<String> = emptySet(),
    selectedTextIds: Set<String> = emptySet(),
    drawSelection: Boolean = false,
    drawPaper: Boolean = true,
    drawBackground: Boolean = true,
    clip: Boolean = true,
) {
    val pageWidth = PAGE_WIDTH * scale
    val pageHeight = PAGE_HEIGHT * scale
    if (drawPaper) {
        drawRect(
            brush = Brush.verticalGradient(listOf(PaperColor, PaperWarm)),
            topLeft = origin,
            size = Size(pageWidth, pageHeight),
        )
    }
    val content: DrawScope.() -> Unit = {
        if (drawBackground) drawPageBackground(page.background, scale, origin)
        page.strokes.forEach { stroke ->
            val selected = stroke.id in selectedStrokeIds
            if (selected) {
                drawStrokeBody(stroke, scale, origin, overrideColor = SelectionColor, alpha = 0.22f, extraWidth = 10f)
            }
            drawStrokeBody(stroke, scale, origin)
        }
        page.texts.forEach { item ->
            drawTextItem(item, scale, origin)
        }
        if (drawSelection && (selectedStrokeIds.isNotEmpty() || selectedTextIds.isNotEmpty())) {
            val bounds = selectionBounds(page, selectedStrokeIds, selectedTextIds)
            if (bounds != null) {
                drawSelectionFrame(bounds, scale, origin)
            }
        }
    }
    if (clip) {
        clipRect(
            left = origin.x,
            top = origin.y,
            right = origin.x + pageWidth,
            bottom = origin.y + pageHeight,
            block = content,
        )
    } else {
        content()
    }
}

/**
 * Fills the whole visible editor area with the paper surface. Used by the editor so the paper is
 * edge-to-edge instead of a sheet resting on a desk.
 */
fun DrawScope.drawPaperSurface() {
    drawRect(brush = Brush.verticalGradient(listOf(PaperColor, PaperWarm)))
}

/**
 * Draws the page pattern over the supplied screen-space bounds. The pattern stays anchored to the
 * page [origin], so it pans and zooms with the page while filling the entire viewport.
 *
 * Callers that render a page preview pass the page bounds explicitly; the editor passes the full
 * canvas bounds so the paper appears continuous.
 */
fun DrawScope.drawPageBackground(
    background: PageBackground,
    scale: Float,
    origin: Offset,
    left: Float = origin.x,
    top: Float = origin.y,
    right: Float = origin.x + PAGE_WIDTH * scale,
    bottom: Float = origin.y + PAGE_HEIGHT * scale,
) {
    if (scale <= 0f || right <= left || bottom <= top) return
    when (background) {
        PageBackground.BLANK -> Unit

        PageBackground.LINED -> {
            // 8 mm ruled paper.
            val spacing = 38f * scale
            val lineWidth = 1.1f * scale.coerceAtLeast(0.3f)
            var y = origin.y + spacing * ceil((top - origin.y) / spacing)
            while (y <= bottom) {
                drawLine(
                    color = RuleColor.copy(alpha = 0.65f),
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = lineWidth,
                )
                y += spacing
            }
            val marginX = origin.x + 90f * scale
            if (marginX in left..right) {
                drawLine(
                    color = MarginColor.copy(alpha = 0.30f),
                    start = Offset(marginX, top),
                    end = Offset(marginX, bottom),
                    strokeWidth = lineWidth,
                )
            }
        }

        PageBackground.GRID -> {
            // 5 mm squared graph paper.
            val spacing = 24f * scale
            val lineWidth = 1f * scale.coerceAtLeast(0.3f)
            var x = origin.x + spacing * ceil((left - origin.x) / spacing)
            while (x <= right) {
                drawLine(
                    color = GridColor.copy(alpha = 0.55f),
                    start = Offset(x, top),
                    end = Offset(x, bottom),
                    strokeWidth = lineWidth,
                )
                x += spacing
            }
            var y = origin.y + spacing * ceil((top - origin.y) / spacing)
            while (y <= bottom) {
                drawLine(
                    color = GridColor.copy(alpha = 0.55f),
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = lineWidth,
                )
                y += spacing
            }
        }

        PageBackground.DOTTED -> {
            // 5 mm dotted grid.
            val spacing = 24f * scale
            val radius = 1.35f * scale.coerceAtLeast(0.35f)
            var x = origin.x + spacing * ceil((left - origin.x) / spacing)
            while (x <= right) {
                var y = origin.y + spacing * ceil((top - origin.y) / spacing)
                while (y <= bottom) {
                    drawCircle(
                        color = DotColor.copy(alpha = 0.45f),
                        radius = radius,
                        center = Offset(x, y),
                    )
                    y += spacing
                }
                x += spacing
            }
        }

        PageBackground.CORNELL -> {
            // Cornell layout: title band, cue column, note area, summary band.
            val spacing = 38f * scale
            val lineWidth = 1.1f * scale.coerceAtLeast(0.3f)
            val topLineY = origin.y + 150f * scale
            val summaryY = origin.y + 1120f * scale
            val cueX = origin.x + 280f * scale

            var y = topLineY + spacing
            while (y < summaryY) {
                drawLine(
                    color = RuleColor.copy(alpha = 0.45f),
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = lineWidth,
                )
                y += spacing
            }

            listOf(topLineY, summaryY).forEach { lineY ->
                drawLine(
                    color = GridColor.copy(alpha = 0.85f),
                    start = Offset(left, lineY),
                    end = Offset(right, lineY),
                    strokeWidth = lineWidth * 1.2f,
                )
            }
            if (cueX in left..right) {
                drawLine(
                    color = MarginColor.copy(alpha = 0.45f),
                    start = Offset(cueX, topLineY),
                    end = Offset(cueX, summaryY),
                    strokeWidth = lineWidth * 1.2f,
                )
            }
        }
    }
}

fun DrawScope.drawStrokeBody(
    stroke: InkStroke,
    scale: Float,
    origin: Offset,
    overrideColor: Color? = null,
    alpha: Float = 1f,
    extraWidth: Float = 0f,
) {
    val color = overrideColor ?: Color(stroke.color.toInt())
    val width = (stroke.width + extraWidth) * scale
    if (width <= 0f) return
    val screenPoints = stroke.points.map {
        Offset(origin.x + it.x * scale, origin.y + it.y * scale)
    }

    if (stroke.tool == ToolType.SHAPE) {
        drawShapeStroke(stroke, screenPoints, color.copy(alpha = alpha), width)
        return
    }
    if (stroke.tool == ToolType.ERASER || stroke.tool == ToolType.LASSO) return
    if (stroke.tool == ToolType.TEXT) return

    val pressures = stroke.points.map { it.pressure }

    when (stroke.tool) {
        ToolType.HIGHLIGHTER -> {
            if (screenPoints.size == 1) {
                drawCircle(color = color.copy(alpha = 0.30f * alpha), radius = width / 2f, center = screenPoints.first())
                return
            }
            val path = smoothPath(screenPoints)
            val highlight = color.copy(alpha = 0.30f * alpha)
            drawPath(
                path = path,
                color = highlight,
                style = DrawStroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = BlendMode.Multiply,
            )
            drawPath(
                path = path,
                color = highlight.copy(alpha = 0.10f * alpha),
                style = DrawStroke(width = width * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = BlendMode.Multiply,
            )
        }

        ToolType.TAPE -> drawTapeBand(screenPoints, color, width, alpha)

        // Fountain nib: thin on light pressure, opening up as the pen is pressed.
        ToolType.FOUNTAIN_PEN -> drawPressureStroke(
            points = screenPoints,
            pressures = pressures,
            color = color.copy(alpha = alpha),
            baseWidth = width,
            minFactor = 0.45f,
            maxFactor = 1.35f,
        )

        // Brush pen: soft, wide, and much more responsive to pressure than a fountain pen.
        ToolType.BRUSH_PEN -> drawPressureStroke(
            points = screenPoints,
            pressures = pressures,
            color = color.copy(alpha = alpha),
            baseWidth = width,
            minFactor = 0.35f,
            maxFactor = 1.85f,
        )

        ToolType.PENCIL -> {
            if (screenPoints.size == 1) {
                drawCircle(color = color.copy(alpha = 0.78f * alpha), radius = width / 2f, center = screenPoints.first())
                return
            }
            val path = smoothPath(screenPoints)
            drawPath(
                path = path,
                color = color.copy(alpha = 0.78f * alpha),
                style = DrawStroke(width = width * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawPencilTexture(screenPoints, color, width, stroke.seed, alpha)
        }

        else -> {
            if (screenPoints.size == 1) {
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = width / 2f,
                    center = screenPoints.first(),
                )
                return
            }
            drawPath(
                path = smoothPath(screenPoints),
                color = color.copy(alpha = alpha),
                style = DrawStroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/**
 * Draws segment by segment so each part of the line can take the width its pressure asks for.
 * Round caps on every segment keep the joins smooth.
 */
private fun DrawScope.drawPressureStroke(
    points: List<Offset>,
    pressures: List<Float>,
    color: Color,
    baseWidth: Float,
    minFactor: Float,
    maxFactor: Float,
) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        drawCircle(color = color, radius = baseWidth * 0.55f, center = points.first())
        return
    }
    for (index in 1 until points.size) {
        val pressure = pressures.getOrElse(index) { 0.5f }.coerceIn(0.15f, 1f)
        val factor = minFactor + (maxFactor - minFactor) * pressure
        drawLine(
            color = color,
            start = points[index - 1],
            end = points[index],
            strokeWidth = (baseWidth * factor).coerceAtLeast(0.6f),
            cap = StrokeCap.Round,
        )
    }
}

/** Tape is a translucent band laid over the page, like a strip of washi tape. */
private fun DrawScope.drawTapeBand(
    points: List<Offset>,
    color: Color,
    width: Float,
    alpha: Float,
) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        drawCircle(color = color.copy(alpha = 0.42f * alpha), radius = width / 2f, center = points.first())
        return
    }
    val path = smoothPath(points)
    drawPath(
        path = path,
        color = color.copy(alpha = 0.14f * alpha),
        style = DrawStroke(width = width * 1.08f, cap = StrokeCap.Square, join = StrokeJoin.Round),
    )
    drawPath(
        path = path,
        color = color.copy(alpha = 0.42f * alpha),
        style = DrawStroke(width = width, cap = StrokeCap.Square, join = StrokeJoin.Round),
    )
}

private fun DrawScope.drawPencilTexture(
    points: List<Offset>,
    color: Color,
    width: Float,
    seed: Int,
    alpha: Float,
) {
    val random = Random(seed)
    val step = if (points.size > 220) 3 else 2
    var index = 0
    while (index < points.size) {
        val point = points[index]
        val jitterX = (random.nextFloat() - 0.5f) * width * 0.7f
        val jitterY = (random.nextFloat() - 0.5f) * width * 0.7f
        drawCircle(
            color = color.copy(alpha = (0.05f + random.nextFloat() * 0.09f) * alpha),
            radius = width * (0.10f + random.nextFloat() * 0.18f),
            center = Offset(point.x + jitterX, point.y + jitterY),
        )
        index += step
    }
}

private fun DrawScope.drawShapeStroke(
    stroke: InkStroke,
    points: List<Offset>,
    color: Color,
    width: Float,
) {
    if (points.size < 2) {
        if (points.isNotEmpty()) {
            drawCircle(color = color, radius = width / 2f, center = points.first())
        }
        return
    }
    val start = points.first()
    val end = points.last()
    val style = DrawStroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (stroke.shapeType) {
        ShapeType.LINE, null -> drawLine(color = color, start = start, end = end, strokeWidth = width, cap = StrokeCap.Round)

        ShapeType.ARROW -> {
            drawLine(color = color, start = start, end = end, strokeWidth = width, cap = StrokeCap.Round)
            val angle = atan2(end.y - start.y, end.x - start.x)
            val length = hypot(end.x - start.x, end.y - start.y)
            val head = min(max(width * 4.5f, 8f), length * 0.4f)
            val firstAngle = angle - 0.55f
            val secondAngle = angle + 0.55f
            drawLine(
                color = color,
                start = end,
                end = Offset(end.x - head * cos(firstAngle), end.y - head * sin(firstAngle)),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = end,
                end = Offset(end.x - head * cos(secondAngle), end.y - head * sin(secondAngle)),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }

        ShapeType.RECTANGLE -> drawRect(
            color = color,
            topLeft = Offset(min(start.x, end.x), min(start.y, end.y)),
            size = Size(abs(end.x - start.x), abs(end.y - start.y)),
            style = style,
        )

        ShapeType.ELLIPSE -> drawOval(
            color = color,
            topLeft = Offset(min(start.x, end.x), min(start.y, end.y)),
            size = Size(abs(end.x - start.x), abs(end.y - start.y)),
            style = style,
        )
    }
}

private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    if (points.size == 1) return path
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }
    for (index in 1 until points.size - 1) {
        val current = points[index]
        val next = points[index + 1]
        path.quadraticTo(
            current.x,
            current.y,
            (current.x + next.x) / 2f,
            (current.y + next.y) / 2f,
        )
    }
    path.lineTo(points.last().x, points.last().y)
    return path
}

fun DrawScope.drawTextItem(item: TextItem, scale: Float, origin: Offset) {
    if (item.text.isBlank()) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = item.color.toInt()
        textSize = item.fontSize * scale
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    val lines = item.text.split('\n')
    val lineHeight = item.fontSize * 1.28f * scale
    val baselineOffset = item.fontSize * 1.02f * scale
    val canvas = drawContext.canvas.nativeCanvas
    lines.forEachIndexed { index, line ->
        canvas.drawText(
            line,
            origin.x + item.x * scale,
            origin.y + item.y * scale + baselineOffset + index * lineHeight,
            paint,
        )
    }
}

fun DrawScope.drawSelectionFrame(bounds: Bounds, scale: Float, origin: Offset) {    val left = origin.x + bounds.left * scale
    val top = origin.y + bounds.top * scale
    val width = bounds.width * scale
    val height = bounds.height * scale
    val padding = 8f * scale.coerceAtLeast(0.5f)
    val effect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
    drawRoundRect(
        color = SelectionColor,
        topLeft = Offset(left - padding, top - padding),
        size = Size(width + padding * 2, height + padding * 2),
        cornerRadius = CornerRadius(6f, 6f),
        style = DrawStroke(width = 2.2f, pathEffect = effect),
    )
    val handleRadius = 6f
    listOf(
        Offset(left - padding, top - padding),
        Offset(left + width + padding, top - padding),
        Offset(left - padding, top + height + padding),
        Offset(left + width + padding, top + height + padding),
    ).forEach { point ->
        drawCircle(color = Color.White, radius = handleRadius, center = point)
        drawCircle(
            color = SelectionColor,
            radius = handleRadius,
            center = point,
            style = DrawStroke(width = 2f),
        )
    }
}
