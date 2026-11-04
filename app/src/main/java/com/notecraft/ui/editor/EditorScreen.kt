package com.notecraft.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notecraft.model.PAGE_HEIGHT
import com.notecraft.model.PAGE_WIDTH
import com.notecraft.model.Page
import com.notecraft.model.PersistentTools
import com.notecraft.model.TextItem
import com.notecraft.model.ToolType
import com.notecraft.ui.NoteCraftViewModel
import com.notecraft.ui.components.ConfirmDialog
import com.notecraft.ui.components.NoteThumbnail
import com.notecraft.ui.components.TextEntryDialog
import com.notecraft.ui.theme.MintGreen

private sealed interface TextDialogTarget {
    data class New(val point: Offset) : TextDialogTarget
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
    var textDialogTarget by remember { mutableStateOf<TextDialogTarget?>(null) }
    var showPageDrawer by remember { mutableStateOf(false) }
    var expandedTool by remember { mutableStateOf<ToolType?>(null) }
    var showToolMenu by remember { mutableStateOf(false) }
    val pageSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current

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
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(MintGreen),
                                )
                                Text(
                                    text = "PAGE ${editor.pageIndex + 1} / ${editor.note.pageCount} · SAVED",
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
                                text = { Text("Page background") },
                                leadingIcon = { Icon(Icons.Outlined.GridOn, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showBackgroundDialog = true
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
                    onToolSelected = selectTool,
                    onDismissOptions = { expandedTool = null },
                    onColorSelected = viewModel::setToolColor,
                    onWidthChange = viewModel::setToolWidth,
                    onShapeSelected = viewModel::setShapeType,
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
            PageNavigator(
                pageIndex = editor.pageIndex,
                pageCount = editor.note.pageCount,
                onPrevious = { viewModel.goToPage(editor.pageIndex - 1) },
                onNext = { viewModel.goToPage(editor.pageIndex + 1) },
                onBackground = { showBackgroundDialog = true },
                onPages = { showPageDrawer = true },
            )
            DrawingCanvas(
                page = editor.page,
                tool = editor.tool,
                color = editor.color,
                width = editor.width,
                shapeType = editor.shapeType,
                selectedStrokeIds = editor.selectedStrokeIds,
                selectedTextIds = editor.selectedTextIds,
                onStrokeCommitted = { stroke ->
                    if (stroke.tool in PersistentTools) viewModel.addStroke(stroke)
                },
                onEraseStart = viewModel::beginErase,
                onEraseMove = { point -> viewModel.eraseAt(point.x, point.y) },
                onEraseEnd = viewModel::endErase,
                onLassoSelected = viewModel::setSelection,
                onSelectionMoveStart = viewModel::beginSelectionMove,
                onSelectionMove = { delta -> viewModel.moveSelection(delta.x, delta.y) },
                onSelectionMoveEnd = viewModel::endSelectionMove,
                onAddText = { point -> textDialogTarget = TextDialogTarget.New(point) },
                onEditText = { item -> textDialogTarget = TextDialogTarget.Edit(item) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }

    if (showPageDrawer) {
        ModalBottomSheet(
            onDismissRequest = { showPageDrawer = false },
            sheetState = pageSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
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
                supportingText = "Tap the text later with the Text tool to edit it.",
                onDismiss = { textDialogTarget = null },
                onConfirm = { text ->
                    viewModel.addText(target.point.x, target.point.y, text, editor.color, editor.width)
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
                supportingText = "Use the eraser to remove this text.",
                onDismiss = { textDialogTarget = null },
                onConfirm = { text ->
                    viewModel.updateText(target.item.id, text)
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
    onBackground: () -> Unit,
    onPages: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .border(
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
                .fillMaxWidth()
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
            Text(
                text = "PAGE ${pageIndex + 1} / $pageCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            Spacer(Modifier.width(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                // Page dots, capped so a very long note stays readable.
                val visibleCount = pageCount.coerceAtMost(5)
                val start = (pageIndex - visibleCount / 2).coerceIn(0, (pageCount - visibleCount).coerceAtLeast(0))
                repeat(visibleCount) { offset ->
                    val index = start + offset
                    Box(
                        modifier = Modifier
                            .size(if (index == pageIndex) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pageIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                },
                            ),
                    )
                }
            }
            IconButton(onClick = onPages, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.GridView,
                    contentDescription = "Page thumbnails",
                    modifier = Modifier.size(19.dp),
                )
            }
            IconButton(onClick = onBackground, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.GridOn,
                    contentDescription = "Page background",
                    modifier = Modifier.size(19.dp),
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
) {
    val rows = (pages.size + 1 + 2) / 3

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
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val gap = 10.dp
            val cellWidth = (maxWidth - gap * 2f) / 3f
            val tileHeight = cellWidth * (PAGE_HEIGHT / PAGE_WIDTH) + 18.dp
            val gridHeight = minOf(
                tileHeight * rows.toFloat() + gap * (rows - 1).toFloat(),
                440.dp,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
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
                        onClick = { onSelect(index) },
                    )
                }
                item {
                    AddPageTile(onClick = onAdd)
                }
            }
        }
    }
}

@Composable
private fun PageTile(
    page: Page,
    index: Int,
    current: Boolean,
    onClick: () -> Unit,
) {
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
