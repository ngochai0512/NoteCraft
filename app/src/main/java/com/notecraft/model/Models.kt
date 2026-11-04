package com.notecraft.model

import java.util.UUID

/** Logical page size in page units. Strokes are stored in this coordinate space. */
const val PAGE_WIDTH = 1000f
const val PAGE_HEIGHT = 1414f

enum class ToolType(val label: String) {
    PEN("Pen"),
    PENCIL("Pencil"),
    HIGHLIGHTER("Highlighter"),
    ERASER("Eraser"),
    SHAPE("Shape"),
    TEXT("Text"),
    LASSO("Lasso"),
}

enum class ShapeType(val label: String) {
    LINE("Line"),
    ARROW("Arrow"),
    RECTANGLE("Rectangle"),
    ELLIPSE("Ellipse"),
}

enum class PageBackground(val label: String) {
    BLANK("Blank"),
    LINED("Lined"),
    GRID("Grid"),
    DOTTED("Dotted"),
}

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
)

data class Page(
    val id: String,
    val background: PageBackground = PageBackground.BLANK,
    val strokes: List<Stroke> = emptyList(),
    val texts: List<TextItem> = emptyList(),
) {
    val isEmpty: Boolean get() = strokes.isEmpty() && texts.isEmpty()
}

data class Note(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pages: List<Page>,
) {
    val pageCount: Int get() = pages.size
}

fun newId(): String = UUID.randomUUID().toString()

fun blankPage(background: PageBackground = PageBackground.BLANK): Page =
    Page(id = newId(), background = background)

fun newNote(title: String = "Untitled note"): Note {
    val now = System.currentTimeMillis()
    return Note(
        id = newId(),
        title = title,
        createdAt = now,
        updatedAt = now,
        pages = listOf(blankPage()),
    )
}

fun Note.duplicate(): Note {
    val now = System.currentTimeMillis()
    return copy(
        id = newId(),
        title = if (title.isBlank()) "Untitled note copy" else "$title copy",
        createdAt = now,
        updatedAt = now,
        pages = pages.map { page -> page.duplicate() },
    )
}

fun Page.duplicate(): Page = copy(
    id = newId(),
    strokes = strokes.map { it.copy(id = newId()) },
    texts = texts.map { it.copy(id = newId()) },
)

val DefaultToolWidths: Map<ToolType, Float> = mapOf(
    ToolType.PEN to 4.5f,
    ToolType.PENCIL to 4f,
    ToolType.HIGHLIGHTER to 22f,
    ToolType.ERASER to 26f,
    ToolType.SHAPE to 4f,
    ToolType.TEXT to 34f,
    ToolType.LASSO to 4f,
)

const val InkBlack: Long = 0xFF23222BL
const val HighlightYellow: Long = 0xFFFFD966L
const val DefaultShapeColor: Long = 0xFF5B4B8AL
const val PencilGrey: Long = 0xFF3C3B44L

val DefaultToolColors: Map<ToolType, Long> = mapOf(
    ToolType.PEN to InkBlack,
    ToolType.PENCIL to PencilGrey,
    ToolType.HIGHLIGHTER to HighlightYellow,
    ToolType.SHAPE to DefaultShapeColor,
    ToolType.TEXT to InkBlack,
    ToolType.ERASER to InkBlack,
    ToolType.LASSO to DefaultShapeColor,
)
