package com.notecraft.ui.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.notecraft.model.PAGE_HEIGHT
import com.notecraft.model.PAGE_WIDTH
import com.notecraft.model.Page
import com.notecraft.model.Pt
import com.notecraft.model.ShapeType
import com.notecraft.model.StickyNote
import com.notecraft.model.Stroke as InkStroke
import com.notecraft.model.TextItem
import com.notecraft.model.ToolType
import com.notecraft.model.blankPage
import com.notecraft.model.newId
import com.notecraft.util.approximateBounds
import com.notecraft.util.hitHandle
import com.notecraft.util.hitTest
import com.notecraft.util.selectInsidePolygon
import com.notecraft.util.selectionBounds
import com.notecraft.ui.theme.PaperSurface
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.brush.Brush as InkApiBrush
import androidx.ink.strokes.Stroke as InkApiStroke
import android.util.Log
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private class CanvasViewport {
    var zoom by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
}

private class FlingHandle {
    var job: Job? = null
}

private class InkTargetState(pageIndex: Int) {
    var pageIndex: Int = pageIndex
    var origin: Offset = Offset.Zero
}

private class VisiblePageState(value: Int) {
    var value: Int = value
}

private enum class GestureMode { NONE, DRAW, SHAPE, ERASE, TEXT, TEXT_RESIZE, STICKY, STICKY_MOVE, LASSO, MOVE, LASER }

/** Freehand tools rendered through Jetpack Ink while authoring. */
private val InkToolTypes = setOf(
    ToolType.FOUNTAIN_PEN,
    ToolType.BALL_PEN,
    ToolType.BRUSH_PEN,
    ToolType.HIGHLIGHTER,
)

