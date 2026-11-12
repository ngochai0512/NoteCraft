package com.notecraft.ui.editor

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notecraft.model.Note
import com.notecraft.model.Page
import com.notecraft.model.PersistentTools
import com.notecraft.model.StickyNote
import com.notecraft.model.TextItem
import com.notecraft.model.ToolType
import com.notecraft.ui.NoteCraftViewModel
import com.notecraft.ui.components.ConfirmDialog
import com.notecraft.ui.components.ErrorBanner
import com.notecraft.ui.components.CoverToneDialog
import com.notecraft.ui.components.TextEntryDialog
import com.notecraft.ui.theme.HighlighterAmber
import com.notecraft.ui.theme.MintGreen
import com.notecraft.util.PdfExporter
import com.notecraft.util.PageSharer
import kotlinx.coroutines.launch

private sealed interface TextDialogTarget {
    data class New(val point: Offset, val pageIndex: Int) : TextDialogTarget
    data class Edit(val item: TextItem) : TextDialogTarget
}

private sealed interface StickyDialogTarget {
    data class New(val point: Offset, val pageIndex: Int) : StickyDialogTarget
    data class Edit(val note: StickyNote) : StickyDialogTarget
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
    var stickyDialogTarget by remember { mutableStateOf<StickyDialogTarget?>(null) }
    var showPageDrawer by remember { mutableStateOf(false) }
    var expandedTool by remember { mutableStateOf<ToolType?>(null) }
    var showToolMenu by remember { mutableStateOf(false) }
    var exportNote by remember { mutableStateOf<Note?>(null) }
    var exportPages by remember { mutableStateOf<List<Page>?>(null) }
    var showPageRangeDialog by remember { mutableStateOf(false) }
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
    val pageExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        val pages = exportPages
        if (uri != null && pages != null) {
            Toast.makeText(context, "Exporting pages…", Toast.LENGTH_SHORT).show()
            scope.launch {
                runCatching { PdfExporter.exportPages(context, uri, pages) }
                    .onSuccess { Toast.makeText(context, "Pages exported", Toast.LENGTH_SHORT).show() }
                    .onFailure { Toast.makeText(context, "Could not export pages", Toast.LENGTH_SHORT).show() }
            }
        }
        exportPages = null
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val compactHeight = configuration.screenHeightDp < 480
    val compactTools = compactHeight || isLandscape
    val wideTools = configuration.screenWidthDp >= 600
    var pageScrollRequest by remember { mutableIntStateOf(0) }
    val requestPageScroll: () -> Unit = { pageScrollRequest++ }

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
        showPageRangeDialog ||
        showColorPicker ||
        showDeleteNoteDialog ||
        showCoverDialog ||
        textDialogTarget != null ||
        stickyDialogTarget != null ||
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
                                    requestPageScroll()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicate page") },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.duplicatePage()
                                    requestPageScroll()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Move page up") },
                                leadingIcon = { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null) },
                                enabled = editor.pageIndex > 0,
                                onClick = {
                                    showMenu = false
                                    viewModel.movePage(-1)
                                    requestPageScroll()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Move page down") },
                                leadingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null) },
                                enabled = editor.pageIndex < editor.note.pageCount - 1,
                                onClick = {
                                    showMenu = false
                                    viewModel.movePage(1)
                                    requestPageScroll()
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
                                    requestPageScroll()
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Export current page") },
                                leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    exportPages = listOf(editor.page)
                                    pageExportLauncher.launch(
                                        PdfExporter.pageFileName(editor.note.title, editor.pageIndex + 1),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Export pages…") },
                                leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showPageRangeDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Share current page") },
                                leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    scope.launch {
                                        PageSharer.sharePage(
                                            context = context,
                                            page = editor.page,
                                            noteTitle = editor.note.title,
                                            pageNumber = editor.pageIndex + 1,
                                        ).onFailure {
                                            Toast.makeText(
                                                context,
                                                "Could not share page",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                },
                            )
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
                    onStickyFontSizeChange = viewModel::setStickyFontSize,
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
            viewModel.saveError?.let { message ->
                ErrorBanner(
                    message = message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    actionLabel = "Retry",
                    onAction = viewModel::retrySave,
                    onDismiss = viewModel::dismissSaveError,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                DrawingCanvas(
                    pages = editor.note.pages,
                    pageIndex = editor.pageIndex,
                    noteId = editor.note.id,
                    scrollRequest = pageScrollRequest,
                    tool = editor.tool,
                    color = editor.color,
                    width = editor.width,
                    shapeType = editor.shapeType,
                    stylusOnly = viewModel.settings.stylusOnlyMode,
                    selectedStrokeIds = editor.selectedStrokeIds,
                    selectedTextIds = editor.selectedTextIds,
                    selectionPageIndex = editor.selectionPageIndex,
                    onVisiblePageChanged = { index -> viewModel.goToPage(index) },
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
                    onAddSticky = { point, targetPage ->
                        stickyDialogTarget = StickyDialogTarget.New(point, targetPage)
                    },
                    onEditSticky = { note -> stickyDialogTarget = StickyDialogTarget.Edit(note) },
                    onStickyMoveStart = viewModel::beginStickyMove,
                    onStickyMove = { id, delta -> viewModel.moveSticky(id, delta.x, delta.y) },
                    onStickyMoveEnd = viewModel::endStickyMove,
                    onShowPages = { showPageDrawer = true },
                    onShowPageBackground = { showBackgroundDialog = true },
                    modifier = Modifier.fillMaxSize(),
                )
                PageIndicator(
                    pageIndex = editor.pageIndex,
                    pageCount = editor.note.pageCount,
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
                    requestPageScroll()
                    showPageDrawer = false
                },
                onAdd = {
                    viewModel.addPage()
                    requestPageScroll()
                    showPageDrawer = false
                },
                onToggleBookmark = { index -> viewModel.togglePageBookmark(index) },
                onReorder = { from, to ->
                    viewModel.movePageTo(from, to)
                    requestPageScroll()
                },
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
                formatting = editor.textFormatting,
                onFormattingChange = viewModel::setTextFormatting,
                onDismiss = { textDialogTarget = null },
                onConfirm = { text ->
                    viewModel.addText(
                        target.point.x,
                        target.point.y,
                        text,
                        editor.color,
                        editor.width,
                        alignment = editor.textAlignment,
                        formatting = editor.textFormatting,
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
                formatting = target.item.formatting,
                onFormattingChange = { formatting ->
                    textDialogTarget = TextDialogTarget.Edit(target.item.copy(formatting = formatting))
                },
                onDismiss = { textDialogTarget = null },
                onConfirm = { text ->
                    viewModel.updateText(
                        target.item.id,
                        text,
                        target.item.alignment,
                        target.item.formatting,
                    )
                    textDialogTarget = null
                },
            )
        }

        null -> Unit
    }

    when (val target = stickyDialogTarget) {
        is StickyDialogTarget.New -> {
            TextEntryDialog(
                title = "Add sticky note",
                initialValue = "",
                label = "Note",
                confirmLabel = "Add",
                allowEmpty = false,
                minLines = 3,
                maxLines = 8,
                supportingText = "Drag the note with the Sticky tool to move it. The eraser removes it whole.",
                onDismiss = { stickyDialogTarget = null },
                onConfirm = { text ->
                    viewModel.addSticky(
                        x = target.point.x,
                        y = target.point.y,
                        text = text,
                        color = editor.color,
                        width = editor.width,
                        fontSize = editor.stickyFontSize,
                        pageIndexOverride = target.pageIndex,
                    )
                    stickyDialogTarget = null
                },
            )
        }

        is StickyDialogTarget.Edit -> {
            TextEntryDialog(
                title = "Edit sticky note",
                initialValue = target.note.text,
                label = "Note",
                allowEmpty = true,
                minLines = 3,
                maxLines = 8,
                supportingText = "Save an empty note to remove it.",
                onDismiss = { stickyDialogTarget = null },
                onConfirm = { text ->
                    viewModel.updateSticky(target.note.id, text)
                    stickyDialogTarget = null
                },
            )
        }

        null -> Unit
    }

    if (showPageRangeDialog) {
        PageRangeDialog(
            pageCount = editor.note.pageCount,
            onDismiss = { showPageRangeDialog = false },
            onConfirm = { range ->
                exportPages = editor.note.pages.subList(range.first - 1, range.last)
                showPageRangeDialog = false
                pageExportLauncher.launch(
                    PdfExporter.rangeFileName(editor.note.title, range.first, range.last),
                )
            },
        )
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
