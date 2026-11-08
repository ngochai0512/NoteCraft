package com.notecraft.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.notecraft.model.Page
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Renders a page to a PNG in the cache and opens the Android share sheet. */
object PageSharer {

    suspend fun sharePage(
        context: Context,
        page: Page,
        noteTitle: String,
        pageNumber: Int,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val bitmap = runCatching { PdfExporter.renderPage(page) }
            .getOrElse { return@withContext Result.failure(it) }
        try {
            val directory = File(context.cacheDir, "shared").apply { mkdirs() }
            pruneOldFiles(directory)
            val file = File(
                directory,
                "${safeName(noteTitle)} - Page $pageNumber.png",
            )
            file.outputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    error("Unable to encode the page as PNG")
                }
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "$noteTitle — Page $pageNumber")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(Intent.createChooser(intent, "Share page"))
            }
            Result.success(Unit)
        } catch (exception: Exception) {
            Result.failure(exception)
        } finally {
            bitmap.recycle()
        }
    }

    private fun pruneOldFiles(directory: File) {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    private fun safeName(title: String): String = title
        .replace(Regex("[\\\\/:*?\"<>|]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { "NoteCraft" }
}
