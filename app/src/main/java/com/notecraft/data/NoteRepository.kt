package com.notecraft.data

import android.content.Context
import com.notecraft.model.Note
import com.notecraft.model.Page
import com.notecraft.model.PageBackground
import com.notecraft.model.Pt
import com.notecraft.model.ShapeType
import com.notecraft.model.Stroke
import com.notecraft.model.TextAlignment
import com.notecraft.model.TextItem
import com.notecraft.model.ToolType
import com.notecraft.model.blankPage
import com.notecraft.model.newId
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Notes are persisted as individual JSON files under filesDir/notes.
 * This keeps the format inspectable and avoids a database dependency.
 */
class NoteRepository(context: Context) {

    private val directory = File(context.filesDir, "notes").apply { mkdirs() }
    private val mutex = Mutex()

    suspend fun loadAll(): List<Note> = withContext(Dispatchers.IO) {
        mutex.withLock {
            directory.listFiles { file -> file.isFile && file.extension == "json" }
                ?.mapNotNull { file ->
                    runCatching { parseNote(JSONObject(file.readText())) }.getOrNull()
                }
                ?.sortedByDescending { it.updatedAt }
                ?: emptyList()
        }
    }

    suspend fun save(note: Note) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val target = File(directory, "${note.id}.json")
            val temp = File(directory, "${note.id}.json.tmp")
            temp.writeText(note.toJson().toString())
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            Unit
        }
    }

    suspend fun delete(noteId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            File(directory, "$noteId.json").delete()
            File(directory, "$noteId.json.tmp").delete()
            Unit
        }
    }

    private fun Note.toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("id", id)
        put("title", title)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        if (coverTone != null) put("coverTone", coverTone)
        if (pinned) put("pinned", true)
        put("pages", JSONArray().apply { pages.forEach { put(it.toJson()) } })
    }

    private fun Page.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("background", background.name)
        if (bookmarked) put("bookmarked", true)
        put("strokes", JSONArray().apply { strokes.forEach { put(it.toJson()) } })
        put("texts", JSONArray().apply { texts.forEach { put(it.toJson()) } })
    }

    private fun Stroke.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("tool", tool.name)
        put("color", color)
        put("width", width.toDouble())
        put("seed", seed)
        if (shapeType != null) put("shapeType", shapeType.name)
        put("points", JSONArray().apply {
            points.forEach { point ->
                put(JSONArray().apply {
                    put(point.x.toDouble())
                    put(point.y.toDouble())
                    put(point.pressure.toDouble())
                })
            }
        })
    }

    private fun TextItem.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("text", text)
        put("color", color)
        put("fontSize", fontSize.toDouble())
        if (width > 0f) put("width", width.toDouble())
        if (alignment != TextAlignment.LEFT) put("alignment", alignment.name)
    }

    private fun parseNote(json: JSONObject): Note {
        val now = System.currentTimeMillis()
        val pagesJson = json.optJSONArray("pages")
        val pages = buildList {
            if (pagesJson != null) {
                for (i in 0 until pagesJson.length()) {
                    pagesJson.optJSONObject(i)?.let { add(parsePage(it)) }
                }
            }
        }.ifEmpty { listOf(blankPage()) }

        return Note(
            id = json.optString("id").ifBlank { newId() },
            title = json.optString("title").ifBlank { "Untitled notebook" },
            createdAt = json.optLong("createdAt", now),
            updatedAt = json.optLong("updatedAt", now),
            pages = pages,
            coverTone = json.optString("coverTone").takeIf { it.isNotBlank() },
            pinned = json.optBoolean("pinned", false),
        )
    }

    private fun parsePage(json: JSONObject): Page {
        val strokesJson = json.optJSONArray("strokes")
        val strokes = buildList {
            if (strokesJson != null) {
                for (i in 0 until strokesJson.length()) {
                    val stroke = strokesJson.optJSONObject(i) ?: continue
                    parseStroke(stroke)?.let { add(it) }
                }
            }
        }
        val textsJson = json.optJSONArray("texts")
        val texts = buildList {
            if (textsJson != null) {
                for (i in 0 until textsJson.length()) {
                    textsJson.optJSONObject(i)?.let { add(parseText(it)) }
                }
            }
        }
        return Page(
            id = json.optString("id").ifBlank { newId() },
            background = enumValueOrDefault(json.optString("background"), PageBackground.BLANK),
            strokes = strokes,
            texts = texts,
            bookmarked = json.optBoolean("bookmarked", false),
        )
    }

    private fun parseStroke(json: JSONObject): Stroke? {
        val pointsJson = json.optJSONArray("points") ?: return null
        val points = buildList {
            for (i in 0 until pointsJson.length()) {
                val pointJson = pointsJson.optJSONArray(i) ?: continue
                if (pointJson.length() < 2) continue
                add(
                    Pt(
                        x = pointJson.optDouble(0).toFloat(),
                        y = pointJson.optDouble(1).toFloat(),
                        pressure = pointJson.optDouble(2, 0.5).toFloat(),
                    ),
                )
            }
        }
        if (points.isEmpty()) return null
        return Stroke(
            id = json.optString("id").ifBlank { newId() },
            tool = parseTool(json.optString("tool")),
            color = json.optLong("color", 0xFF23222BL),
            width = json.optDouble("width", 4.0).toFloat(),
            points = points,
            shapeType = json.optString("shapeType").takeIf { it.isNotBlank() }?.let { raw ->
                enumValueOrDefault(raw, ShapeType.LINE)
            },
            seed = json.optInt("seed", 0),
        )
    }

    private fun parseText(json: JSONObject): TextItem = TextItem(
        id = json.optString("id").ifBlank { newId() },
        x = json.optDouble("x", 0.0).toFloat(),
        y = json.optDouble("y", 0.0).toFloat(),
        text = json.optString("text"),
        color = json.optLong("color", 0xFF23222BL),
        fontSize = json.optDouble("fontSize", 34.0).toFloat(),
        width = json.optDouble("width", 0.0).toFloat(),
        alignment = enumValueOrDefault(json.optString("alignment"), TextAlignment.LEFT),
    )

    /**
     * Older notes stored `PEN`; it maps to the ball pen so their strokes keep rendering.
     * Unknown tools fall back to the ball pen instead of dropping the stroke.
     */
    private fun parseTool(raw: String): ToolType = when (raw) {
        "PEN" -> ToolType.BALL_PEN
        else -> runCatching { ToolType.valueOf(raw) }.getOrDefault(ToolType.BALL_PEN)
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, default: T): T =
        runCatching { enumValueOf<T>(raw.orEmpty()) }.getOrDefault(default)
}
