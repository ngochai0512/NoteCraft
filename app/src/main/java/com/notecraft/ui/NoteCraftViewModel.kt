package com.notecraft.ui

import android.app.Application
import android.util.Log
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notecraft.data.NoteRepository
import com.notecraft.model.AppSettings
import com.notecraft.model.DefaultTextWidth
import com.notecraft.model.DefaultToolChoices
import com.notecraft.model.DefaultToolColors
import com.notecraft.model.DefaultToolWidths
import com.notecraft.model.EraserMode
import com.notecraft.model.InkBlack
import com.notecraft.model.Note
import com.notecraft.model.NoteTemplate
import com.notecraft.model.PAGE_WIDTH
import com.notecraft.model.Page
import com.notecraft.model.PageBackground
import com.notecraft.model.ShapeType
import com.notecraft.model.StickyNote
import com.notecraft.model.Stroke
import com.notecraft.model.TextAlignment
import com.notecraft.model.TextFormatting
import com.notecraft.model.TextItem
import com.notecraft.model.ThemeMode
import com.notecraft.model.ToolType
import com.notecraft.model.blankPage
import com.notecraft.model.duplicate
import com.notecraft.model.newId
import com.notecraft.model.newNote
import com.notecraft.util.Bounds
import com.notecraft.util.TextMeasurer
import com.notecraft.util.approximateBounds
import com.notecraft.util.hitTest
import com.notecraft.util.normalizeFolder
import com.notecraft.util.normalizeTags
import com.notecraft.util.pageIndexAfterMove
import com.notecraft.util.partialErase
import com.notecraft.util.rotateAroundCenter
import com.notecraft.util.selectionBounds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MaxRecentColors = 6

data class LibraryState(
    val notes: List<Note> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
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
    val textFormatting: TextFormatting = TextFormatting(),
    val stickyFontSize: Float = 28f,
    val recentColors: List<Long> = emptyList(),
    val selectedStrokeIds: Set<String> = emptySet(),
    val selectedTextIds: Set<String> = emptySet(),
    val selectionPageIndex: Int = 0,
    val clipboardCount: Int = 0,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
) {
    val page: Page get() = note.pages[pageIndex.coerceIn(0, note.pages.lastIndex)]
    val color: Long get() = toolColors[tool] ?: InkBlack
    val width: Float get() = toolWidths[tool] ?: 4f
    val hasSelection: Boolean get() = selectedStrokeIds.isNotEmpty() || selectedTextIds.isNotEmpty()
    val selectionCount: Int get() = selectedStrokeIds.size + selectedTextIds.size
}

private data class ClipboardSelection(
    val strokes: List<Stroke>,
    val texts: List<TextItem>,
) {
    val itemCount: Int get() = strokes.size + texts.size
}

