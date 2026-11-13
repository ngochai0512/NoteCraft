package com.notecraft.ui.editor

import android.graphics.Paint
import android.graphics.Typeface
import android.text.StaticLayout
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withSave
import com.notecraft.model.PAGE_HEIGHT
import com.notecraft.model.PAGE_WIDTH
import com.notecraft.model.Page
import com.notecraft.model.PageBackground
import com.notecraft.model.ShapeType
import com.notecraft.model.StickyNote
import com.notecraft.model.Stroke as InkStroke
import com.notecraft.model.TextItem
import com.notecraft.model.ToolType
import com.notecraft.util.Bounds
import com.notecraft.util.buildStickyLayout
import com.notecraft.util.buildTextLayout
import com.notecraft.util.StickyTextPadding
import com.notecraft.util.approximateBounds
import com.notecraft.util.effectiveHeight
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

private val LightPaperTop = Color(0xFFFCFCFA)
private val LightPaperWarm = Color(0xFFFBF8F1)
private val LightRule = Color(0xFFE2E8F0)
private val LightGrid = Color(0xFFE2E8F0)
private val LightMargin = Color(0xFFEF4444)
private val LightDot = Color(0xFF94A3B8)

private val DarkPaperTop = Color(0xFF1B2432)
private val DarkPaperWarm = Color(0xFF141B26)
private val DarkRule = Color(0xFF3A4A5F)
private val DarkGrid = Color(0xFF3A4A5F)
private val DarkMargin = Color(0xFF9F3B3B)
private val DarkDot = Color(0xFF66788F)

private val TemplateLabelColor = Color(0xFF94A3B8)
private val SelectionColor = Color(0xFF2563EB)
private val SelectionColorDark = Color(0xFF60A5FA)

/** Paper/rule tint for the darkest ink that still needs to read on the dark surface. */
private val DarkInkMix = Color(0xFFEDF1F8)

private fun selectionColorFor(darkPaper: Boolean): Color =
    if (darkPaper) SelectionColorDark else SelectionColor

/**
 * Maps an authored ink color to the palette used on the current paper. On dark paper, dark inks
 * are lifted toward a warm white so black notes stay readable; light inks keep their hue.
 * Stored colors are never changed, so switching themes does not modify the note.
 */
internal fun paperInkColor(argb: Long, darkPaper: Boolean): Color {
    val color = Color(argb)
    if (!darkPaper) return color
    if (color.luminance() >= 0.62f) return color
    val mix = 0.84f
    return Color(
        red = color.red + (DarkInkMix.red - color.red) * mix,
        green = color.green + (DarkInkMix.green - color.green) * mix,
        blue = color.blue + (DarkInkMix.blue - color.blue) * mix,
        alpha = color.alpha,
    )
}

private data class CachedTextLayout(val spec: TextItem, val layout: StaticLayout)
private data class CachedStickyLayout(val spec: StickyNote, val layout: StaticLayout)

private val textLayoutCache = object : LinkedHashMap<String, CachedTextLayout>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedTextLayout>?): Boolean =
        size > 96
}

