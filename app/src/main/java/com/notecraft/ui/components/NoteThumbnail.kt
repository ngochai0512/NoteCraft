package com.notecraft.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.notecraft.model.PAGE_HEIGHT
import com.notecraft.model.PAGE_WIDTH
import com.notecraft.model.Page
import com.notecraft.ui.editor.drawPageContent
import kotlin.math.min

@Composable
fun NoteThumbnail(
    page: Page?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(Color.White),
    ) {
        if (page != null) {
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
    }
}
