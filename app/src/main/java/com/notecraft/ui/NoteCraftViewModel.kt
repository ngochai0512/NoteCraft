package com.notecraft.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notecraft.data.NoteRepository
import com.notecraft.model.AppSettings
import com.notecraft.model.DefaultTextWidth
import com.notecraft.model.DefaultToolColors
import com.notecraft.model.DefaultToolWidths
import com.notecraft.model.EraserMode
import com.notecraft.model.Note
import com.notecraft.model.PAGE_WIDTH
import com.notecraft.model.Page
import com.notecraft.model.PageBackground
import com.notecraft.model.ShapeType
import com.notecraft.model.Stroke
import com.notecraft.model.TextAlignment
import com.notecraft.model.TextItem
import com.notecraft.model.ThemeMode
import com.notecraft.model.ToolType
import com.notecraft.model.blankPage
import com.notecraft.model.duplicate
import com.notecraft.model.newId
import com.notecraft.model.newNote
import com.notecraft.util.hitTest
import com.notecraft.util.partialErase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MaxRecentColors = 6

data class LibraryState(
    val notes: List<Note> = emptyList(),
    val loading: Boolean = true,
)

data class EditorState(
    val note: Note,
    val pageIndex: Int = 0,
    val tool: ToolType = ToolType.BALL_PEN,
    val toolColors: Map<ToolType, Long> = DefaultToolColors,
    val toolWidths: Map<ToolType, Float> = DefaultToolWidths,
    val shapeType: ShapeType = ShapeType.LINE,
    val eraserMode: EraserMode = EraserMode.PARTIAL,
    val textAlignment: TextAlignment = TextAlignment.LEFT,
    val recentColors: List<Long> = emptyList(),
    val selectedStrokeIds: Set<String> = emptySet(),
    val selectedTextIds: Set<String> = emptySet(),
    val selectionPageIndex: Int = 0,
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
    private val settingsPreferences = application.getSharedPreferences("notecraft_settings", Context.MODE_PRIVATE)
    private var savedToolColors: Map<ToolType, Long> = DefaultToolColors.mapValues { (tool, default) ->
        preferences.getLong("color_${tool.name}", default)
    }
    private var savedToolWidths: Map<ToolType, Float> = DefaultToolWidths.mapValues { (tool, default) ->
        preferences.getFloat("width_${tool.name}", default)
    }
    private var savedEraserMode: EraserMode = runCatching {
        EraserMode.valueOf(
            preferences.getString("eraser_mode", EraserMode.PARTIAL.name)
                ?: EraserMode.PARTIAL.name,
        )
    }.getOrDefault(EraserMode.PARTIAL)
    private val savedRecentColors: MutableList<Long> = loadRecentColors()
    private var textResizeBaseline: List<Page>? = null

    var settings by mutableStateOf(loadSettings())
        private set

    var settingsOpen by mutableStateOf(false)
        private set

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
        val note = newNote(background = settings.defaultPageBackground ?: PageBackground.BLANK)
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
        textResizeBaseline = null
        editor = EditorState(
            note = note,
            tool = settings.defaultTool,
            toolColors = savedToolColors,
            toolWidths = savedToolWidths,
            eraserMode = savedEraserMode,
            recentColors = savedRecentColors.toList(),
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
        textResizeBaseline = null
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
            title = title.trim().ifBlank { "Untitled notebook" },
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
            title = title.trim().ifBlank { "Untitled notebook" },
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

    // ---------------------------------------------------------------- settings

    fun openSettings() {
        flush()
        settingsOpen = true
    }

    fun closeSettings() {
        settingsOpen = false
    }

    fun setThemeMode(mode: ThemeMode) {
        settings = settings.copy(themeMode = mode)
        settingsPreferences.edit().putString("theme_mode", mode.name).apply()
    }

    fun setDefaultTool(tool: ToolType) {
        settings = settings.copy(defaultTool = tool)
        settingsPreferences.edit().putString("default_tool", tool.name).apply()
    }

    fun setDefaultPageBackground(background: PageBackground?) {
        settings = settings.copy(defaultPageBackground = background)
        settingsPreferences.edit().apply {
            if (background == null) {
                remove("default_page_background")
            } else {
                putString("default_page_background", background.name)
            }
        }.apply()
    }

    fun setShowAutosaveIndicator(show: Boolean) {
        settings = settings.copy(showAutosaveIndicator = show)
        settingsPreferences.edit().putBoolean("show_autosave_indicator", show).apply()
    }

    fun setStylusOnlyMode(enabled: Boolean) {
        settings = settings.copy(stylusOnlyMode = enabled)
        settingsPreferences.edit().putBoolean("stylus_only_mode", enabled).apply()
    }

    fun setNoteCover(noteId: String, coverTone: String?) {
        val existing = library.notes.firstOrNull { it.id == noteId }
            ?: editor?.note?.takeIf { it.id == noteId }
            ?: return
        val updated = existing.copy(coverTone = coverTone, updatedAt = System.currentTimeMillis())
        syncLibrary(updated)
        if (editor?.note?.id == noteId) {
            editor = editor?.copy(note = updated)
        }
        scheduleSave(updated)
    }

    private fun loadSettings(): AppSettings {
        val themeMode = runCatching {
            ThemeMode.valueOf(
                settingsPreferences.getString("theme_mode", ThemeMode.SYSTEM.name)
                    ?: ThemeMode.SYSTEM.name,
            )
        }.getOrDefault(ThemeMode.SYSTEM)
        val defaultTool = runCatching {
            ToolType.valueOf(
                settingsPreferences.getString("default_tool", ToolType.BALL_PEN.name)
                    ?: ToolType.BALL_PEN.name,
            )
        }.getOrDefault(ToolType.BALL_PEN)
        val defaultBackground = settingsPreferences
            .getString("default_page_background", null)
            ?.let { raw -> runCatching { PageBackground.valueOf(raw) }.getOrNull() }
        return AppSettings(
            themeMode = themeMode,
            defaultTool = defaultTool,
            defaultPageBackground = defaultBackground,
            showAutosaveIndicator = settingsPreferences.getBoolean("show_autosave_indicator", true),
            stylusOnlyMode = settingsPreferences.getBoolean("stylus_only_mode", false),
        )
    }

    private fun loadRecentColors(): MutableList<Long> = preferences
        .getString("recent_colors", null)
        ?.split(',')
        ?.mapNotNull { raw -> raw.toLongOrNull() }
        ?.distinct()
        ?.take(MaxRecentColors)
        ?.toMutableList()
        ?: mutableListOf()

    private fun rememberRecentColor(color: Long) {
        savedRecentColors.remove(color)
        savedRecentColors.add(0, color)
        while (savedRecentColors.size > MaxRecentColors) savedRecentColors.removeAt(savedRecentColors.lastIndex)
        preferences.edit()
            .putString("recent_colors", savedRecentColors.joinToString(","))
            .apply()
        editor = editor?.copy(recentColors = savedRecentColors.toList())
    }

    // ---------------------------------------------------------------- editor state

    fun setTool(tool: ToolType) {
        val current = editor ?: return
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
        rememberRecentColor(color)
    }

    fun setEraserMode(mode: EraserMode) {
        val current = editor ?: return
        savedEraserMode = mode
        preferences.edit().putString("eraser_mode", mode.name).apply()
        editor = current.copy(eraserMode = mode)
    }

    fun setTextAlignment(alignment: TextAlignment) {
        editor = editor?.copy(textAlignment = alignment)
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
            selectionPageIndex = target,
        )
    }

    fun addPage() {
        val current = editor ?: return
        val insertAt = (current.pageIndex + 1).coerceIn(0, current.note.pages.size)
        val page = blankPage(settings.defaultPageBackground ?: current.page.background)
        commitPages { pages -> pages.toMutableList().also { it.add(insertAt, page) } }
        editor = editor?.copy(
            pageIndex = insertAt,
            selectionPageIndex = insertAt,
            selectedStrokeIds = emptySet(),
            selectedTextIds = emptySet(),
        )
    }

    fun duplicatePage() {
        val current = editor ?: return
        val insertAt = (current.pageIndex + 1).coerceIn(0, current.note.pages.size)
        val page = current.page.duplicate()
        commitPages { pages -> pages.toMutableList().also { it.add(insertAt, page) } }
        editor = editor?.copy(
            pageIndex = insertAt,
            selectionPageIndex = insertAt,
            selectedStrokeIds = emptySet(),
            selectedTextIds = emptySet(),
        )
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
        val newIndex = index.coerceAtMost(newEditor.note.pages.lastIndex)
        editor = newEditor.copy(
            pageIndex = newIndex,
            selectionPageIndex = newIndex,
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
        editor = editor?.copy(pageIndex = target, selectionPageIndex = target)
    }

    /** Drag-and-drop reordering from the page drawer. */
    fun movePageTo(from: Int, to: Int) {
        val current = editor ?: return
        if (from !in current.note.pages.indices) return
        val target = to.coerceIn(0, current.note.pages.lastIndex)
        if (from == target) return
        commitPages { pages ->
            pages.toMutableList().also {
                val page = it.removeAt(from)
                it.add(target, page)
            }
        }
        val updated = editor ?: return
        val currentIndex = current.pageIndex
        val newIndex = when {
            currentIndex == from -> target
            from < currentIndex && target >= currentIndex -> currentIndex - 1
            from > currentIndex && target <= currentIndex -> currentIndex + 1
            else -> currentIndex
        }.coerceIn(0, updated.note.pages.lastIndex)
        val selectionIndex = when {
            current.selectionPageIndex == from -> target
            from < current.selectionPageIndex && target >= current.selectionPageIndex -> current.selectionPageIndex - 1
            from > current.selectionPageIndex && target <= current.selectionPageIndex -> current.selectionPageIndex + 1
            else -> current.selectionPageIndex
        }.coerceIn(0, updated.note.pages.lastIndex)
        editor = updated.copy(pageIndex = newIndex, selectionPageIndex = selectionIndex)
    }

    /** Star/unstar a page. Bookmarks live in the note JSON like the rest of the page. */
    fun togglePageBookmark(index: Int? = null) {
        val current = editor ?: return
        val target = (index ?: current.pageIndex).coerceIn(0, current.note.pages.lastIndex)
        commitPages { pages ->
            pages.toMutableList().also {
                it[target] = it[target].copy(bookmarked = !it[target].bookmarked)
            }
        }
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

    fun addStroke(stroke: Stroke, pageIndexOverride: Int? = null) {
        val current = editor ?: return
        val index = (pageIndexOverride ?: current.pageIndex).coerceIn(0, current.note.pages.lastIndex)
        commitPages { pages ->
            pages.toMutableList().also {
                it[index] = it[index].copy(strokes = it[index].strokes + stroke)
            }
        }
    }

    fun beginErase() {
        eraseBaseline = editor?.note?.pages
    }

    fun eraseAt(x: Float, y: Float, pageIndexOverride: Int? = null) {
        val current = editor ?: return
        val index = (pageIndexOverride ?: current.pageIndex).coerceIn(0, current.note.pages.lastIndex)
        val page = current.note.pages[index]
        val radius = (current.toolWidths[ToolType.ERASER] ?: 26f) / 2f
        val remainingStrokes = if (current.eraserMode == EraserMode.OBJECT) {
            page.strokes.filterNot { stroke -> stroke.hitTest(x, y, radius) }
        } else {
            page.strokes.flatMap { stroke ->
                if (stroke.hitTest(x, y, radius)) {
                    stroke.partialErase(x, y, radius)
                } else {
                    listOf(stroke)
                }
            }
        }
        val textsToRemove = page.texts.filter { it.hitTest(x, y, radius) }.map { it.id }.toSet()
        if (remainingStrokes == page.strokes && textsToRemove.isEmpty()) return
        val updated = page.copy(
            strokes = remainingStrokes,
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

    fun addText(
        x: Float,
        y: Float,
        text: String,
        color: Long,
        fontSize: Float,
        alignment: TextAlignment = TextAlignment.LEFT,
        pageIndexOverride: Int? = null,
    ) {
        if (text.isBlank()) return
        val current = editor ?: return
        val index = (pageIndexOverride ?: current.pageIndex).coerceIn(0, current.note.pages.lastIndex)
        val item = TextItem(
            id = newId(),
            x = x,
            y = y,
            text = text,
            color = color,
            fontSize = fontSize,
            width = DefaultTextWidth,
            alignment = alignment,
        )
        commitPages { pages ->
            pages.toMutableList().also {
                it[index] = it[index].copy(texts = it[index].texts + item)
            }
        }
    }

    fun updateText(textId: String, text: String, alignment: TextAlignment? = null) {
        val current = editor ?: return
        val index = current.note.pages.indexOfFirst { page -> page.texts.any { it.id == textId } }
        if (index < 0) return
        val page = current.note.pages[index]
        if (text.isBlank()) {
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
                        if (item.id == textId) {
                            item.copy(
                                text = text,
                                alignment = alignment ?: item.alignment,
                            )
                        } else {
                            item
                        }
                    },
                )
            }
        }
    }

    fun deleteText(textId: String) {
        val current = editor ?: return
        val index = current.note.pages.indexOfFirst { page -> page.texts.any { it.id == textId } }
        if (index < 0) return
        commitPages { pages ->
            pages.toMutableList().also {
                it[index] = it[index].copy(texts = it[index].texts.filterNot { item -> item.id == textId })
            }
        }
    }

    fun beginTextResize() {
        textResizeBaseline = editor?.note?.pages
    }

    fun resizeText(textId: String, width: Float) {
        val current = editor ?: return
        val index = current.note.pages.indexOfFirst { page -> page.texts.any { it.id == textId } }
        if (index < 0) return
        val page = current.note.pages[index]
        val updated = page.copy(
            texts = page.texts.map { item ->
                if (item.id == textId) item.copy(width = width.coerceIn(80f, PAGE_WIDTH * 2f)) else item
            },
        )
        replacePageWithoutHistory(index, updated)
    }

    fun endTextResize() {
        val baseline = textResizeBaseline
        textResizeBaseline = null
        if (baseline != null && editor?.note?.pages !== baseline) {
            pushUndo(baseline)
            syncHistoryFlags()
        }
    }

    // ---------------------------------------------------------------- selection

    fun setSelection(pageIndex: Int, strokeIds: Set<String>, textIds: Set<String>) {
        val current = editor ?: return
        val target = pageIndex.coerceIn(0, current.note.pages.lastIndex)
        editor = current.copy(
            selectedStrokeIds = strokeIds,
            selectedTextIds = textIds,
            selectionPageIndex = target,
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
        val index = current.selectionPageIndex.coerceIn(0, current.note.pages.lastIndex)
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
        val index = current.selectionPageIndex.coerceIn(0, current.note.pages.lastIndex)
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
        val index = current.selectionPageIndex.coerceIn(0, current.note.pages.lastIndex)
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
