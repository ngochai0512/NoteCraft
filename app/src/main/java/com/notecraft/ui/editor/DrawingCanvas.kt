package com.notecraft.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.notecraft.model.PAGE_HEIGHT
import com.notecraft.model.PAGE_WIDTH
import com.notecraft.model.Page
import com.notecraft.model.Pt
import com.notecraft.model.ShapeType
import com.notecraft.model.Stroke as InkStroke
import com.notecraft.model.TextItem
import com.notecraft.model.ToolType
import com.notecraft.model.newId
import com.notecraft.util.hitTest
import com.notecraft.util.selectInsidePolygon
import com.notecraft.util.selectionBounds
import com.notecraft.ui.theme.PaperSurface
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.brush.Brush as InkApiBrush
import androidx.ink.brush.SelfOverlap
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.Stroke as InkApiStroke
import android.util.Log
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private data class CanvasTransform(val scale: Float, val origin: Offset)

private class CanvasViewport {
    var zoom by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
}

private enum class GestureMode { NONE, DRAW, SHAPE, ERASE, TEXT, LASSO, MOVE, LASER }

/** Freehand tools rendered through Jetpack Ink while authoring. */
private val InkToolTypes = setOf(
    ToolType.FOUNTAIN_PEN,
    ToolType.BALL_PEN,
    ToolType.BRUSH_PEN,
    ToolType.HIGHLIGHTER,
)

