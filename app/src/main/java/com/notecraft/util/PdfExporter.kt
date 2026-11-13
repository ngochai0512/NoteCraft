package com.notecraft.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.notecraft.model.Note
import com.notecraft.model.PAGE_WIDTH
import com.notecraft.model.Page
import com.notecraft.ui.editor.drawPageContent
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a notebook to a PDF chosen through the system document picker.
 *
 * Pages are rasterized at 150 dpi and placed on A4 pages. Rendering through the existing
 * [drawPageContent] composable renderer keeps paper, backgrounds, strokes, and text identical to
 * what the user sees in the editor. Exports always use the light paper palette, even when the
 * editor is in dark-paper mode, so shared and printed documents stay print-friendly.
 */
object PdfExporter {

    private const val PDF_PAGE_WIDTH = 595
    private const val PDF_PAGE_HEIGHT = 842
    private const val PAGE_BITMAP_WIDTH = 1240
    private const val PAGE_BITMAP_HEIGHT = 1754

    fun fileName(title: String): String = "${cleanedTitle(title)}.pdf"

    fun pageFileName(title: String, pageNumber: Int): String =
        "${cleanedTitle(title)} - Page $pageNumber.pdf"

    fun rangeFileName(title: String, startPage: Int, endPage: Int): String =
        "${cleanedTitle(title)} - Pages $startPage-$endPage.pdf"

    private fun cleanedTitle(title: String): String = title
        .removeSuffix(".pdf")
        .replace(Regex("[\\\\/:*?\"<>|]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { "NoteCraft" }

    suspend fun export(context: Context, uri: Uri, note: Note) =
        exportPages(context, uri, note.pages)

    suspend fun exportPage(context: Context, uri: Uri, page: Page) =
        exportPages(context, uri, listOf(page))

    suspend fun exportPages(context: Context, uri: Uri, pages: List<Page>) = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, page ->
                val bitmap = renderPage(page)
                try {
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        PDF_PAGE_WIDTH,
                        PDF_PAGE_HEIGHT,
                        index + 1,
                    ).create()
                    val pdfPage = document.startPage(pageInfo)
                    pdfPage.canvas.drawColor(Color.WHITE)
                    pdfPage.canvas.drawBitmap(
                        bitmap,
                        null,
                        RectF(
                            0f,
                            0f,
                            PDF_PAGE_WIDTH.toFloat(),
                            PDF_PAGE_HEIGHT.toFloat(),
                        ),
                        Paint().apply { isFilterBitmap = true },
                    )
                    document.finishPage(pdfPage)
                } finally {
                    bitmap.recycle()
                }
            }
            context.contentResolver.openOutputStream(uri)?.use { output ->
                document.writeTo(output)
            } ?: throw IOException("Unable to open the selected PDF destination")
        } finally {
            document.close()
        }
    }

    fun renderPage(page: Page): Bitmap {
        val scale = PAGE_BITMAP_WIDTH / PAGE_WIDTH
        val bitmap = Bitmap.createBitmap(
            PAGE_BITMAP_WIDTH,
            PAGE_BITMAP_HEIGHT,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = ComposeCanvas(canvas),
            size = Size(PAGE_BITMAP_WIDTH.toFloat(), PAGE_BITMAP_HEIGHT.toFloat()),
        ) {
            drawPageContent(
                page = page,
                scale = scale,
                origin = Offset.Zero,
                drawSelection = false,
                drawPaper = true,
                drawBackground = true,
                clip = true,
            )
        }
        return bitmap
    }
}
