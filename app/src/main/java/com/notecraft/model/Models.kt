package com.notecraft.model

import java.util.UUID

/** Logical page size in page units. Strokes are stored in this coordinate space. */
const val PAGE_WIDTH = 1000f
const val PAGE_HEIGHT = 1414f

enum class ToolType(val label: String) {
    FOUNTAIN_PEN("Fountain"),
    BALL_PEN("Ball"),
    BRUSH_PEN("Brush"),
    PENCIL("Pencil"),
    HIGHLIGHTER("Marker"),
    ERASER("Eraser"),
    SHAPE("Shapes"),
    TAPE("Tape"),
    LASER("Laser"),
    TEXT("Text"),
    STICKY("Sticky"),
    LASSO("Lasso"),
}

/** Tools shown in the editor's primary tool row, in design order. */
val PrimaryTools: List<ToolType> = listOf(
    ToolType.FOUNTAIN_PEN,
    ToolType.BALL_PEN,
    ToolType.BRUSH_PEN,
    ToolType.PENCIL,
    ToolType.HIGHLIGHTER,
    ToolType.ERASER,
    ToolType.LASSO,
    ToolType.TEXT,
    ToolType.STICKY,
)

/** Tools that make sense as the editor's startup tool. */
val DefaultToolChoices: List<ToolType> = listOf(
    ToolType.FOUNTAIN_PEN,
    ToolType.BALL_PEN,
    ToolType.BRUSH_PEN,
    ToolType.PENCIL,
    ToolType.HIGHLIGHTER,
    ToolType.TEXT,
    ToolType.STICKY,
)

/** Tools kept out of the primary row, reachable from the editor's overflow menu. */
val SecondaryTools: List<ToolType> = listOf(
    ToolType.SHAPE,
    ToolType.TAPE,
    ToolType.LASER,
)

/** Tools that leave a mark on the page. Laser is a transient pointer and is never stored. */
val PersistentTools: Set<ToolType> = setOf(
    ToolType.FOUNTAIN_PEN,
    ToolType.BALL_PEN,
    ToolType.BRUSH_PEN,
    ToolType.PENCIL,
    ToolType.HIGHLIGHTER,
    ToolType.ERASER,
    ToolType.SHAPE,
    ToolType.TAPE,
    ToolType.TEXT,
    ToolType.STICKY,
    ToolType.LASSO,
)

enum class ShapeType(val label: String) {
    LINE("Line"),
    ARROW("Arrow"),
    RECTANGLE("Rectangle"),
    ELLIPSE("Ellipse"),
}

/** How the eraser treats whatever it touches. */
enum class EraserMode(val label: String) {
    /** Split the touched stroke where the eraser passed over it. */
    PARTIAL("Partial"),

    /** Remove every whole stroke the eraser touches. */
    OBJECT("Object"),
}

/** Horizontal alignment of wrapped text inside its box. */
enum class TextAlignment(val label: String) {
    LEFT("Left"),
    CENTER("Center"),
    RIGHT("Right"),
}

/** Default text-box width in page units. */
const val DefaultTextWidth = 420f

/** List decoration applied when the text is rendered. Stored text stays plain. */
enum class TextListStyle(val label: String) {
    NONE("None"),
    BULLET("Bullets"),
    NUMBERED("Numbers"),
}

/** Font and paragraph formatting for a text box. */
data class TextFormatting(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val listStyle: TextListStyle = TextListStyle.NONE,
    val lineSpacing: Float = 1f,
    val highlightColor: Long? = null,
) {
    /** Prefix list lines at render time so the stored text remains editable as plain text. */
    fun decorate(text: String): String {
        if (listStyle == TextListStyle.NONE) return text
        var number = 0
        return text.split('\n').joinToString("\n") { line ->
            if (line.isBlank()) {
                line
            } else {
                when (listStyle) {
                    TextListStyle.BULLET -> "•  $line"
                    TextListStyle.NUMBERED -> {
                        number++
                        "$number.  $line"
                    }
                    TextListStyle.NONE -> line
                }
            }
        }
    }
}

enum class PageBackground(val label: String) {
    BLANK("Blank paper"),
    LINED("Ruled · 8 mm"),
    GRID("Squared graph"),
    DOTTED("Dotted · 5 mm"),
    CORNELL("Cornell notes"),
    PLANNER("Daily planner"),
    WEEKLY("Weekly planner"),
    MUSIC("Music staff"),
    STORYBOARD("Storyboard"),
    CHECKLIST("Checklist"),
}

/** Theme preference exposed by the dedicated settings screen. */
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

