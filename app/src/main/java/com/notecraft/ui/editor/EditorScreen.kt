package com.notecraft.ui.editor

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.notecraft.model.Note
import com.notecraft.model.PAGE_HEIGHT
import com.notecraft.model.PAGE_WIDTH
import com.notecraft.model.Page
import com.notecraft.model.PersistentTools
import com.notecraft.model.TextItem
import com.notecraft.model.ToolType
import com.notecraft.ui.NoteCraftViewModel
import com.notecraft.ui.components.ConfirmDialog
import com.notecraft.ui.components.CoverToneDialog
import com.notecraft.ui.components.NoteThumbnail
import com.notecraft.ui.components.TextEntryDialog
import com.notecraft.ui.theme.HighlighterAmber
import com.notecraft.ui.theme.MintGreen
import com.notecraft.util.PdfExporter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private sealed interface TextDialogTarget {
    data class New(val point: Offset, val pageIndex: Int) : TextDialogTarget
    data class Edit(val item: TextItem) : TextDialogTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: NoteCraftViewModel) {
    val editor = viewModel.editor ?: return

    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showBackgroundDialog by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showDeleteNoteDialog by remember { mutableStateOf(false) }
    var showCoverDialog by remember { mutableStateOf(false) }
    var textDialogTarget by remember { mutableStateOf<TextDialogTarget?>(null) }
    var showPageDrawer by remember { mutableStateOf(false) }
    var expandedTool by remember { mutableStateOf<ToolType?>(null) }
    var showToolMenu by remember { mutableStateOf(false) }
    var exportNote by remember { mutableStateOf<Note?>(null) }
    val pageSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        val note = exportNote
        if (uri != null && note != null) {
            Toast.makeText(context, "Exporting PDF…", Toast.LENGTH_SHORT).show()
            scope.launch {
                runCatching { PdfExporter.export(context, uri, note) }
                    .onSuccess { Toast.makeText(context, "PDF exported", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(context, "Could not export PDF", Toast.LENGTH_SHORT).show() }
            }
        }
        exportNote = null
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val compactHeight = configuration.screenHeightDp < 480
    val compactTools = compactHeight || isLandscape
    val wideTools = configuration.screenWidthDp >= 600
    val spread = isTablet && isLandscape
    val pageStep = if (spread) 2 else 1

    LaunchedEffect(editor.pageIndex) {
        expandedTool = null
    }

    val selectTool: (ToolType) -> Unit = { tool ->
        val wasSelected = editor.tool == tool
        val wasExpanded = expandedTool == tool
        viewModel.setTool(tool)
        expandedTool = if (wasSelected && wasExpanded) null else tool
    }

    val dialogOpen = showRenameDialog ||
        showBackgroundDialog ||
        showColorPicker ||
        showDeleteNoteDialog ||
        showCoverDialog ||
        textDialogTarget != null ||
        showPageDrawer ||
        showMenu ||
        showToolMenu ||
        expandedTool != null

    BackHandler(enabled = !dialogOpen) {
        if (editor.hasSelection) {
            viewModel.clearSelection()
        } else {
            viewModel.closeEditor()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    windowInsets = WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (editor.hasSelection) viewModel.clearSelection() else viewModel.closeEditor()
                            },
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        Column(
                            modifier = Modifier
                                .clickable { showRenameDialog = true }
                                .padding(vertical = 1.dp),
                        ) {
                            Text(
                                text = editor.note.title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                if (viewModel.settings.showAutosaveIndicator) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(MintGreen),
                                    )
                                }
                                Text(
                                    text = if (viewModel.settings.showAutosaveIndicator) {
                                        "SAVED · ${editor.note.pageCount} " +
                                            if (editor.note.pageCount == 1) "PAGE" else "PAGES"
                                    } else {
                                        "${editor.note.pageCount} " +
                                            if (editor.note.pageCount == 1) "PAGE" else "PAGES"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (editor.canUndo) {
                                    viewModel.undo()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            enabled = editor.canUndo,
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo")
                        }
                        IconButton(
                            onClick = {
                                if (editor.canRedo) {
                                    viewModel.redo()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            enabled = editor.canRedo,
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Redo")
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename note") },
                                leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showRenameDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Cover color") },
                                leadingIcon = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showCoverDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Page background") },
                                leadingIcon = { Icon(Icons.Outlined.GridOn, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showBackgroundDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (editor.page.bookmarked) "Remove bookmark" else "Bookmark page",
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (editor.page.bookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                        contentDescription = null,
                                        tint = if (editor.page.bookmarked) {
                                            HighlighterAmber
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    viewModel.togglePageBookmark()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Add page") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.addPage()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate page") },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.duplicatePage()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Move page left") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = null) },
                                enabled = editor.pageIndex > 0,
                                onClick = {
                                    showMenu = false
                                    viewModel.movePage(-1)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Move page right") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null) },
                                enabled = editor.pageIndex < editor.note.pageCount - 1,
                                onClick = {
                                    showMenu = false
                                    viewModel.movePage(1)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Clear page") },
                                leadingIcon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.clearPage()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete page") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                enabled = editor.note.pageCount > 1,
                                onClick = {
                                    showMenu = false
                                    viewModel.deletePage()
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Export PDF") },
                                leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    exportNote = editor.note
                                    exportLauncher.launch(PdfExporter.fileName(editor.note.title))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.openSettings()
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Delete note",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.DeleteForever,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showDeleteNoteDialog = true
                                },
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
                EditorToolPanel(
                    editor = editor,
                    expandedTool = expandedTool,
                    compact = compactTools,
                    wide = wideTools,
                    onToolSelected = selectTool,
                    onDismissOptions = { expandedTool = null },
                    onColorSelected = viewModel::setToolColor,
                    onWidthChange = viewModel::setToolWidth,
                    onShapeSelected = viewModel::setShapeType,
                    onEraserModeSelected = viewModel::setEraserMode,
                    onTextAlignmentSelected = viewModel::setTextAlignment,
                    onOpenColorPicker = {
                        expandedTool = null
                        showColorPicker = true
                    },
                    onDeleteSelection = viewModel::deleteSelection,
                    onDuplicateSelection = viewModel::duplicateSelection,
                    onClearSelection = viewModel::clearSelection,
                    onClearPage = viewModel::clearPage,
                    onMoreMenuVisibilityChanged = { showToolMenu = it },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (!isLandscape) {
                PageNavigator(
                    pageIndex = editor.pageIndex,
                    pageCount = editor.note.pageCount,
                    onPrevious = { viewModel.goToPage(editor.pageIndex - pageStep) },
                    onNext = { viewModel.goToPage(editor.pageIndex + pageStep) },
                    modifier = Modifier.padding(
                        horizontal = if (isTablet) 16.dp else 12.dp,
                        vertical = 8.dp,
                    ),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                DrawingCanvas(
                    page = editor.page,
                    secondPage = if (spread) editor.note.pages.getOrNull(editor.pageIndex + 1) else null,
                    pageIndex = editor.pageIndex,
                    tool = editor.tool,
                    color = editor.color,
                    width = editor.width,
                    shapeType = editor.shapeType,
                    stylusOnly = viewModel.settings.stylusOnlyMode,
                    selectedStrokeIds = editor.selectedStrokeIds,
                    selectedTextIds = editor.selectedTextIds,
                    selectionPageIndex = editor.selectionPageIndex,
                    onStrokeCommitted = { stroke, targetPage ->
                        if (stroke.tool in PersistentTools) viewModel.addStroke(stroke, targetPage)
                    },
                    onEraseStart = viewModel::beginErase,
                    onEraseMove = { point, targetPage -> viewModel.eraseAt(point.x, point.y, targetPage) },
                    onEraseEnd = viewModel::endErase,
                    onLassoSelected = { targetPage, strokes, texts ->
                        viewModel.setSelection(targetPage, strokes, texts)
                    },
                    onSelectionMoveStart = viewModel::beginSelectionMove,
                    onSelectionMove = { delta -> viewModel.moveSelection(delta.x, delta.y) },
                    onSelectionMoveEnd = viewModel::endSelectionMove,
                    onAddText = { point, targetPage ->
                        textDialogTarget = TextDialogTarget.New(point, targetPage)
                    },
                    onEditText = { item -> textDialogTarget = TextDialogTarget.Edit(item) },
                    onTextResizeStart = viewModel::beginTextResize,
                    onTextResize = { id, newWidth -> viewModel.resizeText(id, newWidth) },
                    onTextResizeEnd = viewModel::endTextResize,
                    onShowPages = { showPageDrawer = true },
                    onShowPageBackground = { showBackgroundDialog = true },
                    modifier = Modifier.fillMaxSize(),
                )
                if (isLandscape) {
                    PageNavigator(
                        pageIndex = editor.pageIndex,
                        pageCount = editor.note.pageCount,
                        onPrevious = { viewModel.goToPage(editor.pageIndex - pageStep) },
                        onNext = { viewModel.goToPage(editor.pageIndex + pageStep) },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .windowInsetsPadding(
                                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                            )
                            .padding(12.dp),
                    )
                }
            }
        }
    }

    if (showPageDrawer) {
        ModalBottomSheet(
            onDismissRequest = { showPageDrawer = false },
            sheetState = pageSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = {
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                )
            },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 4.dp)
                        .size(width = 34.dp, height = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            },
        ) {
            PageDrawer(
                pages = editor.note.pages,
                currentIndex = editor.pageIndex,
                onSelect = { index ->
                    viewModel.goToPage(index)
                    showPageDrawer = false
                },
                onAdd = {
                    viewModel.addPage()
                    showPageDrawer = false
                },
                onToggleBookmark = { index -> viewModel.togglePageBookmark(index) },
                onReorder = { from, to -> viewModel.movePageTo(from, to) },
            )
        }
    }

    if (showRenameDialog) {
        TextEntryDialog(
            title = "Rename note",
            initialValue = editor.note.title,
            label = "Title",
            onDismiss = { showRenameDialog = false },
            onConfirm = { title ->
                viewModel.renameCurrentNote(title)
                showRenameDialog = false
            },
        )
    }

    if (showBackgroundDialog) {
        PageBackgroundDialog(
            current = editor.page.background,
            onDismiss = { showBackgroundDialog = false },
            onSelect = { background ->
                viewModel.setPageBackground(background)
                showBackgroundDialog = false
            },
        )
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = editor.color,
            onDismiss = { showColorPicker = false },
            onConfirm = { color ->
                viewModel.setToolColor(color)
                showColorPicker = false
            },
        )
    }

    if (showCoverDialog) {
        CoverToneDialog(
            noteId = editor.note.id,
            currentTone = editor.note.coverTone,
            onDismiss = { showCoverDialog = false },
            onSelect = { tone ->
                viewModel.setNoteCover(editor.note.id, tone)
                showCoverDialog = false
            },
        )
    }

    when (val target = textDialogTarget) {
        is TextDialogTarget.New -> {
            TextEntryDialog(
                title = "Add text",
                initialValue = "",
                label = "Text",
                confirmLabel = "Add",
                allowEmpty = false,
                minLines = 2,
                maxLines = 6,
                supportingText = "Text wraps in its box. Tap it later with the Text tool to edit or drag the corner to resize.",
                alignment = editor.textAlignment,
                onAlignmentChange = viewModel::setTextAlignment,
                onDismiss = { textDialogTarget = null },
                onConfirm = { text ->
                    viewModel.addText(
                        target.point.x,
                        target.point.y,
                        text,
                        editor.color,
                        editor.width,
                        alignment = editor.textAlignment,
                        pageIndexOverride = target.pageIndex,
                    )
                    textDialogTarget = null
                },
            )
        }

        is TextDialogTarget.Edit -> {
            TextEntryDialog(
                title = "Edit text",
                initialValue = target.item.text,
                label = "Text",
                allowEmpty = false,
                minLines = 2,
                maxLines = 6,
                supportingText = "Use the eraser to remove this text, or drag the box corner to resize it.",
                alignment = target.item.alignment,
                onAlignmentChange = { alignment ->
                    textDialogTarget = TextDialogTarget.Edit(target.item.copy(alignment = alignment))
                },
                onDismiss = { textDialogTarget = null },
                onConfirm = { text ->
                    viewModel.updateText(target.item.id, text, target.item.alignment)
                    textDialogTarget = null
                },
            )
        }

        null -> Unit
    }

    if (showDeleteNoteDialog) {
        ConfirmDialog(
            title = "Delete note?",
            message = "\"${editor.note.title}\" and all of its pages will be permanently deleted.",
            onDismiss = { showDeleteNoteDialog = false },
            onConfirm = { viewModel.deleteNote(editor.note.id) },
        )
    }
}

@Composable
private fun PageNavigator(
    pageIndex: Int,
    pageCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(16.dp),
        ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .height(42.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = pageIndex > 0,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "Previous page",
                    modifier = Modifier.size(21.dp),
                )
            }
            IconButton(
                onClick = onNext,
                enabled = pageIndex < pageCount - 1,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "Next page",
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }
}

@Composable
private fun PageDrawer(
    pages: List<Page>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onToggleBookmark: (Int) -> Unit,
    onReorder: (Int, Int) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val columns = when {
        isTablet -> minOf(6, maxOf(3, configuration.screenWidthDp / 120))
        isLandscape -> 4
        else -> 3
    }
    val maxGridHeight = if (configuration.screenHeightDp < 480) 220.dp else 440.dp
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Pages",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${pages.size} ${if (pages.size == 1) "PAGE" else "PAGES"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tap to open · long-press and drag to reorder",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val gap = 10.dp
            val cellWidth = (maxWidth - gap * (columns - 1).toFloat()) / columns.toFloat()
            val tileHeight = cellWidth * (PAGE_HEIGHT / PAGE_WIDTH) + 18.dp
            val rows = (pages.size + 1 + columns - 1) / columns
            val gridHeight = minOf(
                tileHeight * rows.toFloat() + gap * (rows - 1).toFloat(),
                maxGridHeight,
            )
            val density = LocalDensity.current
            val cellWidthPx = with(density) { cellWidth.toPx() }
            val rowHeightPx = with(density) { (tileHeight + 12.dp).toPx() }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(gridHeight),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(pages) { index, page ->
                    PageTile(
                        page = page,
                        index = index,
                        current = index == currentIndex,
                        dragging = draggingIndex == index,
                        dragOffset = if (draggingIndex == index) dragOffset else Offset.Zero,
                        onClick = { onSelect(index) },
                        onToggleBookmark = { onToggleBookmark(index) },
                        onDragStart = {
                            draggingIndex = index
                            dragOffset = Offset.Zero
                        },
                        onDrag = { amount -> dragOffset += amount },
                        onDragEnd = {
                            val from = draggingIndex
                            if (from != null) {
                                val target = reorderTarget(
                                    index = from,
                                    dragOffset = dragOffset,
                                    columns = columns,
                                    cellWidthPx = cellWidthPx,
                                    rowHeightPx = rowHeightPx,
                                    lastIndex = pages.lastIndex,
                                )
                                if (target != from) onReorder(from, target)
                            }
                            draggingIndex = null
                            dragOffset = Offset.Zero
                        },
                        onDragCancel = {
                            draggingIndex = null
                            dragOffset = Offset.Zero
                        },
                    )
                }
                item {
                    AddPageTile(onClick = onAdd)
                }
            }
        }
    }
}

private fun reorderTarget(
    index: Int,
    dragOffset: Offset,
    columns: Int,
    cellWidthPx: Float,
    rowHeightPx: Float,
    lastIndex: Int,
): Int {
    if (cellWidthPx <= 0f || rowHeightPx <= 0f || columns <= 0 || lastIndex < 0) return index
    val startColumn = index % columns
    val startRow = index / columns
    val column = (startColumn + (dragOffset.x / cellWidthPx).roundToInt())
        .coerceIn(0, columns - 1)
    val row = (startRow + (dragOffset.y / rowHeightPx).roundToInt()).coerceAtLeast(0)
    return (row * columns + column).coerceIn(0, lastIndex)
}

@Composable
private fun PageTile(
    page: Page,
    index: Int,
    current: Boolean,
    dragging: Boolean,
    dragOffset: Offset,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationX = dragOffset.x
                translationY = dragOffset.y
                val dragScale = if (dragging) 1.05f else 1f
                scaleX = dragScale
                scaleY = dragScale
                shadowElevation = if (dragging) 18.dp.toPx() else 0f
                alpha = if (dragging) 0.94f else 1f
            }
            .pointerInput(page.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                )
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PAGE_WIDTH / PAGE_HEIGHT)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = if (current) 2.dp else 1.dp,
                    color = if (current) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = RoundedCornerShape(6.dp),
                ),
        ) {
            NoteThumbnail(
                page = page,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                    .clickable(onClick = onToggleBookmark),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (page.bookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (page.bookmarked) "Remove bookmark" else "Bookmark page",
                    tint = if (page.bookmarked) {
                        HighlighterAmber
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    },
                    modifier = Modifier.size(16.dp),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.80f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.DragIndicator,
                    contentDescription = "Drag to reorder page",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "PAGE ${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = if (current) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun AddPageTile(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PAGE_WIDTH / PAGE_HEIGHT)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "Add page",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "ADD PAGE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}
