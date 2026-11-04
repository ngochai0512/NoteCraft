package com.notecraft.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
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
import com.notecraft.util.hitTest
import com.notecraft.util.selectInsidePolygon
import com.notecraft.util.selectionBounds
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private data class CanvasTransform(val scale: Float, val origin: Offset)

private class CanvasViewport {
    var zoom by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
}

private enum class GestureMode { NONE, DRAW, SHAPE, ERASE, TEXT, LASSO, MOVE }

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
    val paddingPx = with(density) { 22.dp.toPx() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val viewport = remember(page.id) { CanvasViewport() }
    var previewStroke by remember { mutableStateOf<InkStroke?>(null) }
    var lassoPreview by remember { mutableStateOf<List<Pt>>(emptyList()) }

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
    }

    fun baseScale(size: IntSize): Float {
        if (size.width == 0 || size.height == 0) return 1f
        val availableWidth = (size.width - paddingPx * 2).coerceAtLeast(1f)
        val availableHeight = (size.height - paddingPx * 2).coerceAtLeast(1f)
        return min(availableWidth / PAGE_WIDTH, availableHeight / PAGE_HEIGHT)
    }

    fun currentTransform(): CanvasTransform {
        val base = baseScale(canvasSize)
        val scale = base * viewport.zoom
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
        val scaledWidth = PAGE_WIDTH * baseScale(canvasSize) * zoom
        val scaledHeight = PAGE_HEIGHT * baseScale(canvasSize) * zoom
        val maxX = max(0f, (scaledWidth - canvasSize.width) / 2f + 24f)
        val maxY = max(0f, (scaledHeight - canvasSize.height) / 2f + 24f)
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    fun applyTransform(focus: Offset, panChange: Offset, zoomChange: Float) {
        if (canvasSize.width == 0 || canvasSize.height == 0) return
        val transform = currentTransform()
        val previousFocus = focus - panChange
        val focusInPage = (previousFocus - transform.origin) / transform.scale
        val newZoom = (viewport.zoom * zoomChange).coerceIn(1f, 8f)
        val base = baseScale(canvasSize)
        val newScale = base * newZoom
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

    val workspaceColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val hintBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    val hintText = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(workspaceColor)
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
                        val startedInside = isInsidePage(downPage)
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

                        if (startedInside) {
                            when (tool) {
                                ToolType.PEN, ToolType.PENCIL, ToolType.HIGHLIGHTER -> {
                                    mode = GestureMode.DRAW
                                    points.add(Pt(downPage.x, downPage.y, pressureOf(down)))
                                    previewStroke = makeStroke()
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
                                    val inside = isInsidePage(pagePoint)
                                    val transform = currentTransform()
                                    when (mode) {
                                        GestureMode.DRAW, GestureMode.SHAPE -> {
                                            if (inside) {
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

                                        GestureMode.ERASE -> {
                                            if (inside) eraseMove(pagePoint)
                                        }

                                        GestureMode.LASSO -> {
                                            if (inside) {
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
                                    if (!moved && startedInside) {
                                        val hit = currentPage.texts.lastOrNull {
                                            it.hitTest(downPage.x, downPage.y, 8f)
                                        }
                                        if (hit != null) editText(hit) else addText(downPage)
                                    }
                                }

                                GestureMode.NONE -> Unit
                            }
                        }

                        previewStroke = null
                        lassoPreview = emptyList()
                    }
                },
        ) {
            // Workspace dot grid.
            val dotSpacing = 34f
            var x = dotSpacing
            while (x < size.width) {
                var y = dotSpacing
                while (y < size.height) {
                    drawCircle(color = dotColor, radius = 1.1f, center = Offset(x, y))
                    y += dotSpacing
                }
                x += dotSpacing
            }

            val transform = currentTransform()
            if (transform.scale > 0f) {
                drawPagePaperEdge(transform.origin, transform.scale)
                drawPageContent(
                    page = page,
                    scale = transform.scale,
                    origin = transform.origin,
                    selectedStrokeIds = selectedStrokeIds,
                    selectedTextIds = selectedTextIds,
                    drawSelection = true,
                    drawPaper = false,
                )

                previewStroke?.let { stroke ->
                    drawStrokeBody(stroke, transform.scale, transform.origin)
                }

                drawLassoPreview(lassoPreview, transform)

                drawRect(
                    color = Color.Black.copy(alpha = 0.10f),
                    topLeft = transform.origin,
                    size = Size(PAGE_WIDTH * transform.scale, PAGE_HEIGHT * transform.scale),
                    style = DrawStroke(width = 1.2f),
                )
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
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { zoomBy(1.3f) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Outlined.ZoomIn,
                            contentDescription = "Zoom in",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
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
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    IconButton(onClick = ::resetViewport, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Outlined.FitScreen,
                            contentDescription = "Fit page",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
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
                shape = RoundedCornerShape(50),
                color = hintBackground,
                tonalElevation = 1.dp,
                shadowElevation = 1.dp,
            ) {
                Text(
                    text = if (tool == ToolType.TEXT) {
                        "Tap on the page to add text"
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
        color = Color(0xFF5B7CFA).copy(alpha = 0.08f),
    )
    drawPath(
        path = path,
        color = Color(0xFF5B7CFA),
        style = DrawStroke(
            width = 2f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f), 0f),
        ),
    )
}
