package com.notecraft.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
internal fun CanvasZoomDock(
    compactHeight: Boolean,
    zoomPercent: Int,
    rulerEnabled: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    onShowPages: () -> Unit,
    onShowPageBackground: () -> Unit,
    onToggleRuler: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            modifier = Modifier.border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(16.dp),
            ),
        ) {
            if (compactHeight) {
                Row(
                    modifier = Modifier.padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ZoomControlButton(
                        icon = Icons.Outlined.ZoomOut,
                        description = "Zoom out",
                        onClick = onZoomOut,
                    )
                    Text(
                        text = "$zoomPercent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                    ZoomControlButton(
                        icon = Icons.Outlined.ZoomIn,
                        description = "Zoom in",
                        onClick = onZoomIn,
                    )
                    ZoomControlButton(
                        icon = Icons.Outlined.FitScreen,
                        description = "Reset view",
                        onClick = onReset,
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = 1.dp, height = 20.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    ZoomControlButton(
                        icon = Icons.Outlined.GridView,
                        description = "Page thumbnails",
                        onClick = onShowPages,
                    )
                    ZoomControlButton(
                        icon = Icons.Outlined.GridOn,
                        description = "Page background",
                        onClick = onShowPageBackground,
                    )
                    ZoomControlButton(
                        icon = Icons.Outlined.Straighten,
                        description = "Ruler",
                        active = rulerEnabled,
                        onClick = onToggleRuler,
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ZoomControlButton(
                        icon = Icons.Outlined.ZoomIn,
                        description = "Zoom in",
                        onClick = onZoomIn,
                    )
                    Text(
                        text = "$zoomPercent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ZoomControlButton(
                        icon = Icons.Outlined.ZoomOut,
                        description = "Zoom out",
                        onClick = onZoomOut,
                    )
                    ZoomControlButton(
                        icon = Icons.Outlined.FitScreen,
                        description = "Reset view",
                        onClick = onReset,
                    )
                    Box(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .size(width = 20.dp, height = 1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    ZoomControlButton(
                        icon = Icons.Outlined.GridView,
                        description = "Page thumbnails",
                        onClick = onShowPages,
                    )
                    ZoomControlButton(
                        icon = Icons.Outlined.GridOn,
                        description = "Page background",
                        onClick = onShowPageBackground,
                    )
                    ZoomControlButton(
                        icon = Icons.Outlined.Straighten,
                        description = "Ruler",
                        active = rulerEnabled,
                        onClick = onToggleRuler,
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomControlButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun RulerHandle(
    modifier: Modifier,
    onDrag: (Offset) -> Unit,
) {
    Surface(
        modifier = modifier
            .size(48.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.Straighten,
                contentDescription = "Drag ruler; slide sideways to rotate",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
