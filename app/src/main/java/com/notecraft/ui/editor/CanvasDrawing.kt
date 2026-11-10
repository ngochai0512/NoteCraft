package com.notecraft.ui.editor

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.ink.brush.Brush as InkApiBrush
import androidx.ink.brush.SelfOverlap
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.Stroke as InkApiStroke
import com.notecraft.model.Pt
import com.notecraft.model.Stroke as InkStroke
import com.notecraft.model.ToolType
import com.notecraft.model.newId
import kotlin.math.hypot
import kotlin.math.roundToInt

internal data class CanvasTransform(val scale: Float, val origin: Offset)

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRulerGuide(
    pivot: Offset,
    angleDegrees: Float,
    color: Color,
) {
    val bandHeight = 34.dp.toPx()
    val halfLength = hypot(size.width, size.height)
    rotate(degrees = angleDegrees, pivot = pivot) {
        val left = pivot.x - halfLength
        drawRoundRect(
            color = Color.White.copy(alpha = 0.88f),
            topLeft = Offset(left, pivot.y),
            size = Size(halfLength * 2f, bandHeight),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
        )
        drawLine(
            color = Color(0xFFCBD5E1),
            start = Offset(left, pivot.y + bandHeight),
            end = Offset(left + halfLength * 2f, pivot.y + bandHeight),
            strokeWidth = 1f,
        )
        var x = left
        val step = 26.dp.toPx()
        var index = 0
        while (x <= left + halfLength * 2f) {
            val tick = if (index % 4 == 0) 16.dp.toPx() else 8.dp.toPx()
            drawLine(
                color = color.copy(alpha = 0.55f),
                start = Offset(x, pivot.y),
                end = Offset(x, pivot.y + tick),
                strokeWidth = 1f,
            )
            x += step
            index++
        }
        drawLine(
            color = color,
            start = Offset(left, pivot.y),
            end = Offset(left + halfLength * 2f, pivot.y),
            strokeWidth = 2.dp.toPx(),
        )
    }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLaserTrail(
    points: List<Pt>,
    transform: CanvasTransform,
) {
    if (points.size < 2) return
    val path = Path()
    points.forEachIndexed { index, point ->
        val screen = Offset(transform.origin.x + point.x * transform.scale, transform.origin.y + point.y * transform.scale)
        if (index == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
    }
    drawPath(
        path = path,
        color = Color(0xFFEF4444).copy(alpha = 0.18f),
        style = DrawStroke(width = 22f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    drawPath(
        path = path,
        color = Color(0xFFEF4444).copy(alpha = 0.85f),
        style = DrawStroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

internal fun createInkBrush(
    tool: ToolType,
    color: Long,
    width: Float,
    scale: Float,
): InkApiBrush? {
    if (scale <= 0f) return null
    val family = when (tool) {
        ToolType.FOUNTAIN_PEN -> StockBrushes.pressurePen()
        ToolType.BALL_PEN -> StockBrushes.marker()
        ToolType.BRUSH_PEN -> StockBrushes.pressurePen()
        ToolType.HIGHLIGHTER -> StockBrushes.highlighter(SelfOverlap.DISCARD)
        else -> return null
    }
    val argb = color.toInt()
    val inkColor = if (tool == ToolType.HIGHLIGHTER) {
        val alpha = (((argb ushr 24) and 0xFF) * 0.30f).roundToInt().coerceIn(1, 255)
        (argb and 0x00FFFFFF) or (alpha shl 24)
    } else {
        argb
    }
    val sizePx = (width * scale).coerceAtLeast(0.75f)
    return InkApiBrush.createWithColorIntArgb(family, inkColor, sizePx, 0.1f)
}

internal fun InkApiStroke.toNoteCraftStroke(
    tool: ToolType,
    color: Long,
    width: Float,
    scale: Float,
    origin: Offset,
): InkStroke? {
    if (scale <= 0f || inputs.size == 0) return null
    val points = ArrayList<Pt>(inputs.size)
    for (index in 0 until inputs.size) {
        val input = inputs[index]
        if (!input.x.isFinite() || !input.y.isFinite()) continue
        val x = (input.x - origin.x) / scale
        val y = (input.y - origin.y) / scale
        val pressure = if (input.hasPressure) {
            input.pressure.coerceIn(0.15f, 1f)
        } else {
            0.5f
        }
        if (points.isEmpty() || hypot(x - points.last().x, y - points.last().y) >= 0.4f) {
            points.add(Pt(x, y, pressure))
        }
    }
    if (points.isEmpty()) return null
    val id = newId()
    return InkStroke(
        id = id,
        tool = tool,
        color = color,
        width = width,
        points = points,
        seed = id.hashCode(),
    )
}


internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLassoPreview(
    points: List<Pt>,
    transform: CanvasTransform,
) {    if (points.size < 2) return
    val path = Path()
    points.forEachIndexed { index, point ->
        val screen = Offset(transform.origin.x + point.x * transform.scale, transform.origin.y + point.y * transform.scale)
        if (index == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
    }
    path.close()
    drawPath(
        path = path,
        color = Color(0xFF2563EB).copy(alpha = 0.08f),
    )
    drawPath(
        path = path,
        color = Color(0xFF2563EB),
        style = DrawStroke(
            width = 2f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f), 0f),
        ),
    )
}
