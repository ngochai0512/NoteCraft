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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitScreen
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.notecraft.ui.theme.DeskBackdrop
import com.notecraft.util.hitTest
import com.notecraft.util.selectInsidePolygon
import com.notecraft.util.selectionBounds
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay

private data class CanvasTransform(val scale: Float, val origin: Offset)

private class CanvasViewport {
    var zoom by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
}

private enum class GestureMode { NONE, DRAW, SHAPE, ERASE, TEXT, LASSO, MOVE, LASER }

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
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val pagePadding = with(density) { 18.dp.toPx() }
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
        val availableWidth = (size.width - pagePadding * 2f).coerceAtLeast(1f)
        val availableHeight = (size.height - pagePadding * 2f).coerceAtLeast(1f)
        return min(availableWidth / PAGE_WIDTH, availableHeight / PAGE_HEIGHT)
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

    fun isInsidePage(point: Offset): Boolean =
        point.x in 0f..PAGE_WIDTH && point.y in 0f..PAGE_HEIGHT

    fun clampPan(candidate: Offset, zoom: Float): Offset {
        if (canvasSize.width == 0 || canvasSize.height == 0) return candidate
        val scale = baseScale(canvasSize) * zoom
        val maxX = max(0f, (PAGE_WIDTH * scale - canvasSize.width) / 2f + pagePadding)
        val maxY = max(0f, (PAGE_HEIGHT * scale - canvasSize.height) / 2f + pagePadding)
        return Offset(
            candidate.x.coerceIn(-maxX, maxX),
            candidate.y.coerceIn(-maxY, maxY),
        )
    }

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
        viewport.pan = clampPan(candidate, newZoom)
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
    val canvasColor = MaterialTheme.colorScheme.background
    val deskTop = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(canvasColor)
            .onSizeChanged { canvasSize = it },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tool, page.id, color, width, shapeType, selectedStrokeIds, selectedTextIds) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
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
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(deskTop, canvasColor),
                ),
            )

            val transform = currentTransform()
            if (transform.scale > 0f) {
                drawPaperSheet(transform.scale, transform.origin)
                clipRect(
                    left = transform.origin.x,
                    top = transform.origin.y,
                    right = transform.origin.x + PAGE_WIDTH * transform.scale,
                    bottom = transform.origin.y + PAGE_HEIGHT * transform.scale,
                ) {
                    drawPageBackground(page.background, transform.scale, transform.origin)
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
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { zoomBy(1.3f) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Outlined.ZoomIn,
                            contentDescription = "Zoom in",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        text = "${(viewport.zoom * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { zoomBy(1f / 1.3f) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Outlined.ZoomOut,
                            contentDescription = "Zoom out",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = ::resetViewport, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Outlined.FitScreen,
                            contentDescription = "Reset view",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        if (tool == ToolType.TEXT || tool == ToolType.LASSO) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLassoPreview(
    points: List<Pt>,
    transform: CanvasTransform,
) {
    if (points.size < 2) return
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
