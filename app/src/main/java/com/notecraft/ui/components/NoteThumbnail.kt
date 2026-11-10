package com.notecraft.ui.components

import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.createBitmap
import com.notecraft.model.PAGE_HEIGHT
import com.notecraft.model.PAGE_WIDTH
import com.notecraft.model.Page
import com.notecraft.ui.editor.drawPageContent
import com.notecraft.ui.theme.PaperSurface
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val THUMBNAIL_WIDTH = 240
private const val THUMBNAIL_HEIGHT = 339
private const val THUMBNAIL_CACHE_SIZE = 64

private object PageThumbnailCache {
    private val cache = LruCache<String, ImageBitmap>(THUMBNAIL_CACHE_SIZE)

    fun getOrRender(key: String, page: Page): ImageBitmap {
        cache.get(key)?.let { return it }
        val bitmap = createBitmap(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
        val canvas = Canvas(bitmap)
        val scale = THUMBNAIL_WIDTH / PAGE_WIDTH
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = androidx.compose.ui.graphics.Canvas(canvas),
            size = Size(THUMBNAIL_WIDTH.toFloat(), THUMBNAIL_HEIGHT.toFloat()),
        ) {
            drawPageContent(
                page = page,
                scale = scale,
                origin = Offset.Zero,
                drawPaper = true,
                clip = true,
            )
        }
        val image = bitmap.asImageBitmap()
        cache.put(key, image)
        return image
    }
}

@Composable
fun NoteThumbnail(
    page: Page?,
    modifier: Modifier = Modifier,
    cacheKey: String? = null,
) {
    Box(
        modifier = modifier.background(PaperSurface),
    ) {
        if (page != null) {
            val image = if (cacheKey != null) {
                val state = produceState<ImageBitmap?>(initialValue = null, cacheKey) {
                    value = withContext(Dispatchers.Default) {
                        PageThumbnailCache.getOrRender(cacheKey, page)
                    }
                }
                state.value
            } else {
                null
            }
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LiveThumbnail(page = page)
            }
        }
    }
}

@Composable
private fun LiveThumbnail(page: Page) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scale = min(size.width / PAGE_WIDTH, size.height / PAGE_HEIGHT)
        val width = PAGE_WIDTH * scale
        val height = PAGE_HEIGHT * scale
        val origin = Offset(
            (size.width - width) / 2f,
            (size.height - height) / 2f,
        )
        drawPageContent(
            page = page,
            scale = scale,
            origin = origin,
            drawPaper = true,
        )
    }
}
