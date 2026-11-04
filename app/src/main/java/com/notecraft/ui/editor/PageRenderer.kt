package com.notecraft.ui.editor

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private val PaperColor = Color(0xFFFFFEFB)
private val RuleColor = Color(0xFFB9D4E8)
private val GridColor = Color(0xFFC9D6E2)
private val MarginColor = Color(0xFFE6A5A5)
private val DotColor = Color(0xFF9FB3C4)
private val SelectionColor = Color(0xFF5B7CFA)

fun DrawScope.drawPageContent(
    page: Page,
    scale: Float,
    origin: Offset,
    selectedStrokeIds: Set<String> = emptySet(),
    selectedTextIds: Set<String> = emptySet(),
    drawSelection: Boolean = false,
    drawPaper: Boolean = true,
) {
    val pageWidth = PAGE_WIDTH * scale
    val pageHeight = PAGE_HEIGHT * scale
    if (drawPaper) {
        drawRect(
            color = PaperColor,
            topLeft = origin,
            size = Size(pageWidth, pageHeight),
        )
    }
    clipRect(
        left = origin.x,
        top = origin.y,
        right = origin.x + pageWidth,
        bottom = origin.y + pageHeight,
    ) {
        drawPageBackground(page.background, scale, origin)
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
}

fun DrawScope.drawPageBackground(background: PageBackground, scale: Float, origin: Offset) {
    when (background) {
        PageBackground.BLANK -> Unit

        PageBackground.LINED -> {
            val spacing = 56f * scale
            val marginX = origin.x + 92f * scale
            var y = origin.y + spacing
            while (y < origin.y + PAGE_HEIGHT * scale) {
                drawLine(
                    color = RuleColor.copy(alpha = 0.55f),
                    start = Offset(origin.x + 52f * scale, y),
                    end = Offset(origin.x + PAGE_WIDTH * scale - 40f * scale, y),
                    strokeWidth = 1.2f * scale.coerceAtLeast(0.3f),
                )
                y += spacing
            }
            drawLine(
                color = MarginColor.copy(alpha = 0.65f),
                start = Offset(marginX, origin.y),
                end = Offset(marginX, origin.y + PAGE_HEIGHT * scale),
                strokeWidth = 1.2f * scale.coerceAtLeast(0.3f),
            )
        }

        PageBackground.GRID -> {
            val spacing = 48f * scale
            val lineWidth = 1.1f * scale.coerceAtLeast(0.3f)
            var x = origin.x + spacing
            while (x < origin.x + PAGE_WIDTH * scale) {
                drawLine(
                    color = GridColor.copy(alpha = 0.55f),
                    start = Offset(x, origin.y),
                    end = Offset(x, origin.y + PAGE_HEIGHT * scale),
                    strokeWidth = lineWidth,
                )
                x += spacing
            }
            var y = origin.y + spacing
            while (y < origin.y + PAGE_HEIGHT * scale) {
                drawLine(
                    color = GridColor.copy(alpha = 0.55f),
                    start = Offset(origin.x, y),
                    end = Offset(origin.x + PAGE_WIDTH * scale, y),
                    strokeWidth = lineWidth,
                )
                y += spacing
            }
        }

        PageBackground.DOTTED -> {
            val spacing = 48f * scale
            val radius = 1.6f * scale.coerceAtLeast(0.4f)
            var x = origin.x + spacing
            while (x < origin.x + PAGE_WIDTH * scale) {
                var y = origin.y + spacing
                while (y < origin.y + PAGE_HEIGHT * scale) {
                    drawCircle(
                        color = DotColor.copy(alpha = 0.65f),
                        radius = radius,
                        center = Offset(x, y),
                    )
                    y += spacing
                }
                x += spacing
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

    if (screenPoints.size == 1) {
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = width / 2f,
            center = screenPoints.first(),
        )
        return
    }

    val path = smoothPath(screenPoints)
    when (stroke.tool) {
        ToolType.HIGHLIGHTER -> {
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

        ToolType.PENCIL -> {
            drawPath(
                path = path,
                color = color.copy(alpha = 0.78f * alpha),
                style = DrawStroke(width = width * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawPencilTexture(screenPoints, color, width, stroke.seed, alpha)
        }

        else -> {
            drawPath(
                path = path,
                color = color.copy(alpha = alpha),
                style = DrawStroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
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

fun DrawScope.drawSelectionFrame(bounds: Bounds, scale: Float, origin: Offset) {
    val left = origin.x + bounds.left * scale
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

fun DrawScope.drawPagePaperEdge(origin: Offset, scale: Float) {
    val width = PAGE_WIDTH * scale
    val height = PAGE_HEIGHT * scale
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.14f),
        topLeft = Offset(origin.x + 3f, origin.y + 5f),
        size = Size(width, height),
        cornerRadius = CornerRadius(3f, 3f),
    )
    drawRect(
        color = PaperColor,
        topLeft = origin,
        size = Size(width, height),
    )
}