private val stickyLayoutCache = object : LinkedHashMap<String, CachedStickyLayout>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedStickyLayout>?): Boolean =
        size > 64
}

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
    darkPaper: Boolean = false,
) {
    val pageWidth = PAGE_WIDTH * scale
    val pageHeight = PAGE_HEIGHT * scale
    if (drawPaper) {
        drawRect(
            brush = paperBrush(darkPaper),
            topLeft = origin,
            size = Size(pageWidth, pageHeight),
        )
    }
    val content: DrawScope.() -> Unit = {
        if (drawBackground) drawPageBackground(page.background, scale, origin, darkPaper = darkPaper)
        page.strokes.forEach { stroke ->
            val selected = stroke.id in selectedStrokeIds
            if (selected) {
                drawStrokeBody(
                    stroke = stroke,
                    scale = scale,
                    origin = origin,
                    overrideColor = selectionColorFor(darkPaper),
                    alpha = 0.22f,
                    extraWidth = 10f,
                )
            }
            drawStrokeBody(stroke, scale, origin, darkPaper = darkPaper)
        }
        page.texts.forEach { item ->
            drawTextItem(item, scale, origin, darkPaper = darkPaper)
        }
        page.stickies.forEach { note ->
            drawStickyNote(note, scale, origin)
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
    // Handles sit outside the page bounds, so the selection frame is drawn unclipped.
    if (drawSelection && (selectedStrokeIds.isNotEmpty() || selectedTextIds.isNotEmpty())) {
        val bounds = selectionBounds(page, selectedStrokeIds, selectedTextIds)
        if (bounds != null) {
            drawSelectionFrame(bounds, scale, origin, darkPaper = darkPaper)
        }
    }
}

private fun paperBrush(darkPaper: Boolean): Brush =
    if (darkPaper) {
        Brush.verticalGradient(listOf(DarkPaperTop, DarkPaperWarm))
    } else {
        Brush.verticalGradient(listOf(LightPaperTop, LightPaperWarm))
    }

/**
 * Fills the whole visible editor area with the paper surface. Used by the editor so the paper is
 * edge-to-edge instead of a sheet resting on a desk.
 */
fun DrawScope.drawPaperSurface(darkPaper: Boolean = false) {
    drawRect(brush = paperBrush(darkPaper))
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
    darkPaper: Boolean = false,
) {
    if (scale <= 0f || right <= left || bottom <= top) return
    val pageLeft = origin.x
    val pageTop = origin.y
    val pageRight = origin.x + PAGE_WIDTH * scale
    val pageBottom = origin.y + PAGE_HEIGHT * scale
    val ruleColor = if (darkPaper) DarkRule else LightRule
    val gridColor = if (darkPaper) DarkGrid else LightGrid
    val marginColor = if (darkPaper) DarkMargin else LightMargin
    val dotColor = if (darkPaper) DarkDot else LightDot
    when (background) {
        PageBackground.BLANK -> Unit

        PageBackground.LINED -> {
            // 8 mm ruled paper.
            val spacing = 38f * scale
            val lineWidth = 1.1f * scale.coerceAtLeast(0.3f)
            var y = origin.y + spacing * ceil((top - origin.y) / spacing)
            while (y <= bottom) {
                drawLine(
                    color = ruleColor.copy(alpha = 0.65f),
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = lineWidth,
                )
                y += spacing
            }
            val marginX = origin.x + 90f * scale
            if (marginX in left..right) {
                drawLine(
                    color = marginColor.copy(alpha = 0.30f),
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
                    color = gridColor.copy(alpha = 0.55f),
                    start = Offset(x, top),
                    end = Offset(x, bottom),
                    strokeWidth = lineWidth,
                )
                x += spacing
            }
            var y = origin.y + spacing * ceil((top - origin.y) / spacing)
            while (y <= bottom) {
                drawLine(
                    color = gridColor.copy(alpha = 0.55f),
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
                        color = dotColor.copy(alpha = 0.45f),
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
                    color = ruleColor.copy(alpha = 0.45f),
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = lineWidth,
                )
                y += spacing
            }

            listOf(topLineY, summaryY).forEach { lineY ->
                drawLine(
                    color = gridColor.copy(alpha = 0.85f),
                    start = Offset(left, lineY),
                    end = Offset(right, lineY),
                    strokeWidth = lineWidth * 1.2f,
                )
            }
            if (cueX in left..right) {
                drawLine(
                    color = marginColor.copy(alpha = 0.45f),
                    start = Offset(cueX, topLineY),
                    end = Offset(cueX, summaryY),
                    strokeWidth = lineWidth * 1.2f,
                )
            }
        }

        PageBackground.PLANNER -> {
            clipRect(pageLeft, pageTop, pageRight, pageBottom) {
                val margin = 48f * scale
                val innerLeft = pageLeft + margin
                val innerRight = pageRight - margin
                val contentWidth = innerRight - innerLeft
                val lineWidth = 1.1f * scale.coerceAtLeast(0.3f)
                val thin = 0.9f * scale.coerceAtLeast(0.3f)
                val headerTop = pageTop + 48f * scale
                val headerBottom = pageTop + 150f * scale
                val mainTop = pageTop + 190f * scale
                val scheduleBottom = pageTop + 1010f * scale
                val tasksTop = pageTop + 1050f * scale
                val tasksBottom = pageBottom - 44f * scale
                val scheduleRight = innerLeft + contentWidth * 0.32f
                val notesLeft = scheduleRight + 24f * scale

                drawTemplateLabel(
                    text = "DAILY PLANNER",
                    x = innerLeft,
                    y = headerTop + 28f * scale,
                    scale = scale,
                    size = 20f,
                )
                drawTemplateLabel(
                    text = "DATE",
                    x = innerLeft + 10f * scale,
                    y = headerTop + 62f * scale,
                    scale = scale,
                    size = 11f,
                )
                drawTemplateLabel(
                    text = "FOCUS",
                    x = innerLeft + contentWidth * 0.55f,
                    y = headerTop + 62f * scale,
                    scale = scale,
                    size = 11f,
                )
                drawRoundRect(
                    color = gridColor.copy(alpha = 0.65f),
                    topLeft = Offset(innerLeft, headerTop),
                    size = Size(contentWidth, headerBottom - headerTop),
                    cornerRadius = CornerRadius(10f * scale, 10f * scale),
                    style = DrawStroke(width = lineWidth),
                )
                drawLine(
                    color = gridColor.copy(alpha = 0.65f),
                    start = Offset(innerLeft + contentWidth * 0.52f, headerTop),
                    end = Offset(innerLeft + contentWidth * 0.52f, headerBottom),
                    strokeWidth = lineWidth,
                )
                drawLine(
                    color = ruleColor.copy(alpha = 0.55f),
                    start = Offset(innerLeft + 10f * scale, headerTop + 78f * scale),
                    end = Offset(innerLeft + contentWidth * 0.48f, headerTop + 78f * scale),
                    strokeWidth = thin,
                )
                drawLine(
                    color = ruleColor.copy(alpha = 0.55f),
                    start = Offset(innerLeft + contentWidth * 0.55f, headerTop + 78f * scale),
                    end = Offset(innerRight - 10f * scale, headerTop + 78f * scale),
                    strokeWidth = thin,
                )

                drawRoundRect(
                    color = gridColor.copy(alpha = 0.65f),
                    topLeft = Offset(innerLeft, mainTop),
                    size = Size(scheduleRight - innerLeft, scheduleBottom - mainTop),
                    cornerRadius = CornerRadius(10f * scale, 10f * scale),
                    style = DrawStroke(width = lineWidth),
                )
                drawTemplateLabel(
                    text = "SCHEDULE",
                    x = innerLeft + 10f * scale,
                    y = mainTop + 22f * scale,
                    scale = scale,
                    size = 11f,
                )
                val slotHeight = 54f * scale
                var slotY = mainTop + 40f * scale
                var hour = 8
                while (slotY < scheduleBottom - 8f * scale) {
                    drawLine(
                        color = ruleColor.copy(alpha = 0.55f),
                        start = Offset(innerLeft, slotY),
                        end = Offset(scheduleRight, slotY),
                        strokeWidth = thin,
                    )
                    if (hour <= 20) {
                        drawTemplateLabel(
                            text = "$hour:00",
                            x = innerLeft + 8f * scale,
                            y = slotY + 16f * scale,
                            scale = scale,
                            size = 10f,
                            color = dotColor.copy(alpha = 0.9f),
                        )
                    }
                    slotY += slotHeight
                    hour++
                }

                drawRoundRect(
                    color = gridColor.copy(alpha = 0.65f),
                    topLeft = Offset(notesLeft, mainTop),
                    size = Size(innerRight - notesLeft, scheduleBottom - mainTop),
                    cornerRadius = CornerRadius(10f * scale, 10f * scale),
                    style = DrawStroke(width = lineWidth),
                )
                drawTemplateLabel(
                    text = "NOTES",
                    x = notesLeft + 10f * scale,
                    y = mainTop + 22f * scale,
                    scale = scale,
                    size = 11f,
                )
                var noteY = mainTop + 46f * scale
                while (noteY < scheduleBottom - 10f * scale) {
                    drawLine(
                        color = ruleColor.copy(alpha = 0.50f),
                        start = Offset(notesLeft + 10f * scale, noteY),
                        end = Offset(innerRight - 10f * scale, noteY),
                        strokeWidth = thin,
                    )
                    noteY += 38f * scale
                }

                drawRoundRect(
                    color = gridColor.copy(alpha = 0.65f),
                    topLeft = Offset(innerLeft, tasksTop),
                    size = Size(contentWidth, tasksBottom - tasksTop),
                    cornerRadius = CornerRadius(10f * scale, 10f * scale),
                    style = DrawStroke(width = lineWidth),
                )
                drawTemplateLabel(
                    text = "TASKS",
                    x = innerLeft + 10f * scale,
                    y = tasksTop + 22f * scale,
                    scale = scale,
                    size = 11f,
                )
                var taskY = tasksTop + 44f * scale
                while (taskY < tasksBottom - 8f * scale) {
                    val box = 16f * scale
                    drawRoundRect(
                        color = gridColor.copy(alpha = 0.85f),
                        topLeft = Offset(innerLeft + 12f * scale, taskY - box + 2f * scale),
                        size = Size(box, box),
                        cornerRadius = CornerRadius(3f * scale, 3f * scale),
                        style = DrawStroke(width = thin),
                    )
                    drawLine(
                        color = ruleColor.copy(alpha = 0.50f),
                        start = Offset(innerLeft + 38f * scale, taskY),
                        end = Offset(innerRight - 12f * scale, taskY),
                        strokeWidth = thin,
                    )
                    taskY += 34f * scale
                }
            }
        }

        PageBackground.WEEKLY -> {
            clipRect(pageLeft, pageTop, pageRight, pageBottom) {
                val margin = 40f * scale
                val innerLeft = pageLeft + margin
                val innerRight = pageRight - margin
                val tableTop = pageTop + 90f * scale
                val headerBottom = tableTop + 50f * scale
                val tableBottom = pageBottom - 44f * scale
                val lineWidth = 1.1f * scale.coerceAtLeast(0.3f)
                val thin = 0.9f * scale.coerceAtLeast(0.3f)
                val days = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
                val columnWidth = (innerRight - innerLeft) / days.size

                drawTemplateLabel(
                    text = "WEEKLY PLAN",
                    x = innerLeft,
                    y = pageTop + 58f * scale,
                    scale = scale,
                    size = 20f,
                )
                drawRect(
                    color = gridColor.copy(alpha = 0.70f),
                    topLeft = Offset(innerLeft, tableTop),
                    size = Size(innerRight - innerLeft, headerBottom - tableTop),
                    style = DrawStroke(width = lineWidth),
                )
                days.forEachIndexed { index, day ->
                    val x = innerLeft + columnWidth * index
                    if (index > 0) {
                        drawLine(
                            color = gridColor.copy(alpha = 0.70f),
                            start = Offset(x, tableTop),
                            end = Offset(x, tableBottom),
                            strokeWidth = lineWidth,
                        )
                    }
                    drawTemplateLabel(
                        text = day,
                        x = x + columnWidth / 2f - 10f * scale,
                        y = headerBottom - 16f * scale,
                        scale = scale,
                        size = 11f,
                    )
                    var y = headerBottom + 40f * scale
                    while (y < tableBottom - 6f * scale) {
                        drawLine(
                            color = ruleColor.copy(alpha = 0.48f),
                            start = Offset(x + 8f * scale, y),
                            end = Offset(x + columnWidth - 8f * scale, y),
                            strokeWidth = thin,
                        )
                        y += 38f * scale
                    }
                }
                drawRect(
                    color = gridColor.copy(alpha = 0.70f),
                    topLeft = Offset(innerLeft, tableTop),
                    size = Size(innerRight - innerLeft, tableBottom - tableTop),
                    style = DrawStroke(width = lineWidth),
                )
            }
        }

        PageBackground.MUSIC -> {
            clipRect(pageLeft, pageTop, pageRight, pageBottom) {
                val margin = 70f * scale
                val innerLeft = pageLeft + margin
                val innerRight = pageRight - margin
                val staffGap = 14f * scale
                val systemGap = 62f * scale
                val lineWidth = 1f * scale.coerceAtLeast(0.3f)
                var systemTop = pageTop + 100f * scale

                drawTemplateLabel(
                    text = "MUSIC",
                    x = innerLeft,
                    y = pageTop + 58f * scale,
                    scale = scale,
                    size = 20f,
                )
                while (systemTop + staffGap * 4f < pageBottom - 40f * scale) {
                    for (line in 0..4) {
                        val y = systemTop + staffGap * line
                        drawLine(
                            color = ruleColor.copy(alpha = 0.75f),
                            start = Offset(innerLeft, y),
                            end = Offset(innerRight, y),
                            strokeWidth = lineWidth,
                        )
                    }
                    listOf(innerLeft, innerRight).forEach { x ->
                        drawLine(
                            color = gridColor.copy(alpha = 0.80f),
                            start = Offset(x, systemTop),
                            end = Offset(x, systemTop + staffGap * 4f),
                            strokeWidth = lineWidth * 1.2f,
                        )
                    }
                    systemTop += staffGap * 4f + systemGap
                }
            }
        }

        PageBackground.STORYBOARD -> {
            clipRect(pageLeft, pageTop, pageRight, pageBottom) {
                val margin = 44f * scale
                val innerLeft = pageLeft + margin
                val innerRight = pageRight - margin
                val contentTop = pageTop + 100f * scale
                val contentBottom = pageBottom - 44f * scale
                val gap = 18f * scale
                val lineWidth = 1.1f * scale.coerceAtLeast(0.3f)
                val thin = 0.9f * scale.coerceAtLeast(0.3f)
                val columns = 2
                val rows = 3
                val cellWidth = (innerRight - innerLeft - gap * (columns - 1)) / columns
                val cellHeight = (contentBottom - contentTop - gap * (rows - 1)) / rows

                drawTemplateLabel(
                    text = "STORYBOARD",
                    x = innerLeft,
                    y = pageTop + 58f * scale,
                    scale = scale,
                    size = 20f,
                )
                for (row in 0 until rows) {
                    for (column in 0 until columns) {
                        val x = innerLeft + column * (cellWidth + gap)
                        val y = contentTop + row * (cellHeight + gap)
                        drawRoundRect(
                            color = gridColor.copy(alpha = 0.72f),
                            topLeft = Offset(x, y),
                            size = Size(cellWidth, cellHeight),
                            cornerRadius = CornerRadius(8f * scale, 8f * scale),
                            style = DrawStroke(width = lineWidth),
                        )
                        drawLine(
                            color = ruleColor.copy(alpha = 0.55f),
                            start = Offset(x + 10f * scale, y + cellHeight - 24f * scale),
                            end = Offset(x + cellWidth - 10f * scale, y + cellHeight - 24f * scale),
                            strokeWidth = thin,
                        )
                    }
                }
            }
        }

        PageBackground.CHECKLIST -> {
            clipRect(pageLeft, pageTop, pageRight, pageBottom) {
                val margin = 48f * scale
                val innerLeft = pageLeft + margin
                val innerRight = pageRight - margin
                val contentTop = pageTop + 100f * scale
                val contentBottom = pageBottom - 44f * scale
                val gap = 30f * scale
                val columnWidth = (innerRight - innerLeft - gap) / 2f
                val thin = 0.9f * scale.coerceAtLeast(0.3f)

                drawTemplateLabel(
                    text = "CHECKLIST",
                    x = innerLeft,
                    y = pageTop + 58f * scale,
                    scale = scale,
                    size = 20f,
                )
                for (column in 0..1) {
                    val x = innerLeft + column * (columnWidth + gap)
                    var y = contentTop
                    while (y + 28f * scale < contentBottom) {
                        val box = 16f * scale
                        drawRoundRect(
                            color = gridColor.copy(alpha = 0.85f),
                            topLeft = Offset(x, y),
                            size = Size(box, box),
                            cornerRadius = CornerRadius(3f * scale, 3f * scale),
                            style = DrawStroke(width = thin),
                        )
                        drawLine(
                            color = ruleColor.copy(alpha = 0.58f),
                            start = Offset(x + box + 10f * scale, y + box / 2f),
                            end = Offset(x + columnWidth, y + box / 2f),
                            strokeWidth = thin,
                        )
                        y += 42f * scale
                    }
                }
                drawLine(
                    color = gridColor.copy(alpha = 0.65f),
                    start = Offset(innerLeft + columnWidth + gap / 2f, contentTop),
                    end = Offset(innerLeft + columnWidth + gap / 2f, contentBottom),
                    strokeWidth = thin,
                )
            }
        }
    }
}

private fun DrawScope.drawTemplateLabel(
    text: String,
    x: Float,
    y: Float,
    scale: Float,
    size: Float = 14f,
    color: Color = TemplateLabelColor.copy(alpha = 0.9f),
) {
    if (scale <= 0f) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        textSize = size * scale
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

fun DrawScope.drawStrokeBody(
    stroke: InkStroke,
    scale: Float,
    origin: Offset,
    overrideColor: Color? = null,
    alpha: Float = 1f,
    extraWidth: Float = 0f,
    darkPaper: Boolean = false,
) {
    val color = overrideColor ?: paperInkColor(stroke.color, darkPaper)
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
            val blendMode = if (darkPaper) BlendMode.Screen else BlendMode.Multiply
            drawPath(
                path = path,
                color = highlight,
                style = DrawStroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = blendMode,
            )
            drawPath(
                path = path,
                color = highlight.copy(alpha = 0.10f * alpha),
                style = DrawStroke(width = width * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = blendMode,
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

private fun textLayoutFor(item: TextItem, darkPaper: Boolean): StaticLayout {
    val key = if (darkPaper) "${item.id}:dark" else item.id
    synchronized(textLayoutCache) {
        val cached = textLayoutCache[key]
        if (cached != null && cached.spec == item) return cached.layout
    }
    val layout = buildTextLayout(item, colorOverride = paperInkColor(item.color, darkPaper))
    synchronized(textLayoutCache) {
        textLayoutCache[key] = CachedTextLayout(item, layout)
    }
    return layout
}

private fun stickyLayoutFor(note: StickyNote): StaticLayout {
    synchronized(stickyLayoutCache) {
        val cached = stickyLayoutCache[note.id]
        if (cached != null && cached.spec == note) return cached.layout
    }
    val layout = buildStickyLayout(note)
    synchronized(stickyLayoutCache) {
        stickyLayoutCache[note.id] = CachedStickyLayout(note, layout)
    }
    return layout
}

fun DrawScope.drawTextItem(
    item: TextItem,
    scale: Float,
    origin: Offset,
    darkPaper: Boolean = false,
) {
    if (item.text.isBlank() || scale <= 0f) return
    val highlightColor = item.formatting.highlightColor
    if (highlightColor != null) {
        val bounds = item.approximateBounds()
        drawRoundRect(
            color = paperInkColor(highlightColor, darkPaper).copy(alpha = 0.38f),
            topLeft = Offset(
                origin.x + bounds.left * scale,
                origin.y + bounds.top * scale,
            ),
            size = Size(bounds.width * scale, bounds.height * scale),
            cornerRadius = CornerRadius(
                (6f * scale).coerceAtLeast(2f),
                (6f * scale).coerceAtLeast(2f),
            ),
        )
    }
    val layout = textLayoutFor(item, darkPaper)
    val canvas = drawContext.canvas.nativeCanvas
    canvas.withSave {
        translate(origin.x + item.x * scale, origin.y + item.y * scale)
        scale(scale, scale)
        layout.draw(this)
    }
}

/** A colored note card with wrapped text, rendered above ink and text on the page. */
fun DrawScope.drawStickyNote(note: StickyNote, scale: Float, origin: Offset) {
    if (scale <= 0f) return
    val left = origin.x + note.x * scale
    val top = origin.y + note.y * scale
    val width = note.width * scale
    val layout = if (note.text.isNotBlank()) stickyLayoutFor(note) else null
    val height = max(
        note.height,
        if (layout != null) layout.height + StickyTextPadding * 2f else note.height,
    ) * scale
    val radius = CornerRadius((10f * scale).coerceAtLeast(2f), (10f * scale).coerceAtLeast(2f))
    val bodyColor = Color(note.color.toInt())

    drawRoundRect(
        color = Color.Black.copy(alpha = 0.16f),
        topLeft = Offset(left, top + (4f * scale).coerceAtLeast(1f)),
        size = Size(width, height),
        cornerRadius = radius,
    )
    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = radius,
    )

    val fold = (18f * scale).coerceAtLeast(4f)
    if (width > fold && height > fold) {
        val foldPath = Path().apply {
            moveTo(left + width, top)
            lineTo(left + width, top + fold)
            lineTo(left + width - fold, top)
            close()
        }
        drawPath(foldPath, Color.Black.copy(alpha = 0.08f))
    }

    if (layout != null) {
        val padding = StickyTextPadding * scale
        val canvas = drawContext.canvas.nativeCanvas
        clipRect(left, top, left + width, top + height) {
            canvas.withSave {
                translate(left + padding, top + padding)
                scale(scale, scale)
                layout.draw(this)
            }
        }
    }
}

/** Dashed frame shown around sticky notes while the sticky tool is active. */
fun DrawScope.drawStickyFrame(
    note: StickyNote,
    scale: Float,
    origin: Offset,
    darkPaper: Boolean = false,
) {
    if (scale <= 0f) return
    val left = origin.x + note.x * scale
    val top = origin.y + note.y * scale
    val width = note.width * scale
    val height = note.effectiveHeight() * scale
    drawRoundRect(
        color = selectionColorFor(darkPaper).copy(alpha = 0.55f),
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius((10f * scale).coerceAtLeast(2f), (10f * scale).coerceAtLeast(2f)),
        style = DrawStroke(
            width = 1.4f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f), 0f),
        ),
    )
}

/** Dashed box and bottom-right resize handle shown while the text tool is active. */
fun DrawScope.drawTextFrame(
    item: TextItem,
    scale: Float,
    origin: Offset,
    darkPaper: Boolean = false,
) {
    if (scale <= 0f) return
    val selectionColor = selectionColorFor(darkPaper)
    val bounds = item.approximateBounds()
    val left = origin.x + bounds.left * scale
    val top = origin.y + bounds.top * scale
    val right = origin.x + bounds.right * scale
    val bottom = origin.y + bounds.bottom * scale
    val effect = PathEffect.dashPathEffect(
        floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
        0f,
    )
    drawRect(
        color = selectionColor.copy(alpha = 0.65f),
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        style = DrawStroke(width = 1.2.dp.toPx(), pathEffect = effect),
    )
    val handleRadius = 7.dp.toPx()
    val handle = Offset(right, bottom)
    drawCircle(color = Color.White, radius = handleRadius, center = handle)
    drawCircle(
        color = selectionColor,
        radius = handleRadius,
        center = handle,
        style = DrawStroke(width = 1.5.dp.toPx()),
    )
    drawCircle(color = selectionColor, radius = 3.dp.toPx(), center = handle)
}

/**
 * Dashed selection box with fixed-size corner handles and a rotation handle above the box.
 * Handle geometry is expressed in dp so the hit test in `DrawingCanvas` can match it at any zoom.
 */
fun DrawScope.drawSelectionFrame(
    bounds: Bounds,
    scale: Float,
    origin: Offset,
    darkPaper: Boolean = false,
) {
    if (scale <= 0f) return
    val selectionColor = selectionColorFor(darkPaper)
    val left = origin.x + bounds.left * scale
    val top = origin.y + bounds.top * scale
    val width = bounds.width * scale
    val height = bounds.height * scale
    val padding = 8.dp.toPx()
    val handleRadius = 6.dp.toPx()
    val rotationOffset = 28.dp.toPx()
    val effect = PathEffect.dashPathEffect(
        floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
        0f,
    )
    drawRoundRect(
        color = selectionColor,
        topLeft = Offset(left - padding, top - padding),
        size = Size(width + padding * 2, height + padding * 2),
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        style = DrawStroke(width = 1.6.dp.toPx(), pathEffect = effect),
    )
    val topCenter = Offset(left + width / 2f, top - padding)
    val rotationHandle = Offset(topCenter.x, topCenter.y - rotationOffset)
    drawLine(
        color = selectionColor,
        start = topCenter,
        end = rotationHandle,
        strokeWidth = 1.2.dp.toPx(),
    )
    drawCircle(color = Color.White, radius = handleRadius, center = rotationHandle)
    drawCircle(
        color = selectionColor,
        radius = handleRadius,
        center = rotationHandle,
        style = DrawStroke(width = 1.5.dp.toPx()),
    )
    listOf(
        Offset(left - padding, top - padding),
        Offset(left + width + padding, top - padding),
        Offset(left - padding, top + height + padding),
        Offset(left + width + padding, top + height + padding),
    ).forEach { point ->
        drawCircle(color = Color.White, radius = handleRadius, center = point)
        drawCircle(
            color = selectionColor,
            radius = handleRadius,
            center = point,
            style = DrawStroke(width = 1.5.dp.toPx()),
        )
    }
}
