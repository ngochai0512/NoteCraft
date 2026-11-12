package com.notecraft.ui.editor

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.notecraft.model.PAGE_HEIGHT
import com.notecraft.model.PAGE_WIDTH
import com.notecraft.model.Page
import com.notecraft.ui.components.NoteThumbnail
import com.notecraft.util.pageReorderTarget
import com.notecraft.ui.theme.HighlighterAmber

@Composable
internal fun PageIndicator(
    pageIndex: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(50),
        ),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
    ) {
        Text(
            text = "PAGE ${pageIndex + 1} / $pageCount",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
internal fun PageDrawer(
    pages: List<Page>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onToggleBookmark: (Int) -> Unit,
    onReorder: (Int, Int) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val columns = when {
        isTablet -> minOf(6, maxOf(3, configuration.screenWidthDp / 120))
        isLandscape -> 4
        else -> 3
    }
    val maxGridHeight = if (configuration.screenHeightDp < 480) 220.dp else 440.dp
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Pages",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${pages.size} ${if (pages.size == 1) "PAGE" else "PAGES"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tap to open · long-press and drag to reorder",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val gap = 10.dp
            val cellWidth = (maxWidth - gap * (columns - 1).toFloat()) / columns.toFloat()
            val tileHeight = cellWidth * (PAGE_HEIGHT / PAGE_WIDTH) + 18.dp
            val rows = (pages.size + 1 + columns - 1) / columns
            val gridHeight = minOf(
                tileHeight * rows.toFloat() + gap * (rows - 1).toFloat(),
                maxGridHeight,
            )
            val density = LocalDensity.current
            val cellWidthPx = with(density) { cellWidth.toPx() }
            val rowHeightPx = with(density) { (tileHeight + 12.dp).toPx() }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(gridHeight),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(pages) { index, page ->
                    PageTile(
                        page = page,
                        index = index,
                        current = index == currentIndex,
                        dragging = draggingIndex == index,
                        dragOffset = if (draggingIndex == index) dragOffset else Offset.Zero,
                        onClick = { onSelect(index) },
                        onToggleBookmark = { onToggleBookmark(index) },
                        onDragStart = {
                            draggingIndex = index
                            dragOffset = Offset.Zero
                        },
                        onDrag = { amount -> dragOffset += amount },
                        onDragEnd = {
                            val from = draggingIndex
                            if (from != null) {
                                val target = pageReorderTarget(
                                    index = from,
                                    dragX = dragOffset.x,
                                    dragY = dragOffset.y,
                                    columns = columns,
                                    cellWidthPx = cellWidthPx,
                                    rowHeightPx = rowHeightPx,
                                    lastIndex = pages.lastIndex,
                                )
                                if (target != from) onReorder(from, target)
                            }
                            draggingIndex = null
                            dragOffset = Offset.Zero
                        },
                        onDragCancel = {
                            draggingIndex = null
                            dragOffset = Offset.Zero
                        },
                    )
                }
                item {
                    AddPageTile(onClick = onAdd)
                }
            }
        }
    }
}


@Composable
private fun PageTile(
    page: Page,
    index: Int,
    current: Boolean,
    dragging: Boolean,
    dragOffset: Offset,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationX = dragOffset.x
                translationY = dragOffset.y
                val dragScale = if (dragging) 1.05f else 1f
                scaleX = dragScale
                scaleY = dragScale
                shadowElevation = if (dragging) 18.dp.toPx() else 0f
                alpha = if (dragging) 0.94f else 1f
            }
            .pointerInput(page.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                )
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PAGE_WIDTH / PAGE_HEIGHT)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = if (current) 2.dp else 1.dp,
                    color = if (current) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = RoundedCornerShape(6.dp),
                ),
        ) {
            NoteThumbnail(
                page = page,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                    .clickable(
                        onClickLabel = if (page.bookmarked) "Remove bookmark" else "Bookmark page",
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClick = onToggleBookmark,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (page.bookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (page.bookmarked) "Remove bookmark" else "Bookmark page",
                    tint = if (page.bookmarked) {
                        HighlighterAmber
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    },
                    modifier = Modifier.size(16.dp),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.80f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.DragIndicator,
                    contentDescription = "Drag to reorder page",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "PAGE ${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = if (current) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun AddPageTile(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PAGE_WIDTH / PAGE_HEIGHT)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "Add page",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "ADD PAGE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}