private data class SelectionTransformOrigin(
    val pageIndex: Int,
    val page: Page,
    val strokeIds: Set<String>,
    val textIds: Set<String>,
    val bounds: Bounds,
)

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
    private var savedStickyFontSize: Float = preferences.getFloat("sticky_font_size", 28f)
        .coerceIn(16f, 48f)
    private val savedRecentColors: MutableList<Long> = loadRecentColors()
    private var textResizeBaseline: List<Page>? = null
    private var stickyMoveBaseline: List<Page>? = null
    private var selectionTransformBaseline: List<Page>? = null
    private var selectionTransformOrigin: SelectionTransformOrigin? = null
    private var selectionClipboard: ClipboardSelection? = null
    private var clipboardPasteIndex = 0

    var settings by mutableStateOf(loadSettings())
        private set

    var settingsOpen by mutableStateOf(false)
        private set

    var library by mutableStateOf(LibraryState())
        private set

    var editor by mutableStateOf<EditorState?>(null)
        private set

    var saveError by mutableStateOf<String?>(null)
        private set

    private var failedSaveNote: Note? = null

    private val undoStack = ArrayDeque<List<Page>>()
    private val redoStack = ArrayDeque<List<Page>>()
    private var eraseBaseline: List<Page>? = null
    private var selectionMoveBaseline: List<Page>? = null
    private var saveJob: Job? = null
    private var pendingSave: Note? = null

    init {
        reloadLibrary()
    }

    fun retrySave() {
        val note = failedSaveNote ?: pendingSave ?: editor?.note ?: return
        persist(note)
    }

    fun dismissSaveError() {
        saveError = null
    }

    fun reloadLibrary() {
        library = library.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { repository.loadAll() }
                .onSuccess { notes ->
                    library = LibraryState(
                        notes = notes.sortedByDescending { it.updatedAt },
                        loading = false,
                    )
                }
                .onFailure { error ->
                    Log.e("NoteCraft", "Failed to load notes", error)
                    library = library.copy(
                        loading = false,
                        error = "Could not load notebooks from this device.",
                    )
                }
        }
    }

    // ---------------------------------------------------------------- library

    fun createNote(template: NoteTemplate = settings.defaultNoteTemplate) {
        // "Untitled notebook" keeps honoring the default paper for new pages so existing
        // preferences continue to work; explicit templates pick their own paper.
        val background = if (template == NoteTemplate.BLANK) {
            settings.defaultPageBackground ?: PageBackground.BLANK
        } else {
            template.background
        }
        val note = newNote(
            title = template.label,
            background = background,
        )
        library = library.copy(notes = (listOf(note) + library.notes).sortedByDescending { it.updatedAt })
        persist(note)
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
        stickyMoveBaseline = null
        selectionTransformBaseline = null
        selectionTransformOrigin = null
        editor = EditorState(
            note = note,
            tool = settings.defaultTool,
            toolColors = savedToolColors,
            toolWidths = savedToolWidths,
            eraserMode = savedEraserMode,
            stickyFontSize = savedStickyFontSize,
            recentColors = savedRecentColors.toList(),
            clipboardCount = selectionClipboard?.itemCount ?: 0,
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
        stickyMoveBaseline = null
        selectionTransformBaseline = null
        selectionTransformOrigin = null
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
        viewModelScope.launch {
            runCatching { repository.delete(noteId) }
                .onFailure { error ->
                    saveError = "Could not delete the notebook."
                    Log.e("NoteCraft", "Failed to delete note $noteId", error)
                }
        }
    }

    fun duplicateNote(noteId: String) {
        val note = library.notes.firstOrNull { it.id == noteId } ?: return
        val copy = note.duplicate()
        library = library.copy(notes = (library.notes + copy).sortedByDescending { it.updatedAt })
        persist(copy)
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
        persist(updated)
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
        persist(note)
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
        if (tool !in DefaultToolChoices) return
        settings = settings.copy(defaultTool = tool)
        settingsPreferences.edit().putString("default_tool", tool.name).apply()
    }

    fun setDefaultNoteTemplate(template: NoteTemplate) {
        settings = settings.copy(defaultNoteTemplate = template)
        settingsPreferences.edit().putString("default_note_template", template.name).apply()
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

    /** Update a notebook's folder and tags without changing its edit timestamp. */
    fun updateNoteOrganization(noteId: String, folder: String?, tags: String) {
        val existing = library.notes.firstOrNull { it.id == noteId }
            ?: editor?.note?.takeIf { it.id == noteId }
            ?: return
        val updated = existing.copy(
            folder = normalizeFolder(folder),
            tags = normalizeTags(tags),
        )
        if (updated == existing) return
        syncLibrary(updated)
        if (editor?.note?.id == noteId) {
            editor = editor?.copy(note = updated)
        }
        persist(updated)
    }

    /** Pin/unpin a notebook; pinned notebooks sort first in the library. */
    fun toggleNotePin(noteId: String) {
        val existing = library.notes.firstOrNull { it.id == noteId }
            ?: editor?.note?.takeIf { it.id == noteId }
            ?: return
        val updated = existing.copy(pinned = !existing.pinned)
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
        val savedTool = runCatching {
            ToolType.valueOf(
                settingsPreferences.getString("default_tool", ToolType.BALL_PEN.name)
                    ?: ToolType.BALL_PEN.name,
            )
        }.getOrDefault(ToolType.BALL_PEN)
        val defaultTool = savedTool.takeIf { it in DefaultToolChoices } ?: ToolType.BALL_PEN
        val defaultBackground = settingsPreferences
            .getString("default_page_background", null)
            ?.let { raw -> runCatching { PageBackground.valueOf(raw) }.getOrNull() }
        val defaultTemplate = settingsPreferences
            .getString("default_note_template", NoteTemplate.BLANK.name)
            ?.let { raw -> runCatching { NoteTemplate.valueOf(raw) }.getOrNull() }
            ?: NoteTemplate.BLANK
        return AppSettings(
            themeMode = themeMode,
            defaultTool = defaultTool,
            defaultPageBackground = defaultBackground,
            defaultNoteTemplate = defaultTemplate,
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

    fun setTextFormatting(formatting: TextFormatting) {
        editor = editor?.copy(textFormatting = formatting)
    }

    fun setStickyFontSize(size: Float) {
        val clamped = size.coerceIn(16f, 48f)
        savedStickyFontSize = clamped
        preferences.edit().putFloat("sticky_font_size", clamped).apply()
        editor = editor?.copy(stickyFontSize = clamped)
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
        val lastIndex = updated.note.pages.lastIndex
        val newIndex = pageIndexAfterMove(current.pageIndex, from, target).coerceIn(0, lastIndex)
        val selectionIndex = pageIndexAfterMove(current.selectionPageIndex, from, target)
            .coerceIn(0, lastIndex)
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
            pages.toMutableList().also {
                it[index] = it[index].copy(strokes = emptyList(), texts = emptyList(), stickies = emptyList())
            }
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
        val stickiesToRemove = page.stickies.filter { it.hitTest(x, y, radius) }.map { it.id }.toSet()
        if (remainingStrokes == page.strokes && textsToRemove.isEmpty() && stickiesToRemove.isEmpty()) return
        val updated = page.copy(
            strokes = remainingStrokes,
            texts = page.texts.filterNot { it.id in textsToRemove },
            stickies = page.stickies.filterNot { it.id in stickiesToRemove },
        )
        replacePageWithoutHistory(index, updated)
    }

    fun endErase() {
        val baseline = eraseBaseline
        eraseBaseline = null
        finishContinuousEdit(baseline)
    }

    fun addText(
        x: Float,
        y: Float,
        text: String,
        color: Long,
        fontSize: Float,
        alignment: TextAlignment = TextAlignment.LEFT,
        formatting: TextFormatting = TextFormatting(),
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
            formatting = formatting,
        ).let { it.copy(height = TextMeasurer.textHeight(it)) }
        commitPages { pages ->
            pages.toMutableList().also {
                it[index] = it[index].copy(texts = it[index].texts + item)
            }
        }
    }

    fun updateText(
        textId: String,
        text: String,
        alignment: TextAlignment? = null,
        formatting: TextFormatting? = null,
    ) {
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
                                formatting = formatting ?: item.formatting,
                            ).let { updated -> updated.copy(height = TextMeasurer.textHeight(updated)) }
                        } else {
                            item
                        }
                    },
                )
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
                if (item.id == textId) {
                    val updated = item.copy(width = width.coerceIn(80f, PAGE_WIDTH * 2f))
                    updated.copy(height = TextMeasurer.textHeight(updated))
                } else {
                    item
                }
            },
        )
        replacePageWithoutHistory(index, updated)
    }

    fun endTextResize() {
        val baseline = textResizeBaseline
        textResizeBaseline = null
        finishContinuousEdit(baseline)
    }

    // ---------------------------------------------------------------- sticky notes

    fun addSticky(
        x: Float,
        y: Float,
        text: String,
        color: Long,
        width: Float,
        fontSize: Float = 28f,
        pageIndexOverride: Int? = null,
    ) {
        if (text.isBlank()) return
        val current = editor ?: return
        val index = (pageIndexOverride ?: current.pageIndex).coerceIn(0, current.note.pages.lastIndex)
        val noteWidth = width.coerceIn(160f, 420f)
        val note = StickyNote(
            id = newId(),
            x = x,
            y = y,
            width = noteWidth,
            height = noteWidth * 0.72f,
            text = text,
            color = color,
            fontSize = fontSize.coerceIn(16f, 48f),
        ).let { it.copy(height = maxOf(it.height, TextMeasurer.stickyHeight(it))) }
        commitPages { pages ->
            pages.toMutableList().also {
                it[index] = it[index].copy(stickies = it[index].stickies + note)
            }
        }
    }

    fun updateSticky(stickyId: String, text: String) {
        val current = editor ?: return
        val index = current.note.pages.indexOfFirst { page -> page.stickies.any { it.id == stickyId } }
        if (index < 0) return
        if (text.isBlank()) {
            commitPages { pages ->
                pages.toMutableList().also {
                    it[index] = it[index].copy(stickies = it[index].stickies.filterNot { note -> note.id == stickyId })
                }
            }
            return
        }
        commitPages { pages ->
            pages.toMutableList().also {
                it[index] = it[index].copy(
                    stickies = it[index].stickies.map { note ->
                        if (note.id == stickyId) {
                            val updated = note.copy(text = text)
                            updated.copy(height = maxOf(updated.height, TextMeasurer.stickyHeight(updated)))
                        } else {
                            note
                        }
                    },
                )
            }
        }
    }


    fun beginStickyMove() {
        stickyMoveBaseline = editor?.note?.pages
    }

    fun moveSticky(stickyId: String, dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return
        val current = editor ?: return
        val index = current.note.pages.indexOfFirst { page -> page.stickies.any { it.id == stickyId } }
        if (index < 0) return
        val page = current.note.pages[index]
        val updated = page.copy(
            stickies = page.stickies.map { note ->
                if (note.id == stickyId) note.copy(x = note.x + dx, y = note.y + dy) else note
            },
        )
        replacePageWithoutHistory(index, updated)
    }

    fun endStickyMove() {
        val baseline = stickyMoveBaseline
        stickyMoveBaseline = null
        finishContinuousEdit(baseline)
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
        finishContinuousEdit(baseline)
    }


    // ---------------------------------------------------------------- clipboard

    fun copySelection() {
        val current = editor ?: return
        if (!current.hasSelection) return
        val index = current.selectionPageIndex.coerceIn(0, current.note.pages.lastIndex)
        val page = current.note.pages[index]
        val strokes = page.strokes.filter { it.id in current.selectedStrokeIds }
        val texts = page.texts.filter { it.id in current.selectedTextIds }
        if (strokes.isEmpty() && texts.isEmpty()) return
        selectionClipboard = ClipboardSelection(
            strokes = strokes.map { stroke ->
                stroke.copy(points = stroke.points.map { it.copy() })
            },
            texts = texts.map { it.copy() },
        )
        clipboardPasteIndex = 0
        editor = current.copy(clipboardCount = selectionClipboard?.itemCount ?: 0)
    }

    fun cutSelection() {
        val current = editor ?: return
        if (!current.hasSelection) return
        copySelection()
        deleteSelection()
    }

    fun pasteSelection() {
        val clipboard = selectionClipboard ?: return
        val current = editor ?: return
        val index = current.pageIndex.coerceIn(0, current.note.pages.lastIndex)
        clipboardPasteIndex = (clipboardPasteIndex + 1).coerceAtMost(5)
        val offset = 28f * clipboardPasteIndex
        val newStrokes = clipboard.strokes.map { stroke ->
            stroke.copy(
                id = newId(),
                points = stroke.points.map { point ->
                    point.copy(x = point.x + offset, y = point.y + offset)
                },
            )
        }
        val newTexts = clipboard.texts.map { text ->
            val moved = text.copy(
                id = newId(),
                x = text.x + offset,
                y = text.y + offset,
            )
            moved.copy(height = TextMeasurer.textHeight(moved))
        }
        commitPages { pages ->
            pages.toMutableList().also {
                val page = it[index]
                it[index] = page.copy(
                    strokes = page.strokes + newStrokes,
                    texts = page.texts + newTexts,
                )
            }
        }
        editor = editor?.copy(
            selectedStrokeIds = newStrokes.map { it.id }.toSet(),
            selectedTextIds = newTexts.map { it.id }.toSet(),
            selectionPageIndex = index,
            tool = ToolType.LASSO,
            clipboardCount = clipboard.itemCount,
        )
    }

    // ---------------------------------------------------------------- selection transforms

    fun beginSelectionTransform() {
        val current = editor ?: return
        if (!current.hasSelection) return
        val index = current.selectionPageIndex.coerceIn(0, current.note.pages.lastIndex)
        val page = current.note.pages[index]
        val bounds = selectionBounds(page, current.selectedStrokeIds, current.selectedTextIds) ?: return
        selectionTransformBaseline = current.note.pages
        selectionTransformOrigin = SelectionTransformOrigin(
            pageIndex = index,
            page = page,
            strokeIds = current.selectedStrokeIds,
            textIds = current.selectedTextIds,
            bounds = bounds,
        )
    }

    fun resizeSelection(scaleX: Float, scaleY: Float, anchorX: Float, anchorY: Float) {
        val origin = selectionTransformOrigin ?: return
        val sx = scaleX.coerceIn(0.05f, 20f)
        val sy = scaleY.coerceIn(0.05f, 20f)
        val minScale = minOf(sx, sy)
        val page = origin.page
        val strokes = page.strokes.map { stroke ->
            if (stroke.id in origin.strokeIds) {
                stroke.copy(
                    width = (stroke.width * minScale).coerceIn(0.5f, 120f),
                    points = stroke.points.map { point ->
                        point.copy(
                            x = anchorX + (point.x - anchorX) * sx,
                            y = anchorY + (point.y - anchorY) * sy,
                        )
                    },
                )
            } else {
                stroke
            }
        }
        val texts = page.texts.map { text ->
            if (text.id in origin.textIds) {
                val updated = text.copy(
                    x = anchorX + (text.x - anchorX) * sx,
                    y = anchorY + (text.y - anchorY) * sy,
                    fontSize = (text.fontSize * minScale).coerceIn(8f, 200f),
                    width = if (text.width > 0f) {
                        (text.width * sx).coerceIn(40f, PAGE_WIDTH * 2f)
                    } else {
                        0f
                    },
                )
                updated.copy(height = TextMeasurer.textHeight(updated))
            } else {
                text
            }
        }
        replacePageWithoutHistory(
            origin.pageIndex,
            page.copy(strokes = strokes, texts = texts),
        )
    }

    fun rotateSelection(angleRadians: Float) {
        val origin = selectionTransformOrigin ?: return
        val center = origin.bounds.center
        val page = origin.page
        val strokes = page.strokes.map { stroke ->
            if (stroke.id in origin.strokeIds) {
                stroke.copy(
                    points = stroke.points.map { point ->
                        val rotated = rotateAroundCenter(
                            x = point.x,
                            y = point.y,
                            centerX = center.x,
                            centerY = center.y,
                            angleRadians = angleRadians,
                        )
                        point.copy(x = rotated.x, y = rotated.y)
                    },
                )
            } else {
                stroke
            }
        }
        val texts = page.texts.map { text ->
            if (text.id in origin.textIds) {
                val bounds = text.approximateBounds()
                val rotated = rotateAroundCenter(
                    x = bounds.centerX,
                    y = bounds.centerY,
                    centerX = center.x,
                    centerY = center.y,
                    angleRadians = angleRadians,
                )
                val updated = text.copy(
                    x = rotated.x - bounds.width / 2f,
                    y = rotated.y - bounds.height / 2f,
                )
                updated.copy(height = TextMeasurer.textHeight(updated))
            } else {
                text
            }
        }
        replacePageWithoutHistory(
            origin.pageIndex,
            page.copy(strokes = strokes, texts = texts),
        )
    }

    fun endSelectionTransform() {
        val baseline = selectionTransformBaseline
        selectionTransformBaseline = null
        selectionTransformOrigin = null
        finishContinuousEdit(baseline)
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
        editor = current.copy(note = current.note.copy(pages = pages))
    }

    /**
     * Continuous gestures update the editor on every pointer event but only sync the library and
     * schedule a save once, when the gesture ends.
     */
    private fun finishContinuousEdit(baseline: List<Page>?) {
        val current = editor ?: return
        if (baseline == null || current.note.pages === baseline) return
        pushUndo(baseline)
        val updated = current.note.copy(updatedAt = System.currentTimeMillis())
        editor = current.copy(
            note = updated,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
        )
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

    private fun persist(note: Note) {
        viewModelScope.launch {
            runCatching { repository.save(note) }
                .onSuccess {
                    saveError = null
                    failedSaveNote = null
                }
                .onFailure { error ->
                    saveError = "Could not save changes to this notebook."
                    failedSaveNote = note
                    Log.e("NoteCraft", "Failed to save note ${note.id}", error)
                }
        }
    }

    private fun scheduleSave(note: Note) {
        pendingSave = note
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(550)
            val success = runCatching { repository.save(note) }
                .onSuccess {
                    saveError = null
                    failedSaveNote = null
                }
                .onFailure { error ->
                    saveError = "Could not save changes to this notebook."
                    failedSaveNote = note
                    Log.e("NoteCraft", "Failed to save note ${note.id}", error)
                }
                .isSuccess
            if (success && pendingSave === note) {
                pendingSave = null
            }
        }
    }
}
