package com.notecraft.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notecraft.model.TextItem
import com.notecraft.ui.NoteCraftViewModel
import com.notecraft.ui.components.ConfirmDialog
import com.notecraft.ui.components.TextEntryDialog

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

    BackHandler {
        if (editor.hasSelection) {
            viewModel.clearSelection()
        } else if (textDialogTarget == null) {
            viewModel.closeEditor()
        }
    }

    Scaffold(
        topBar = {
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
                            .padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = editor.note.title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Page ${editor.pageIndex + 1} of ${editor.note.pageCount} · Auto-saved",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::undo,
                        enabled = editor.canUndo,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo")
                    }
                    IconButton(
                        onClick = viewModel::redo,
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
        },
        bottomBar = {
            EditorBottomBar(
                editor = editor,
                onToolSelected = viewModel::setTool,
                onColorSelected = viewModel::setToolColor,
                onWidthChange = viewModel::setToolWidth,
                onShapeSelected = viewModel::setShapeType,
                onOpenColorPicker = { showColorPicker = true },
                onDeleteSelection = viewModel::deleteSelection,
                onDuplicateSelection = viewModel::duplicateSelection,
                onClearSelection = viewModel::clearSelection,
                onClearPage = viewModel::clearPage,
            )
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
                onAddPage = { viewModel.addPage() },
            )
            DrawingCanvas(
                page = editor.page,
                tool = editor.tool,
                color = editor.color,
                width = editor.width,
                shapeType = editor.shapeType,
                selectedStrokeIds = editor.selectedStrokeIds,
                selectedTextIds = editor.selectedTextIds,
                onStrokeCommitted = viewModel::addStroke,
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
    onAddPage: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = pageIndex > 0,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "Previous page",
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = "Page ${pageIndex + 1} / $pageCount",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(
                onClick = onNext,
                enabled = pageIndex < pageCount - 1,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "Next page",
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                // Page dots, capped so a very long note stays readable.
                val visibleCount = pageCount.coerceAtMost(9)
                val start = (pageIndex - visibleCount / 2).coerceIn(0, (pageCount - visibleCount).coerceAtLeast(0))
                repeat(visibleCount) { offset ->
                    val index = start + offset
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = if (index == pageIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.size(if (index == pageIndex) 7.dp else 5.dp),
                    ) {}
                }
            }
            IconButton(onClick = onBackground, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Outlined.GridOn,
                    contentDescription = "Page background",
                    modifier = Modifier.size(19.dp),
                )
            }
            IconButton(onClick = onAddPage, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = "Add page",
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }
}