@Composable
fun DrawingCanvas(
    page: Page,
    tool: ToolType,
    color: Long,
    width: Float,
    shapeType: ShapeType,
    selectedStrokeIds: Set<String>,
    selectedTextIds: Set<String>,
    onStrokeCommitted: (InkStroke) -> Unit,
    onEraseStart: () -> Unit,
    onEraseMove: (Offset) -> Unit,
    onEraseEnd: () -> Unit,
    onLassoSelected: (Set<String>, Set<String>) -> Unit,
    onSelectionMoveStart: () -> Unit,
    onSelectionMove: (Offset) -> Unit,
    onSelectionMoveEnd: () -> Unit,
    onAddText: (Offset) -> Unit,
    onEditText: (TextItem) -> Unit,
    onShowPages: () -> Unit,
    onShowPageBackground: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val compactHeight = configuration.screenHeightDp < 480
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val viewport = remember(page.id) { CanvasViewport() }
    var previewStroke by remember { mutableStateOf<InkStroke?>(null) }
    var lassoPreview by remember { mutableStateOf<List<Pt>>(emptyList()) }
    var laserPoints by remember { mutableStateOf<List<Pt>>(emptyList()) }
    var laserFadeKey by remember { mutableStateOf(0) }

    val currentPage by rememberUpdatedState(page)
    val currentSelectedStrokes by rememberUpdatedState(selectedStrokeIds)
    val currentSelectedTexts by rememberUpdatedState(selectedTextIds)
    val commitStroke by rememberUpdatedState(onStrokeCommitted)
    val eraseStart by rememberUpdatedState(onEraseStart)
    val eraseMove by rememberUpdatedState(onEraseMove)
    val eraseEnd by rememberUpdatedState(onEraseEnd)
    val lassoSelected by rememberUpdatedState(onLassoSelected)
    val selectionMoveStart by rememberUpdatedState(onSelectionMoveStart)
    val selectionMove by rememberUpdatedState(onSelectionMove)
    val selectionMoveEnd by rememberUpdatedState(onSelectionMoveEnd)
    val addText by rememberUpdatedState(onAddText)
    val editText by rememberUpdatedState(onEditText)

    LaunchedEffect(page.id) {
        viewport.zoom = 1f
        viewport.pan = Offset.Zero
    }

    LaunchedEffect(page.id, tool) {
        previewStroke = null
        lassoPreview = emptyList()
        laserPoints = emptyList()
    }

    // The laser pointer is a trail that fades shortly after the finger lifts.
    LaunchedEffect(laserFadeKey) {
        if (laserFadeKey > 0) {
            delay(450)
            laserPoints = emptyList()
        }
    }

    fun baseScale(size: IntSize): Float {
        if (size.width == 0 || size.height == 0) return 1f
        return min(size.width / PAGE_WIDTH, size.height / PAGE_HEIGHT)
    }

    fun currentTransform(): CanvasTransform {
        val scale = baseScale(canvasSize) * viewport.zoom
        val origin = Offset(
            (canvasSize.width - PAGE_WIDTH * scale) / 2f + viewport.pan.x,
            (canvasSize.height - PAGE_HEIGHT * scale) / 2f + viewport.pan.y,
        )
        return CanvasTransform(scale, origin)
    }

    fun screenToPage(position: Offset): Offset {
        val transform = currentTransform()
        if (transform.scale <= 0f) return Offset.Zero
        return (position - transform.origin) / transform.scale
    }

    // The paper is effectively infinite, so strokes are accepted anywhere on the canvas.
    fun isInsidePage(point: Offset): Boolean = true

    fun applyTransform(focus: Offset, panChange: Offset, zoomChange: Float) {
        if (canvasSize.width == 0 || canvasSize.height == 0) return
        val transform = currentTransform()
        val previousFocus = focus - panChange
        val focusInPage = (previousFocus - transform.origin) / transform.scale
        val newZoom = (viewport.zoom * zoomChange).coerceIn(1f, 8f)
        val newScale = baseScale(canvasSize) * newZoom
        val candidate = Offset(
            focus.x - focusInPage.x * newScale - (canvasSize.width - PAGE_WIDTH * newScale) / 2f,
            focus.y - focusInPage.y * newScale - (canvasSize.height - PAGE_HEIGHT * newScale) / 2f,
        )
        viewport.zoom = newZoom
        viewport.pan = candidate
    }

    fun zoomBy(factor: Float) {
        if (canvasSize.width == 0 || canvasSize.height == 0) return
        applyTransform(
            focus = Offset(canvasSize.width / 2f, canvasSize.height / 2f),
            panChange = Offset.Zero,
            zoomChange = factor,
        )
    }

    fun resetViewport() {
        viewport.zoom = 1f
        viewport.pan = Offset.Zero
    }

    val hintBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    val hintText = MaterialTheme.colorScheme.onSurfaceVariant
    val inkTransform = currentTransform()
    val inkScale = inkTransform.scale
    val inkBrush = if (tool in InkToolTypes) {
        remember(tool, color, width, inkScale) {
            runCatching { createInkBrush(tool, color, width, inkScale) }
                .onFailure { Log.e("NoteCraftInk", "Unable to initialize Ink brush", it) }
                .getOrNull()
        }
    } else {
        null
    }
    val useInk = inkBrush != null
    val inkTransformModifier = if (useInk) {
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } > 1) {
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = true)
                        if (centroid != Offset.Unspecified && (zoomChange != 1f || panChange != Offset.Zero)) {
                            applyTransform(centroid, panChange, zoomChange)
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(PaperSurface)
            .onSizeChanged { canvasSize = it }
            .then(inkTransformModifier),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tool, page.id, color, width, shapeType, selectedStrokeIds, selectedTextIds) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)

                        if (useInk) {
                            // The Ink overlay owns the wet stroke. Wait out the gesture without
                            // consuming so it remains available to InProgressStrokes.
                            var event = awaitPointerEvent()
                            while (event.changes.any { it.pressed }) {
                                event = awaitPointerEvent()
                            }
                            return@awaitEachGesture
                        }

                        val downPage = screenToPage(down.position)
                        var mode = GestureMode.NONE
                        var points = mutableListOf<Pt>()
                        var lassoPoints = mutableListOf<Pt>()
                        var strokeId = newId()
                        var moved = false
                        var transformMode = false
                        var eraseActive = false
                        var selectionMoveActive = false

                        fun pressureOf(change: PointerInputChange): Float {
                            val raw = change.pressure
                            return if (raw <= 0.01f) 0.5f else raw.coerceIn(0.15f, 1f)
                        }

                        fun makeStroke(): InkStroke? {
                            if (points.isEmpty()) return null
                            return InkStroke(
                                id = strokeId,
                                tool = tool,
                                color = color,
                                width = width,
                                points = points.toList(),
                                shapeType = if (tool == ToolType.SHAPE) shapeType else null,
                                seed = strokeId.hashCode(),
                            )
                        }

                        if (isInsidePage(downPage)) {
                            when (tool) {
                                ToolType.FOUNTAIN_PEN,
                                ToolType.BALL_PEN,
                                ToolType.BRUSH_PEN,
                                ToolType.PENCIL,
                                ToolType.HIGHLIGHTER,
                                ToolType.TAPE,
                                -> {
                                    mode = GestureMode.DRAW
                                    points.add(Pt(downPage.x, downPage.y, pressureOf(down)))
                                    previewStroke = makeStroke()
                                }

                                ToolType.LASER -> {
                                    mode = GestureMode.LASER
                                    points.add(Pt(downPage.x, downPage.y, pressureOf(down)))
                                    laserPoints = points.toList()
                                }

                                ToolType.SHAPE -> {
                                    mode = GestureMode.SHAPE
                                    points.add(Pt(downPage.x, downPage.y, 0.6f))
                                    points.add(Pt(downPage.x, downPage.y, 0.6f))
                                    previewStroke = makeStroke()
                                }

                                ToolType.ERASER -> {
                                    mode = GestureMode.ERASE
                                    eraseActive = true
                                    eraseStart()
                                    eraseMove(downPage)
                                }

                                ToolType.TEXT -> {
                                    mode = GestureMode.TEXT
                                }

                                ToolType.LASSO -> {
                                    val bounds = selectionBounds(
                                        currentPage,
                                        currentSelectedStrokes,
                                        currentSelectedTexts,
                                    )
                                    if (bounds != null && bounds.expand(14f).contains(downPage.x, downPage.y)) {
                                        mode = GestureMode.MOVE
                                        selectionMoveActive = true
                                        selectionMoveStart()
                                    } else {
                                        mode = GestureMode.LASSO
                                        lassoPoints.add(Pt(downPage.x, downPage.y, 0.5f))
                                        lassoPreview = lassoPoints.toList()
                                    }
                                }
                            }
                        }
                        down.consume()

                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.isEmpty()) break

                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount > 1 || transformMode) {
                                if (!transformMode) {
                                    transformMode = true
                                    previewStroke = null
                                    lassoPreview = emptyList()
                                    laserPoints = emptyList()
                                    if (eraseActive) {
                                        eraseEnd()
                                        eraseActive = false
                                    }
                                    if (selectionMoveActive) {
                                        selectionMoveEnd()
                                        selectionMoveActive = false
                                    }
                                    mode = GestureMode.NONE
                                }
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                if (centroid != Offset.Unspecified && (zoomChange != 1f || panChange != Offset.Zero)) {
                                    applyTransform(centroid, panChange, zoomChange)
                                }
                                event.changes.forEach { it.consume() }
                            } else {
                                val change = event.changes.firstOrNull()
                                if (change != null && change.pressed) {
                                    val pagePoint = screenToPage(change.position)
                                    val transform = currentTransform()
                                    when (mode) {
                                        GestureMode.DRAW, GestureMode.SHAPE -> {
                                            if (isInsidePage(pagePoint)) {
                                                val last = points.last()
                                                val distance = hypot(pagePoint.x - last.x, pagePoint.y - last.y)
                                                if (distance >= 1.6f) {
                                                    val newPoint = Pt(
                                                        pagePoint.x,
                                                        pagePoint.y,
                                                        pressureOf(change),
                                                    )
                                                    if (mode == GestureMode.SHAPE) {
                                                        points = mutableListOf(points.first(), newPoint)
                                                    } else {
                                                        points.add(newPoint)
                                                    }
                                                    previewStroke = makeStroke()
                                                }
                                            }
                                        }

                                        GestureMode.LASER -> {
                                            if (isInsidePage(pagePoint)) {
                                                val last = points.last()
                                                if (hypot(pagePoint.x - last.x, pagePoint.y - last.y) >= 1.2f) {
                                                    points.add(Pt(pagePoint.x, pagePoint.y, pressureOf(change)))
                                                    laserPoints = points.toList()
                                                }
                                            }
                                        }

                                        GestureMode.ERASE -> {
                                            if (isInsidePage(pagePoint)) eraseMove(pagePoint)
                                        }

                                        GestureMode.LASSO -> {
                                            if (isInsidePage(pagePoint)) {
                                                val last = lassoPoints.lastOrNull()
                                                if (last == null ||
                                                    hypot(pagePoint.x - last.x, pagePoint.y - last.y) >= 2f
                                                ) {
                                                    lassoPoints.add(Pt(pagePoint.x, pagePoint.y, 0.5f))
                                                    lassoPreview = lassoPoints.toList()
                                                }
                                            }
                                        }

                                        GestureMode.MOVE -> {
                                            val delta = change.position - change.previousPosition
                                            if (delta != Offset.Zero && transform.scale > 0f) {
                                                selectionMove(
                                                    Offset(
                                                        delta.x / transform.scale,
                                                        delta.y / transform.scale,
                                                    ),
                                                )
                                                moved = true
                                            }
                                        }

                                        GestureMode.TEXT -> {
                                            if ((change.position - down.position).getDistance() > touchSlop) {
                                                moved = true
                                            }
                                        }

                                        GestureMode.NONE -> Unit
                                    }
                                    change.consume()
                                }
                            }
                            if (event.changes.none { it.pressed }) break
                        }

                        if (!transformMode) {
                            when (mode) {
                                GestureMode.DRAW -> makeStroke()?.let(commitStroke)
                                GestureMode.SHAPE -> {
                                    if (points.size >= 2) {
                                        val first = points.first()
                                        val last = points.last()
                                        if (hypot(last.x - first.x, last.y - first.y) >= 5f) {
                                            makeStroke()?.let(commitStroke)
                                        }
                                    }
                                }

                                GestureMode.ERASE -> if (eraseActive) eraseEnd()
                                GestureMode.MOVE -> if (selectionMoveActive) selectionMoveEnd()

                                GestureMode.LASSO -> {
                                    if (lassoPoints.size >= 3) {
                                        val (strokeIds, textIds) = selectInsidePolygon(currentPage, lassoPoints)
                                        lassoSelected(strokeIds, textIds)
                                    }
                                }

                                GestureMode.TEXT -> {
                                    if (!moved && isInsidePage(downPage)) {
                                        val hit = currentPage.texts.lastOrNull {
                                            it.hitTest(downPage.x, downPage.y, 8f)
                                        }
                                        if (hit != null) editText(hit) else addText(downPage)
                                    }
                                }

                                GestureMode.LASER -> laserFadeKey++

                                GestureMode.NONE -> Unit
                            }
                        }

                        previewStroke = null
                        lassoPreview = emptyList()
                    }
                },
        ) {
            drawPaperSurface()

            val transform = currentTransform()
            if (transform.scale > 0f) {
                drawPageBackground(
                    background = page.background,
                    scale = transform.scale,
                    origin = transform.origin,
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                )
                drawPageContent(
                    page = page,
                    scale = transform.scale,
                    origin = transform.origin,
                    selectedStrokeIds = selectedStrokeIds,
                    selectedTextIds = selectedTextIds,
                    drawSelection = true,
                    drawPaper = false,
                    drawBackground = false,
                    clip = false,
                )

                previewStroke?.let { stroke ->
                    drawStrokeBody(stroke, transform.scale, transform.origin)
                }

                drawLaserTrail(laserPoints, transform)
                drawLassoPreview(lassoPreview, transform)
            }
        }

        if (useInk && inkBrush != null && inkScale > 0f) {
            InProgressStrokes(
                defaultBrush = inkBrush,
                onStrokesFinished = { strokes ->
                    strokes.forEach { inkStroke ->
                        inkStroke.toNoteCraftStroke(
                            tool = tool,
                            color = color,
                            width = width,
                            scale = inkScale,
                            origin = inkTransform.origin,
                        )?.let { converted -> commitStroke(converted) }
                    }
                },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(
                    WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                tonalElevation = 0.dp,
                shadowElevation = 10.dp,
                modifier = Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(16.dp),
                ),
            ) {
                if (compactHeight) {
                    Row(
                        modifier = Modifier.padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ZoomControlButton(
                            icon = Icons.Outlined.ZoomOut,
                            description = "Zoom out",
                            onClick = { zoomBy(1f / 1.3f) },
                        )
                        Text(
                            text = "${(viewport.zoom * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                        ZoomControlButton(
                            icon = Icons.Outlined.ZoomIn,
                            description = "Zoom in",
                            onClick = { zoomBy(1.3f) },
                        )
                        ZoomControlButton(
                            icon = Icons.Outlined.FitScreen,
                            description = "Reset view",
                            onClick = ::resetViewport,
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(width = 1.dp, height = 20.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                        ZoomControlButton(
                            icon = Icons.Outlined.GridView,
                            description = "Page thumbnails",
                            onClick = onShowPages,
                        )
                        ZoomControlButton(
                            icon = Icons.Outlined.GridOn,
                            description = "Page background",
                            onClick = onShowPageBackground,
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ZoomControlButton(
                            icon = Icons.Outlined.ZoomIn,
                            description = "Zoom in",
                            onClick = { zoomBy(1.3f) },
                        )
                        Text(
                            text = "${(viewport.zoom * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ZoomControlButton(
                            icon = Icons.Outlined.ZoomOut,
                            description = "Zoom out",
                            onClick = { zoomBy(1f / 1.3f) },
                        )
                        ZoomControlButton(
                            icon = Icons.Outlined.FitScreen,
                            description = "Reset view",
                            onClick = ::resetViewport,
                        )
                        Box(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .size(width = 20.dp, height = 1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                        ZoomControlButton(
                            icon = Icons.Outlined.GridView,
                            description = "Page thumbnails",
                            onClick = onShowPages,
                        )
                        ZoomControlButton(
                            icon = Icons.Outlined.GridOn,
                            description = "Page background",
                            onClick = onShowPageBackground,
                        )
                    }
                }
            }
        }

        if (tool == ToolType.TEXT || tool == ToolType.LASSO) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(
                        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                    )
                    .padding(14.dp),
                shape = RoundedCornerShape(12.dp),
                color = hintBackground,
                tonalElevation = 0.dp,
                shadowElevation = 4.dp,
            ) {
                Text(
                    text = if (tool == ToolType.TEXT) {
                        "Tap the page to add a text box"
                    } else {
                        "Circle items to select · drag to move"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = hintText,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLaserTrail(
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

private fun createInkBrush(
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

private fun InkApiStroke.toNoteCraftStroke(
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

@Composable
private fun ZoomControlButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLassoPreview(
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