/** App-wide preferences persisted separately from per-tool settings. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultTool: ToolType = ToolType.BALL_PEN,
    val defaultPageBackground: PageBackground? = null,
    val showAutosaveIndicator: Boolean = true,
    val stylusOnlyMode: Boolean = false,
)

/** A point in page coordinates, with the touch pressure recorded at input time. */
data class Pt(
    val x: Float,
    val y: Float,
    val pressure: Float = 0.5f,
)

data class Stroke(
    val id: String,
    val tool: ToolType,
    val color: Long,
    val width: Float,
    val points: List<Pt>,
    val shapeType: ShapeType? = null,
    val seed: Int = 0,
) {
    init {
        require(points.isNotEmpty()) { "A stroke needs at least one point" }
    }
}

data class TextItem(
    val id: String,
    val x: Float,
    val y: Float,
    val text: String,
    val color: Long,
    val fontSize: Float = 34f,
    /** Width of the text box in page units. `0f` means a legacy auto-width box. */
    val width: Float = 0f,
    val alignment: TextAlignment = TextAlignment.LEFT,
    val formatting: TextFormatting = TextFormatting(),
) {
    /** The text as rendered, including any list markers. */
    val displayText: String get() = formatting.decorate(text)
}

/** A lightweight colored note card pinned to a page. */
data class StickyNote(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float = 280f,
    val height: Float = 202f,
    val text: String,
    val color: Long = StickyYellow,
    val fontSize: Float = 28f,
)

data class Page(
    val id: String,
    val background: PageBackground = PageBackground.BLANK,
    val strokes: List<Stroke> = emptyList(),
    val texts: List<TextItem> = emptyList(),
    val stickies: List<StickyNote> = emptyList(),
    val bookmarked: Boolean = false,
) {
    val isEmpty: Boolean get() = strokes.isEmpty() && texts.isEmpty() && stickies.isEmpty()
}

data class Note(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pages: List<Page>,
    val coverTone: String? = null,
    val pinned: Boolean = false,
) {
    val pageCount: Int get() = pages.size
}

fun newId(): String = UUID.randomUUID().toString()

fun blankPage(background: PageBackground = PageBackground.BLANK): Page =
    Page(id = newId(), background = background)

fun newNote(
    title: String = "Untitled notebook",
    background: PageBackground = PageBackground.BLANK,
): Note {
    val now = System.currentTimeMillis()
    return Note(
        id = newId(),
        title = title,
        createdAt = now,
        updatedAt = now,
        pages = listOf(blankPage(background)),
    )
}

fun Note.duplicate(): Note {
    val now = System.currentTimeMillis()
    return copy(
        id = newId(),
        title = if (title.isBlank()) "Untitled notebook copy" else "$title copy",
        createdAt = now,
        updatedAt = now,
        pages = pages.map { page -> page.duplicate() },
    )
}

fun Page.duplicate(): Page = copy(
    id = newId(),
    strokes = strokes.map { it.copy(id = newId()) },
    texts = texts.map { it.copy(id = newId()) },
    stickies = stickies.map { it.copy(id = newId()) },
)

val DefaultToolWidths: Map<ToolType, Float> = mapOf(
    ToolType.FOUNTAIN_PEN to 5f,
    ToolType.BALL_PEN to 3f,
    ToolType.BRUSH_PEN to 12f,
    ToolType.PENCIL to 4f,
    ToolType.HIGHLIGHTER to 22f,
    ToolType.ERASER to 26f,
    ToolType.SHAPE to 4f,
    ToolType.TAPE to 26f,
    ToolType.LASER to 5f,
    ToolType.TEXT to 34f,
    ToolType.STICKY to 280f,
    ToolType.LASSO to 4f,
)

const val InkBlack: Long = 0xFF0F172AL
const val HighlightYellow: Long = 0xFFFEF08AL
const val DefaultShapeColor: Long = 0xFF2563EBL
const val PencilGrey: Long = 0xFF475569L
const val LaserRed: Long = 0xFFEF4444L
const val TapeColor: Long = 0xFFFDE68AL
const val StickyYellow: Long = 0xFFFEF08AL

val DefaultToolColors: Map<ToolType, Long> = mapOf(
    ToolType.FOUNTAIN_PEN to InkBlack,
    ToolType.BALL_PEN to InkBlack,
    ToolType.BRUSH_PEN to InkBlack,
    ToolType.PENCIL to PencilGrey,
    ToolType.HIGHLIGHTER to HighlightYellow,
    ToolType.ERASER to InkBlack,
    ToolType.SHAPE to DefaultShapeColor,
    ToolType.TAPE to TapeColor,
    ToolType.LASER to LaserRed,
    ToolType.TEXT to InkBlack,
    ToolType.STICKY to StickyYellow,
    ToolType.LASSO to DefaultShapeColor,
)
