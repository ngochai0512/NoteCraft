package com.notecraft.ui.editor

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixNormal
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.notecraft.model.ShapeType
import com.notecraft.model.ToolType
import com.notecraft.ui.EditorState
import com.notecraft.ui.theme.InkPalette

private fun toolIcon(tool: ToolType): ImageVector = when (tool) {
    ToolType.PEN -> Icons.Outlined.Create
    ToolType.PENCIL -> Icons.Outlined.Edit
    ToolType.HIGHLIGHTER -> Icons.Outlined.Brush
    ToolType.ERASER -> Icons.Outlined.AutoFixNormal
    ToolType.SHAPE -> Icons.Outlined.Category
    ToolType.TEXT -> Icons.Outlined.TextFields
    ToolType.LASSO -> Icons.Outlined.Gesture
}

private fun toolShortLabel(tool: ToolType): String = when (tool) {
    ToolType.HIGHLIGHTER -> "Marker"
    else -> tool.label
}

@Composable
fun EditorBottomBar(
    editor: EditorState,
    onToolSelected: (ToolType) -> Unit,
    onColorSelected: (Long) -> Unit,
    onWidthChange: (Float) -> Unit,
    onShapeSelected: (ShapeType) -> Unit,
    onOpenColorPicker: () -> Unit,
    onDeleteSelection: () -> Unit,
    onDuplicateSelection: () -> Unit,
    onClearSelection: () -> Unit,
    onClearPage: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (editor.hasSelection) {
                SelectionOptions(
                    count = editor.selectionCount,
                    onDuplicate = onDuplicateSelection,
                    onDelete = onDeleteSelection,
                    onClear = onClearSelection,
                )
            } else {
                ToolOptions(
                    editor = editor,
                    onColorSelected = onColorSelected,
                    onWidthChange = onWidthChange,
                    onShapeSelected = onShapeSelected,
                    onOpenColorPicker = onOpenColorPicker,
                    onClearPage = onClearPage,
                )
            }
            Spacer(Modifier.height(4.dp))
            ToolRow(
                selectedTool = editor.tool,
                onToolSelected = onToolSelected,
            )
        }
    }
}

@Composable
private fun ToolRow(
    selectedTool: ToolType,
    onToolSelected: (ToolType) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolType.entries.forEach { tool ->
            val selected = tool == selectedTool
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onToolSelected(tool) }
                    .padding(horizontal = 2.dp, vertical = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = toolIcon(tool),
                        contentDescription = tool.label,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(21.dp),
                    )
                }
                Text(
                    text = toolShortLabel(tool),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ToolOptions(
    editor: EditorState,
    onColorSelected: (Long) -> Unit,
    onWidthChange: (Float) -> Unit,
    onShapeSelected: (ShapeType) -> Unit,
    onOpenColorPicker: () -> Unit,
    onClearPage: () -> Unit,
) {
    Column {
        if (editor.tool == ToolType.ERASER) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Eraser size",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = editor.width,
                    onValueChange = onWidthChange,
                    valueRange = 8f..60f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                )
                WidthPreview(color = 0xFF8A8794, width = editor.width, maxWidth = 60f)
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = onClearPage) {
                    Icon(
                        Icons.Outlined.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Clear page")
                }
            }
            return
        }

        if (editor.tool == ToolType.LASSO) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Gesture,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Draw around items to select, then drag to move.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val customColor = editor.color !in InkPalette
            InkPalette.forEach { swatch ->
                val selected = editor.color == swatch
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(swatch.toInt()))
                        .border(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            },
                            shape = CircleShape,
                        )
                        .clickable { onColorSelected(swatch) },
                )
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = if (customColor) 2.5.dp else 1.dp,
                        color = if (customColor) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        },
                        shape = CircleShape,
                    )
                    .clickable(onClick = onOpenColorPicker),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Palette,
                    contentDescription = "Custom color",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (editor.tool == ToolType.TEXT) "Text size" else "Size",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(58.dp),
            )
            Slider(
                value = editor.width,
                onValueChange = onWidthChange,
                valueRange = widthRange(editor.tool),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
            )
            WidthPreview(
                color = editor.color,
                width = editor.width,
                maxWidth = widthRange(editor.tool).endInclusive,
            )
        }

        if (editor.tool == ToolType.SHAPE) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShapeType.entries.forEach { shape ->
                    FilterChip(
                        selected = editor.shapeType == shape,
                        onClick = { onShapeSelected(shape) },
                        label = { Text(shape.label) },
                    )
                }
            }
        }
    }
}

private fun widthRange(tool: ToolType): ClosedFloatingPointRange<Float> = when (tool) {
    ToolType.TEXT -> 16f..96f
    ToolType.HIGHLIGHTER -> 8f..48f
    ToolType.ERASER -> 8f..60f
    else -> 1f..24f
}

@Composable
private fun WidthPreview(color: Long, width: Float, maxWidth: Float) {
    Canvas(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        val fraction = (width / maxWidth).coerceIn(0.05f, 1f)
        drawCircle(
            color = Color(color.toInt()),
            radius = (2f + fraction * 10f).dp.toPx(),
            center = Offset(size.width / 2f, size.height / 2f),
        )
    }
}

@Composable
private fun SelectionOptions(
    count: Int,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$count selected",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDuplicate) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(4.dp))
            Text("Duplicate")
        }
        TextButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("Delete", color = MaterialTheme.colorScheme.error)
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onClear),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Clear selection",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun ColorPickerDialog(
    initialColor: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColor.toInt(), it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2]) }

    val previewArgb = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness))
    val previewLong = previewArgb.toLong() and 0xFFFFFFFFL
    val hex = "#%06X".format(previewArgb and 0xFFFFFF)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom color") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(previewLong.toInt()))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(14.dp),
                            ),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = hex,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("Hue", style = MaterialTheme.typography.labelMedium)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Red,
                                    Color.Yellow,
                                    Color.Green,
                                    Color.Cyan,
                                    Color.Blue,
                                    Color.Magenta,
                                    Color.Red,
                                ),
                            ),
                        ),
                )
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text("Saturation", style = MaterialTheme.typography.labelMedium)
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f)
                Text("Brightness", style = MaterialTheme.typography.labelMedium)
                Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 0f..1f)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(previewLong) }) {
                Text("Use color")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