@Composable
fun DrawingCanvas(
    pages: List<Page>,
    pageIndex: Int,
    noteId: String,
    scrollRequest: Int,
    tool: ToolType,
    color: Long,
    width: Float,
    shapeType: ShapeType,
    stylusOnly: Boolean,
    selectedStrokeIds: Set<String>,
    selectedTextIds: Set<String>,
    selectionPageIndex: Int,
    onVisiblePageChanged: (Int) -> Unit,
    onStrokeCommitted: (InkStroke, Int) -> Unit,
    onEraseStart: () -> Unit,
    onEraseMove: (Offset, Int) -> Unit,
    onEraseEnd: () -> Unit,
    onLassoSelected: (Int, Set<String>, Set<String>) -> Unit,
    onSelectionMoveStart: () -> Unit,
    onSelectionMove: (Offset) -> Unit,
    onSelectionMoveEnd: () -> Unit,
    onAddText: (Offset, Int) -> Unit,
    onEditText: (TextItem) -> Unit,
    onTextResizeStart: () -> Unit,
    onTextResize: (String, Float) -> Unit,
    onTextResizeEnd: () -> Unit,
    onAddSticky: (Offset, Int) -> Unit,
    onEditSticky: (StickyNote) -> Unit,
    onStickyMoveStart: () -> Unit,
    onStickyMove: (String, Offset) -> Unit,
    onStickyMoveEnd: () -> Unit,
    onShowPages: () -> Unit,
    onShowPageBackground: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val compactHeight = configuration.screenHeightDp < 480
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val viewport = remember(noteId) { CanvasViewport() }
    val scope = rememberCoroutineScope()
    val flingHandle = remember { FlingHandle() }
    var previewStroke by remember { mutableStateOf<InkStroke?>(null) }
    var lassoPreview by remember { mutableStateOf<List<Pt>>(emptyList()) }
    var laserPoints by remember { mutableStateOf<List<Pt>>(emptyList()) }
    var laserFadeKey by remember { mutableStateOf(0) }
    var rulerEnabled by remember { mutableStateOf(false) }
    var rulerFraction by remember { mutableFloatStateOf(0.42f) }
    var rulerAngle by remember { mutableFloatStateOf(0f) }
    var gesturePage by remember { mutableIntStateOf(pageIndex) }
    val inkTarget = remember { InkTargetState(pageIndex) }
    val lastVisiblePage = remember(noteId) { VisiblePageState(pageIndex) }

    val currentPages by rememberUpdatedState(pages)
    val visiblePageChanged by rememberUpdatedState(onVisiblePageChanged)
    val currentSelectedStrokes by rememberUpdatedState(selectedStrokeIds)
    val currentSelectedTexts by rememberUpdatedState(selectedTextIds)
    val currentSelectionPage by rememberUpdatedState(selectionPageIndex)
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
    val textResizeStart by rememberUpdatedState(onTextResizeStart)
    val textResize by rememberUpdatedState(onTextResize)
    val textResizeEnd by rememberUpdatedState(onTextResizeEnd)
    val addSticky by rememberUpdatedState(onAddSticky)
    val editSticky by rememberUpdatedState(onEditSticky)
    val stickyMoveStart by rememberUpdatedState(onStickyMoveStart)
    val stickyMove by rememberUpdatedState(onStickyMove)
    val stickyMoveEnd by rememberUpdatedState(onStickyMoveEnd)

    LaunchedEffect(noteId) {
        viewport.zoom = 1f
        viewport.pan = Offset.Zero
    }

    LaunchedEffect(noteId, pages.size, tool) {
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

    fun pageFor(index: Int): Page =
        currentPages.getOrNull(index) ?: currentPages.firstOrNull() ?: blankPage()

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

    fun pageOriginFor(index: Int): Offset {
        val transform = currentTransform()
        return Offset(
            transform.origin.x,
            transform.origin.y + index * (PAGE_HEIGHT + PageGap) * transform.scale,
        )
    }

    fun pageAt(position: Offset): Pair<Int, Offset> {
        val transform = currentTransform()
        if (transform.scale <= 0f) return 0 to Offset.Zero
        val rawX = (position.x - transform.origin.x) / transform.scale
        val rawY = (position.y - transform.origin.y) / transform.scale
        val index = (rawY / (PAGE_HEIGHT + PageGap)).toInt()
            .coerceIn(0, currentPages.lastIndex.coerceAtLeast(0))
        val localY = rawY - index * (PAGE_HEIGHT + PageGap)
        return index to Offset(rawX, localY)
    }

    fun visiblePageIndex(): Int {
        val transform = currentTransform()
        if (transform.scale <= 0f || currentPages.isEmpty()) return 0
        val rawY = (canvasSize.height / 2f - transform.origin.y) / transform.scale
        return (rawY / (PAGE_HEIGHT + PageGap)).toInt()
            .coerceIn(0, currentPages.lastIndex)
    }

    fun notifyVisiblePage() {
        val visible = visiblePageIndex()
        if (visible != lastVisiblePage.value) {
            lastVisiblePage.value = visible
            visiblePageChanged(visible)
        }
    }

    fun rulerScreenY(): Float = canvasSize.height * rulerFraction

    fun rulerPivot(): Offset = Offset(canvasSize.width / 2f, rulerScreenY())

    fun rulerDirection(): Offset {
        val radians = rulerAngle * PI.toFloat() / 180f
        return Offset(cos(radians), sin(radians))
    }

    fun projectToRuler(position: Offset): Offset {
        val pivot = rulerPivot()
        val direction = rulerDirection()
        val projection = (position.x - pivot.x) * direction.x + (position.y - pivot.y) * direction.y
        return pivot + direction * projection
    }

    fun distanceToRuler(position: Offset): Float {
        val projected = projectToRuler(position)
        return hypot(position.x - projected.x, position.y - projected.y)
    }

    fun localPointFor(screenPosition: Offset, targetIndex: Int): Offset {
        val transform = currentTransform()
        if (transform.scale <= 0f) return Offset.Zero
        val point = if (rulerEnabled) projectToRuler(screenPosition) else screenPosition
        val origin = pageOriginFor(targetIndex)
        return Offset(
            (point.x - origin.x) / transform.scale,
            (point.y - origin.y) / transform.scale,
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
        viewport.pan = candidate
        notifyVisiblePage()
    }

    fun zoomBy(factor: Float) {
        flingHandle.job?.cancel()
        if (canvasSize.width == 0 || canvasSize.height == 0) return
        applyTransform(
            focus = Offset(canvasSize.width / 2f, canvasSize.height / 2f),
            panChange = Offset.Zero,
            zoomChange = factor,
        )
    }

    fun resetViewport() {
        flingHandle.job?.cancel()
        viewport.zoom = 1f
        viewport.pan = Offset.Zero
        notifyVisiblePage()
    }

    LaunchedEffect(scrollRequest, noteId, canvasSize) {
        if (canvasSize.height == 0 || currentPages.isEmpty()) {
            return@LaunchedEffect
        }
        flingHandle.job?.cancel()
        val target = pageIndex.coerceIn(0, currentPages.lastIndex)
        val transform = currentTransform()
        if (transform.scale <= 0f) return@LaunchedEffect
        viewport.pan = Offset(
            viewport.pan.x,
            -target * (PAGE_HEIGHT + PageGap) * transform.scale,
        )
        lastVisiblePage.value = target
        visiblePageChanged(target)
    }

    val hintBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    val hintText = MaterialTheme.colorScheme.onSurfaceVariant
    val rulerColor = MaterialTheme.colorScheme.primary
    val inkTransform = currentTransform()
    val inkScale = inkTransform.scale
    // The ruler snaps custom points, and stylus-only mode needs explicit pointer types, so both
    // paths use the custom renderer instead of Ink's wet overlay.
    val useInk = tool in InkToolTypes && !rulerEnabled && !stylusOnly
    val inkBrush = if (useInk) {
        remember(tool, color, width, inkScale) {
            runCatching { createInkBrush(tool, color, width, inkScale) }
                .onFailure { Log.e("NoteCraftInk", "Unable to initialize Ink brush", it) }
                .getOrNull()
        }
    } else {
        null
    }
    val effectiveInk = useInk && inkBrush != null
    val inkTransformModifier = if (effectiveInk) {
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
                .pointerInput(
                    tool,
                    noteId,
                    pages.size,
                    color,
                    width,
                    shapeType,
                    stylusOnly,
                    rulerEnabled,
                    rulerAngle,
                ) {
                    val touchSlop = viewConfiguration.touchSlop
                    val handleTolerance = 22.dp.toPx()

                    fun applyMultiTouch(event: androidx.compose.ui.input.pointer.PointerEvent) {
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = true)
                        if (centroid != Offset.Unspecified && (zoomChange != 1f || panChange != Offset.Zero)) {
                            applyTransform(centroid, panChange, zoomChange)
                        }
                        event.changes.forEach { it.consume() }
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        flingHandle.job?.cancel()
                        flingHandle.job = null
                        val downIsStylus = down.type == PointerType.Stylus
                        val (targetIndex, downPage) = pageAt(down.position)
                        val targetPage = pageFor(targetIndex)
                        gesturePage = targetIndex
                        if (targetIndex != lastVisiblePage.value) {
                            lastVisiblePage.value = targetIndex
                            visiblePageChanged(targetIndex)
                        }

                        if (effectiveInk) {
                            if (!stylusOnly || downIsStylus) {
                                inkTarget.pageIndex = targetIndex
                                inkTarget.origin = pageOriginFor(targetIndex)
                            }
                            // The Ink overlay owns the wet stroke. Wait out the gesture without
                            // consuming so it remains available to InProgressStrokes.
                            var event = awaitPointerEvent()
                            while (event.changes.any { it.pressed }) {
                                event = awaitPointerEvent()
                            }
                            return@awaitEachGesture
                        }

                        var mode = GestureMode.NONE
                        var points = mutableListOf<Pt>()
                        var lassoPoints = mutableListOf<Pt>()
                        var strokeId = newId()
                        var moved = false
                        var transformMode = false
                        var eraseActive = false
                        var selectionMoveActive = false
                        var textResizeActive = false
                        var resizeTextId: String? = null
                        var resizeStartWidth = 0f
                        var resizeStartX = 0f
                        var stickyMoveActive = false
                        var movingStickyId: String? = null
                        var stickyMoved = false
                        var zoomedDuringGesture = false
                        val velocityTracker = VelocityTracker()
                        var rulerSnap = false
                        if (rulerEnabled && distanceToRuler(down.position) <= 64.dp.toPx()) {
                            rulerSnap = true
                        }

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

                        val handleHit = if (tool == ToolType.TEXT) {
                            val transform = currentTransform()
                            val tolerance = if (transform.scale > 0f) handleTolerance / transform.scale else handleTolerance
                            targetPage.texts.lastOrNull { item -> item.hitHandle(downPage.x, downPage.y, tolerance) }
                        } else {
                            null
                        }

                        when (tool) {
                            ToolType.FOUNTAIN_PEN,
                            ToolType.BALL_PEN,
                            ToolType.BRUSH_PEN,
                            ToolType.PENCIL,
                            ToolType.HIGHLIGHTER,
                            ToolType.TAPE,
                            -> {
                                if (!stylusOnly || downIsStylus) {
                                    mode = GestureMode.DRAW
                                    val drawDown = if (rulerSnap) localPointFor(down.position, targetIndex) else downPage
                                    points.add(Pt(drawDown.x, drawDown.y, pressureOf(down)))
                                    previewStroke = makeStroke()
                                }
                            }

                            ToolType.LASER -> {
                                if (!stylusOnly || downIsStylus) {
                                    mode = GestureMode.LASER
                                    points.add(Pt(downPage.x, downPage.y, pressureOf(down)))
                                    laserPoints = points.toList()
                                }
                            }

                            ToolType.SHAPE -> {
                                if (!stylusOnly || downIsStylus) {
                                    mode = GestureMode.SHAPE
                                    val drawDown = if (rulerSnap) localPointFor(down.position, targetIndex) else downPage
                                    points.add(Pt(drawDown.x, drawDown.y, 0.6f))
                                    points.add(Pt(drawDown.x, drawDown.y, 0.6f))
                                    previewStroke = makeStroke()
                                }
                            }

                            ToolType.ERASER -> {
                                if (!stylusOnly || downIsStylus) {
                                    mode = GestureMode.ERASE
                                    eraseActive = true
                                    eraseStart()
                                    eraseMove(downPage, targetIndex)
                                }
                            }

                            ToolType.TEXT -> {
                                if (!stylusOnly || downIsStylus) {
                                    if (handleHit != null) {
                                        mode = GestureMode.TEXT_RESIZE
                                        resizeTextId = handleHit.id
                                        resizeStartWidth = if (handleHit.width > 0f) {
                                            handleHit.width
                                        } else {
                                            handleHit.approximateBounds().width
                                        }
                                        resizeStartX = downPage.x
                                        textResizeActive = true
                                        textResizeStart()
                                    } else {
                                        mode = GestureMode.TEXT
                                    }
                                }
                            }

                            ToolType.STICKY -> {
                                if (!stylusOnly || downIsStylus) {
                                    val hit = targetPage.stickies.lastOrNull {
                                        it.hitTest(downPage.x, downPage.y, 8f)
                                    }
                                    if (hit != null) {
                                        mode = GestureMode.STICKY_MOVE
                                        movingStickyId = hit.id
                                        stickyMoveActive = true
                                        stickyMoveStart()
                                    } else {
                                        mode = GestureMode.STICKY
                                    }
                                }
                            }

                            ToolType.LASSO -> {
                                if (!stylusOnly || downIsStylus) {
                                    val transform = currentTransform()
                                    val selectionBounds = if (targetIndex == currentSelectionPage && transform.scale > 0f) {
                                        selectionBounds(
                                            pageFor(currentSelectionPage),
                                            currentSelectedStrokes,
                                            currentSelectedTexts,
                                        )
                                    } else {
                                        null
                                    }
                                    if (selectionBounds != null && selectionBounds.expand(14f).contains(downPage.x, downPage.y)) {
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

                            val ignoreFingers = stylusOnly && downIsStylus
                            val pressedCount = if (ignoreFingers) {
                                event.changes.count { it.pressed && it.type == PointerType.Stylus }
                            } else {
                                event.changes.count { it.pressed }
                            }
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
                                    if (textResizeActive) {
                                        textResizeEnd()
                                        textResizeActive = false
                                    }
                                    if (stickyMoveActive) {
                                        stickyMoveEnd()
                                        stickyMoveActive = false
                                    }
                                    mode = GestureMode.NONE
                                }
                                val centroid = event.calculateCentroid(useCurrent = true)
                                if (centroid != Offset.Unspecified) {
                                    velocityTracker.addPosition(event.changes.first().uptimeMillis, centroid)
                                }
                                if (event.calculateZoom() != 1f) zoomedDuringGesture = true
                                applyMultiTouch(event)
                            } else {
                                val change = if (ignoreFingers) {
                                    event.changes.firstOrNull { it.type == PointerType.Stylus }
                                } else {
                                    event.changes.firstOrNull()
                                }
                                if (change != null && change.pressed) {
                                    val (_, pagePoint) = pageAt(change.position)
                                    val transform = currentTransform()
                                    when (mode) {
                                        GestureMode.DRAW, GestureMode.SHAPE -> {
                                            val drawPoint = if (rulerSnap) {
                                                localPointFor(change.position, targetIndex)
                                            } else {
                                                pagePoint
                                            }
                                            val last = points.last()
                                            val distance = hypot(drawPoint.x - last.x, drawPoint.y - last.y)
                                            if (distance >= 1.6f) {
                                                val newPoint = Pt(
                                                    drawPoint.x,
                                                    drawPoint.y,
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

                                        GestureMode.LASER -> {
                                            val last = points.last()
                                            if (hypot(pagePoint.x - last.x, pagePoint.y - last.y) >= 1.2f) {
                                                points.add(Pt(pagePoint.x, pagePoint.y, pressureOf(change)))
                                                laserPoints = points.toList()
                                            }
                                        }

                                        GestureMode.ERASE -> eraseMove(pagePoint, targetIndex)

                                        GestureMode.LASSO -> {
                                            val last = lassoPoints.lastOrNull()
                                            if (last == null ||
                                                hypot(pagePoint.x - last.x, pagePoint.y - last.y) >= 2f
                                            ) {
                                                lassoPoints.add(Pt(pagePoint.x, pagePoint.y, 0.5f))
                                                lassoPreview = lassoPoints.toList()
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

                                        GestureMode.TEXT_RESIZE -> {
                                            val id = resizeTextId
                                            if (id != null && transform.scale > 0f) {
                                                val pageX = (change.position.x - pageOriginFor(targetIndex).x) / transform.scale
                                                textResize(id, resizeStartWidth + (pageX - resizeStartX))
                                            }
                                        }

                                        GestureMode.STICKY_MOVE -> {
                                            val id = movingStickyId
                                            val delta = change.position - change.previousPosition
                                            if (id != null && delta != Offset.Zero && transform.scale > 0f) {
                                                stickyMove(
                                                    id,
                                                    Offset(
                                                        delta.x / transform.scale,
                                                        delta.y / transform.scale,
                                                    ),
                                                )
                                                stickyMoved = true
                                            }
                                        }

                                        GestureMode.STICKY -> {
                                            if ((change.position - down.position).getDistance() > touchSlop) {
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
                            val stylusStillDown = !ignoreFingers ||
                                event.changes.any { it.pressed && it.type == PointerType.Stylus }
                            if (event.changes.none { it.pressed } || !stylusStillDown) break
                        }

                        if (transformMode) {
                            val velocity = velocityTracker.calculateVelocity()
                            if (!zoomedDuringGesture && abs(velocity.y) > 500f) {
                                val basePan = viewport.pan
                                flingHandle.job?.cancel()
                                flingHandle.job = scope.launch {
                                    val anim = Animatable(0f)
                                    anim.animateDecay(velocity.y, exponentialDecay()) {
                                        viewport.pan = Offset(basePan.x, basePan.y + value)
                                        notifyVisiblePage()
                                    }
                                }
                            }
                        }

                        if (!transformMode) {
                            when (mode) {
                                GestureMode.DRAW -> makeStroke()?.let { commitStroke(it, targetIndex) }
                                GestureMode.SHAPE -> {
                                    if (points.size >= 2) {
                                        val first = points.first()
                                        val last = points.last()
                                        if (hypot(last.x - first.x, last.y - first.y) >= 5f) {
                                            makeStroke()?.let { commitStroke(it, targetIndex) }
                                        }
                                    }
                                }

                                GestureMode.ERASE -> if (eraseActive) eraseEnd()
                                GestureMode.MOVE -> if (selectionMoveActive) selectionMoveEnd()
                                GestureMode.TEXT_RESIZE -> if (textResizeActive) textResizeEnd()
                                GestureMode.STICKY_MOVE -> {
                                    if (stickyMoveActive) {
                                        if (!stickyMoved) {
                                            val id = movingStickyId
                                            if (id != null) {
                                                targetPage.stickies.firstOrNull { it.id == id }?.let(editSticky)
                                            }
                                        }
                                        stickyMoveEnd()
                                    }
                                }
                                GestureMode.STICKY -> if (!moved) addSticky(downPage, targetIndex)

                                GestureMode.LASSO -> {
                                    if (lassoPoints.size >= 3) {
                                        val (strokeIds, textIds) = selectInsidePolygon(targetPage, lassoPoints)
                                        lassoSelected(targetIndex, strokeIds, textIds)
                                    } else {
                                        lassoSelected(targetIndex, emptySet(), emptySet())
                                    }
                                }

                                GestureMode.TEXT -> {
                                    if (!moved) {
                                        val hit = targetPage.texts.lastOrNull {
                                            it.hitTest(downPage.x, downPage.y, 8f)
                                        }
                                        if (hit != null) editText(hit) else addText(downPage, targetIndex)
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
                val pageHeightPx = PAGE_HEIGHT * transform.scale
                currentPages.forEachIndexed { index, item ->
                    val pageOrigin = pageOriginFor(index)
                    if (pageOrigin.y > size.height || pageOrigin.y + pageHeightPx < 0f) {
                        return@forEachIndexed
                    }
                    drawPageBackground(
                        background = item.background,
                        scale = transform.scale,
                        origin = pageOrigin,
                        left = pageOrigin.x,
                        top = pageOrigin.y,
                        right = pageOrigin.x + PAGE_WIDTH * transform.scale,
                        bottom = pageOrigin.y + pageHeightPx,
                    )
                    drawPageContent(
                        page = item,
                        scale = transform.scale,
                        origin = pageOrigin,
                        selectedStrokeIds = selectedStrokeIds,
                        selectedTextIds = selectedTextIds,
                        drawSelection = true,
                        drawPaper = false,
                        drawBackground = false,
                        clip = true,
                    )
                    if (index < currentPages.lastIndex) {
                        val dividerY = pageOrigin.y + pageHeightPx + (PageGap * transform.scale) / 2f
                        drawLine(
                            color = Color(0xFF94A3B8).copy(alpha = 0.28f),
                            start = Offset(pageOrigin.x, dividerY),
                            end = Offset(pageOrigin.x + PAGE_WIDTH * transform.scale, dividerY),
                            strokeWidth = 1f,
                        )
                    }
                }

                if (tool == ToolType.TEXT) {
                    currentPages.forEachIndexed { index, item ->
                        val pageOrigin = pageOriginFor(index)
                        if (pageOrigin.y > size.height || pageOrigin.y + pageHeightPx < 0f) {
                            return@forEachIndexed
                        }
                        item.texts.forEach { text -> drawTextFrame(text, transform.scale, pageOrigin) }
                    }
                }

                if (tool == ToolType.STICKY) {
                    currentPages.forEachIndexed { index, item ->
                        val pageOrigin = pageOriginFor(index)
                        if (pageOrigin.y > size.height || pageOrigin.y + pageHeightPx < 0f) {
                            return@forEachIndexed
                        }
                        item.stickies.forEach { note -> drawStickyFrame(note, transform.scale, pageOrigin) }
                    }
                }

                val gestureTransform = CanvasTransform(
                    scale = transform.scale,
                    origin = pageOriginFor(gesturePage),
                )
                previewStroke?.let { stroke ->
                    drawStrokeBody(stroke, gestureTransform.scale, gestureTransform.origin)
                }

                drawLaserTrail(laserPoints, gestureTransform)
                drawLassoPreview(lassoPreview, gestureTransform)

                if (rulerEnabled) {
                    drawRulerGuide(
                        pivot = rulerPivot(),
                        angleDegrees = rulerAngle,
                        color = rulerColor,
                    )
                }
            }
        }

        if (inkBrush != null && effectiveInk && inkScale > 0f) {
            InProgressStrokes(
                defaultBrush = inkBrush,
                onStrokesFinished = { strokes ->
                    strokes.forEach { inkStroke ->
                        inkStroke.toNoteCraftStroke(
                            tool = tool,
                            color = color,
                            width = width,
                            scale = inkScale,
                            origin = inkTarget.origin,
                        )?.let { converted -> commitStroke(converted, inkTarget.pageIndex) }
                    }
                },
            )
        }

        if (rulerEnabled) {
            RulerHandle(
                modifier = Modifier
                    .align(Alignment.Center)
                    .windowInsetsPadding(
                        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                    )
                    .offset {
                        val radians = rulerAngle * PI.toFloat() / 180f
                        val radius = (min(canvasSize.width, canvasSize.height) / 2f - 30.dp.toPx())
                            .coerceAtLeast(0f)
                        IntOffset(
                            x = (cos(radians) * radius).roundToInt(),
                            y = (
                                canvasSize.height * rulerFraction - canvasSize.height / 2f +
                                    sin(radians) * radius
                                ).roundToInt(),
                        )
                    },
                onDrag = { dragAmount ->
                    if (canvasSize.height > 0) {
                        rulerFraction = (rulerFraction + dragAmount.y / canvasSize.height)
                            .coerceIn(0.08f, 0.92f)
                    }
                    rulerAngle = (rulerAngle + dragAmount.x * 0.4f).coerceIn(-85f, 85f)
                },
            )
        }

        CanvasZoomDock(
            compactHeight = compactHeight,
            zoomPercent = (viewport.zoom * 100).toInt(),
            rulerEnabled = rulerEnabled,
            onZoomIn = { zoomBy(1.3f) },
            onZoomOut = { zoomBy(1f / 1.3f) },
            onReset = ::resetViewport,
            onShowPages = onShowPages,
            onShowPageBackground = onShowPageBackground,
            onToggleRuler = { rulerEnabled = !rulerEnabled },
            modifier = Modifier.align(Alignment.TopEnd),
        )

        if (tool == ToolType.TEXT || tool == ToolType.LASSO || tool == ToolType.STICKY) {
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
                    text = when (tool) {
                        ToolType.TEXT -> "Tap the page to add a text box · drag its corner to resize"
                        ToolType.STICKY -> "Tap to add a sticky note · drag a note to move it"
                        else -> "Circle items to select · drag to move"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = hintText,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

private const val PageGap = 36f
