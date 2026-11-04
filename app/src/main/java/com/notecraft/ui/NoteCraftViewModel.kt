package com.notecraft.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notecraft.data.NoteRepository
import com.notecraft.model.DefaultToolColors
import com.notecraft.model.DefaultToolWidths
import com.notecraft.model.Note
import com.notecraft.model.Page
import com.notecraft.model.PageBackground
import com.notecraft.model.ShapeType
import com.notecraft.model.Stroke
import com.notecraft.model.TextItem
import com.notecraft.model.ToolType
import com.notecraft.model.blankPage
import com.notecraft.model.duplicate
import com.notecraft.model.newId
import com.notecraft.model.newNote
import com.notecraft.util.hitTest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class LibraryState(
    val notes: List<Note> = emptyList(),
    val loading: Boolean = true,
)

data class EditorState(
    val note: Note,
    val pageIndex: Int = 0,
    val tool: ToolType = ToolType.PEN,
    val toolColors: Map<ToolType, Long> = DefaultToolColors,
    val toolWidths: Map<ToolType, Float> = DefaultToolWidths,
    val shapeType: ShapeType = ShapeType.LINE,
    val selectedStrokeIds: Set<String> = emptySet(),
    val selectedTextIds: Set<String> = emptySet(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
) {
    val page: Page get() = note.pages[pageIndex.coerceIn(0, note.pages.lastIndex)]
    val color: Long get() = toolColors[tool] ?: 0xFF23222BL
    val width: Float get() = toolWidths[tool] ?: 4f
    val hasSelection: Boolean get() = selectedStrokeIds.isNotEmpty() || selectedTextIds.isNotEmpty()
    val selectionCount: Int get() = selectedStrokeIds.size + selectedTextIds.size
}

class NoteCraftViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NoteRepository(application)
    private val preferences = application.getSharedPreferences("notecraft_tool_prefs", Context.MODE_PRIVATE)
    private var savedToolColors: Map<ToolType, Long> = DefaultToolColors.mapValues { (tool, default) ->
        preferences.getLong("color_${tool.name}", default)
    }
    private var savedToolWidths: Map<ToolType, Float> = DefaultToolWidths.mapValues { (tool, default) ->
        preferences.getFloat("width_${tool.name}", default)
    }
    private var savedTool: ToolType = runCatching {
        ToolType.valueOf(preferences.getString("tool", ToolType.PEN.name) ?: ToolType.PEN.name)
    }.getOrDefault(ToolType.PEN)

    var library by mutableStateOf(LibraryState())
        private set

    var editor by mutableStateOf<EditorState?>(null)
        private set

    private val undoStack = ArrayDeque<List<Page>>()
    private val redoStack = ArrayDeque<List<Page>>()
    private var eraseBaseline: List<Page>? = null
    private var selectionMoveBaseline: List<Page>? = null
    private var saveJob: Job? = null
    private var pendingSave: Note? = null

    init {
        viewModelScope.launch {
            val notes = runCatching { repository.loadAll() }.getOrDefault(emptyList())
            library = LibraryState(notes = notes.sortedByDescending { it.updatedAt }, loading = false)
        }
    }

    // ---------------------------------------------------------------- library

    fun createNote() {
        val note = newNote()
        library = library.copy(notes = (listOf(note) + library.notes).sortedByDescending { it.updatedAt })
        viewModelScope.launch { repository.save(note) }
        openNote(note.id)
    }

    fun openNote(noteId: String) {
        val note = library.notes.firstOrNull { it.id == noteId } ?: return
        flush()
        undoStack.clear()
        redoStack.clear()
        eraseBaseline = null
        selectionMoveBaseline = null
        editor = EditorState(
            note = note,
            tool = savedTool,
            toolColors = savedToolColors,
            toolWidths = savedToolWidths,
            canUndo = false,
            canRedo = false,
        )
    }

    fun closeEditor() {
        flush()
        val note = editor?.note
        if (note != null) syncLibrary(note)
        editor = null
        undoStack.clear()
        redoStack.clear()
        eraseBaseline = null
        selectionMoveBaseline = null
    }

    fun deleteNote(noteId: String) {
        library = library.copy(notes = library.notes.filterNot { it.id == noteId })
        if (editor?.note?.id == noteId) {
            saveJob?.cancel()
            pendingSave = null
            editor = null
            undoStack.clear()
            redoStack.clear()
        }
        viewModelScope.launch { repository.delete(noteId) }
    }

    fun duplicateNote(noteId: String) {
        val note = library.notes.firstOrNull { it.id == noteId } ?: return
        val copy = note.duplicate()
        library = library.copy(notes = (library.notes + copy).sortedByDescending { it.updatedAt })
        viewModelScope.launch { repository.save(copy) }
    }

    fun renameNoteInLibrary(noteId: String, title: String) {
        val current = library.notes.firstOrNull { it.id == noteId } ?: return
        val updated = current.copy(
            title = title.trim().ifBlank { "Untitled note" },
            updatedAt = System.currentTimeMillis(),
        )
        library = library.copy(notes = library.notes.map { if (it.id == noteId) updated else it }.sortedByDescending { it.updatedAt })
        if (editor?.note?.id == noteId) {
            editor = editor?.copy(note = updated)
        }
        viewModelScope.launch { repository.save(updated) }
    }

    fun renameCurrentNote(title: String) {
        val current = editor ?: return
        val updated = current.note.copy(
            title = title.trim().ifBlank { "Untitled note" },
            updatedAt = System.currentTimeMillis(),
        )
        editor = current.copy(note = updated)
        syncLibrary(updated)
        scheduleSave(updated)
    }

    fun flush() {
        val note = pendingSave ?: editor?.note ?: return
        saveJob?.cancel()
        pendingSave = null
        viewModelScope.launch { repository.save(note) }
    }

    // ---------------------------------------------------------------- editor state

    fun setTool(tool: ToolType) {
        val current = editor ?: return
        savedTool = tool
        preferences.edit().putString("tool", tool.name).apply()
        editor = current.copy(
            tool = tool,
            selectedStrokeIds = if (tool == ToolType.LASSO) current.selectedStrokeIds else emptySet(),
            selectedTextIds = if (tool == ToolType.LASSO) current.selectedTextIds else emptySet(),
        )
    }

    fun setToolColor(color: Long) {
        val current = editor ?: return
        val updated = current.toolColors + (current.tool to color)
        savedToolColors = updated
        preferences.edit().putLong("color_${current.tool.name}", color).apply()
        editor = current.copy(toolColors = updated)
    }

    fun setToolWidth(width: Float) {
        val current = editor ?: return
        val updated = current.toolWidths + (current.tool to width)
        savedToolWidths = updated
        preferences.edit().putFloat("width_${current.tool.name}", width).apply()
        editor = current.copy(toolWidths = updated)
    }

    fun setShapeType(shapeType: ShapeType) {
        editor = editor?.copy(shapeType = shapeType)
    }

    fun setPageBackground(background: PageBackground) {
        val current = editor ?: return
        val index = current.pageIndex.coerceIn(0, current.note.pages.lastIndex)
        val page = current.note.pages[index]
        if (page.background == background) return
        commitPages { pages ->
            pages.toMutableList().also { it[index] = it[index].copy(background = background) }
        }
    }

    // ---------------------------------------------------------------- pages

    fun goToPage(index: Int) {
        val current = editor ?: return
        val target = index.coerceIn(0, current.note.pages.lastIndex)
        if (target == current.pageIndex) return
        editor = current.copy(
            pageIndex = target,
            selectedStrokeIds = emptySet(),
            selectedTextIds = emptySet(),
        )
    }

    fun addPage() {
        val current = editor ?: return
        val insertAt = (current.pageIndex + 1).coerceIn(0, current.note.pages.size)
        val page = blankPage(current.page.background)
        commitPages { pages -> pages.toMutableList().also { it.add(insertAt, page) } }
        editor = editor?.copy(pageIndex = insertAt, selectedStrokeIds = emptySet(), selectedTextIds = emptySet())
    }

    fun duplicatePage() {
        val current = editor ?: return
        val insertAt = (current.pageIndex + 1).coerceIn(0, current.note.pages.size)
        val page = current.page.duplicate()
        commitPages { pages -> pages.toMutableList().also { it.add(insertAt, page) } }
        editor = editor?.copy(pageIndex = insertAt, selectedStrokeIds = emptySet(), selectedTextIds = emptySet())
    }

    fun deletePage() {
        val current = editor ?: return
        if (current.note.pages.size <= 1) {
            clearPage()
            return
        }
        val index = current.pageIndex.coerceIn(0, current.note.pages.lastIndex)
        commitPages { pages -> pages.toMutableList().also { it.removeAt(index) } }
        val newEditor = editor ?: return
        editor = newEditor.copy(
            pageIndex = index.coerceAtMost(newEditor.note.pages.lastIndex),
            selectedStrokeIds = emptySet(),
            selectedTextIds = emptySet(),
        )
    }

    fun movePage(direction: Int) {
        val current = editor ?: return
        val index = current.pageIndex.coerceIn(0, current.note.pages.lastIndex)
        val target = index + direction
        if (target !in current.note.pages.indices) return
        commitPages { pages ->
            pages.toMutableList().also {
                val page = it.removeAt(index)
                it.add(target, page)
            }
        }
        editor = editor?.copy(pageIndex = target)
    }

    fun clearPage() {
        val current = editor ?: return
        val index = current.pageIndex.coerceIn(0, current.note.pages.lastIndex)
        val page = current.note.pages[index]
        if (page.isEmpty) return
        commitPages { pages ->
            pages.toMutableList().also { it[index] = it[index].copy(strokes = emptyList(), texts = emptyList()) }
        }
        editor = editor?.copy(selectedStrokeIds = emptySet(), selectedTextIds = emptySet())
    }

    // ---------------------------------------------------------------- drawing

    fun addStroke(stroke: Stroke) {
        val current = editor ?: return
        val index = current.pageIndex.coerceIn(0, current.note.pages.lastIndex)
        commitPages { pages ->
            pages.toMutableList().also {
                it[index] = it[index].copy(strokes = it[index].strokes + stroke)
            }
        }
    }

    fun beginErase() {
        eraseBaseline = editor?.note?.pages
    }

    fun eraseAt(x: Float, y: Float) {
        val current = editor ?: return
        val index = current.pageIndex.coerceIn(0, current.note.pages.lastIndex)
        val page = current.note.pages[index]
        val radius = (current.toolWidths[ToolType.ERASER] ?: 26f) / 2f
        val strokesToRemove = page.strokes.filter { it.hitTest(x, y, radius) }.map { it.id }.toSet()
        val textsToRemove = page.texts.filter { it.hitTest(x, y, radius) }.map { it.id }.toSet()
        if (strokesToRemove.isEmpty() && textsToRemove.isEmpty()) return
        val updated = page.copy(
            strokes = page.strokes.filterNot { it.id in strokesToRemove },
            texts = page.texts.filterNot { it.id in textsToRemove },
        )
        replacePageWithoutHistory(index, updated)
    }

    fun endErase() {
        val baseline = eraseBaseline
        eraseBaseline = null
        if (baseline != null && editor?.note?.pages !== baseline) {
            pushUndo(baseline)
            syncHistoryFlags()
        }
    }

    fun addText(x: Float, y: Float, text: String, color: Long, fontSize: Float) {
        if (text.isBlank()) return
        val current = editor ?: return
        val index = current.pageIndex.coerceIn(0, current.note.pages.lastIndex)
        val item = TextItem(id = newId(), x = x, y = y, text = text, color = color, fontSize = fontSize)
        commitPages { pages ->
            pages.toMutableList().also {
                it[index] = it[index].copy(texts = it[index].texts + item)
            }
        }
    }

    fun updateText(textId: String, text: String) {
        val current = editor ?: return
        val index = current.pageIndex.coerceIn(0, current.note.pages.lastIndex)
        val page = current.note.pages[index]
        if (text.isBlank()) {
            if (page.texts.none { it.id == textId }) return
            commitPages { pages ->
                pages.toMutableList().also {
                    it[index] = it[index].copy(texts = it[index].texts.filterNot { item -> item.id == textId })
                }
            }
            return
        }
        if (page.texts.none { it.id == textId }) return
        commitPages { pages ->
            pages.toMutableList().also {
                it[index] = it[index].copy(
                    texts = it[index].texts.map { item ->
                        if (item.id == textId) item.copy(text = text) else item
                    },
                )
            }
        }
    }

    fun deleteText(textId: String) {
        val current = editor ?: return
        val index = current.pageIndex.coerceIn(0, current.note.pages.lastIndex)
        commitPages { pages ->
            pages.toMutableList().also {
                it[index] = it[index].copy(texts = it[index].texts.filterNot { item -> item.id == textId })
            }
        }
    }

    // ---------------------------------------------------------------- selection

    fun setSelection(strokeIds: Set<String>, textIds: Set<String>) {
        val current = editor ?: return
        editor = current.copy(
            selectedStrokeIds = strokeIds,
            selectedTextIds = textIds,
            tool = ToolType.LASSO,
        )
    }

    fun clearSelection() {
        val current = editor ?: return
        if (!current.hasSelection) return
        editor = current.copy(selectedStrokeIds = emptySet(), selectedTextIds = emptySet())
    }

    fun deleteSelection() {
        val current = editor ?: return
        if (!current.hasSelection) return
        val index = current.pageIndex.coerceIn(0, current.note.pages.lastIndex)
        val strokeIds = current.selectedStrokeIds
        val textIds = current.selectedTextIds
        commitPages { pages ->
            pages.toMutableList().also {
                val page = it[index]
                it[index] = page.copy(
                    strokes = page.strokes.filterNot { stroke -> stroke.id in strokeIds },
                    texts = page.texts.filterNot { text -> text.id in textIds },
                )
            }
        }
        editor = editor?.copy(selectedStrokeIds = emptySet(), selectedTextIds = emptySet())
    }

    fun duplicateSelection() {
        val current = editor ?: return
        if (!current.hasSelection) return
        val index = current.pageIndex.coerceIn(0, current.note.pages.lastIndex)
        val page = current.note.pages[index]
        val offset = 28f
        val newStrokes = page.strokes
            .filter { it.id in current.selectedStrokeIds }
            .map { stroke ->
                stroke.copy(
                    id = newId(),
                    points = stroke.points.map { it.copy(x = it.x + offset, y = it.y + offset) },
                )
            }
        val newTexts = page.texts
            .filter { it.id in current.selectedTextIds }
            .map { text ->
                text.copy(id = newId(), x = text.x + offset, y = text.y + offset)
            }
        commitPages { pages ->
            pages.toMutableList().also {
                val currentPage = it[index]
                it[index] = currentPage.copy(
                    strokes = currentPage.strokes + newStrokes,
                    texts = currentPage.texts + newTexts,
                )
            }
        }
        editor = editor?.copy(
            selectedStrokeIds = newStrokes.map { it.id }.toSet(),
            selectedTextIds = newTexts.map { it.id }.toSet(),
        )
    }

    fun beginSelectionMove() {
        selectionMoveBaseline = editor?.note?.pages
    }

    fun moveSelection(dx: Float, dy: Float) {
        val current = editor ?: return
        if (!current.hasSelection) return
        if (dx == 0f && dy == 0f) return
        val index = current.pageIndex.coerceIn(0, current.note.pages.lastIndex)
        val page = current.note.pages[index]
        val strokeIds = current.selectedStrokeIds
        val textIds = current.selectedTextIds
        val updated = page.copy(
            strokes = page.strokes.map { stroke ->
                if (stroke.id in strokeIds) {
                    stroke.copy(points = stroke.points.map { it.copy(x = it.x + dx, y = it.y + dy) })
                } else {
                    stroke
                }
            },
            texts = page.texts.map { text ->
                if (text.id in textIds) text.copy(x = text.x + dx, y = text.y + dy) else text
            },
        )
        replacePageWithoutHistory(index, updated)
    }

    fun endSelectionMove() {
        val baseline = selectionMoveBaseline
        selectionMoveBaseline = null
        if (baseline != null && editor?.note?.pages !== baseline) {
            pushUndo(baseline)
            syncHistoryFlags()
        }
    }

    // ---------------------------------------------------------------- history

    fun undo() {
        val current = editor ?: return
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(current.note.pages)
        applyHistory(previous)
    }

    fun redo() {
        val current = editor ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(current.note.pages)
        applyHistory(next)
    }

    private fun applyHistory(pages: List<Page>) {
        val current = editor ?: return
        val updated = current.note.copy(pages = pages, updatedAt = System.currentTimeMillis())
        editor = current.copy(
            note = updated,
            pageIndex = current.pageIndex.coerceIn(0, pages.lastIndex),
            selectedStrokeIds = emptySet(),
            selectedTextIds = emptySet(),
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
        )
        syncLibrary(updated)
        scheduleSave(updated)
    }

    private fun pushUndo(pages: List<Page>) {
        undoStack.addLast(pages)
        while (undoStack.size > 80) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun syncHistoryFlags() {
        val current = editor ?: return
        editor = current.copy(
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
        )
    }

    // ---------------------------------------------------------------- internals

    private fun commitPages(transform: (List<Page>) -> List<Page>) {
        val current = editor ?: return
        val previous = current.note.pages
        val updatedPages = transform(previous)
        if (updatedPages == previous) return
        pushUndo(previous)
        applyPages(current, updatedPages)
    }

    private fun applyPages(current: EditorState, pages: List<Page>) {
        val updated = current.note.copy(pages = pages, updatedAt = System.currentTimeMillis())
        editor = current.copy(
            note = updated,
            pageIndex = current.pageIndex.coerceIn(0, pages.lastIndex),
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
        )
        syncLibrary(updated)
        scheduleSave(updated)
    }

    private fun replacePageWithoutHistory(index: Int, page: Page) {
        val current = editor ?: return
        val pages = current.note.pages.toMutableList().also { it[index] = page }
        val updated = current.note.copy(pages = pages, updatedAt = System.currentTimeMillis())
        editor = current.copy(note = updated)
        syncLibrary(updated)
        scheduleSave(updated)
    }

    private fun syncLibrary(note: Note) {
        val exists = library.notes.any { it.id == note.id }
        val notes = if (exists) {
            library.notes.map { if (it.id == note.id) note else it }
        } else {
            library.notes + note
        }
        library = library.copy(notes = notes.sortedByDescending { it.updatedAt })
    }

    private fun scheduleSave(note: Note) {
        pendingSave = note
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(550)
            repository.save(note)
            if (pendingSave === note) {
                pendingSave = null
            }
        }
    }
}
