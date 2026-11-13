package com.notecraft.ui.editor

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notecraft.model.EraserMode
import com.notecraft.model.ShapeType
import com.notecraft.model.TextAlignment
import com.notecraft.model.ToolType
import com.notecraft.ui.EditorState
import com.notecraft.ui.theme.InkPalette
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun ToolOptions(
    editor: EditorState,
    compact: Boolean,
    onColorSelected: (Long) -> Unit,
    onWidthChange: (Float) -> Unit,
    onStickyFontSizeChange: (Float) -> Unit,
    onShapeSelected: (ShapeType) -> Unit,
    onEraserModeSelected: (EraserMode) -> Unit,
    onTextAlignmentSelected: (TextAlignment) -> Unit,
    onOpenColorPicker: () -> Unit,
    onClearPage: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (editor.tool == ToolType.ERASER) {
            Column {
                Text(
                    text = "Eraser type",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EraserMode.entries.forEach { mode ->
                        FilterChip(
                            selected = editor.eraserMode == mode,
                            onClick = { onEraserModeSelected(mode) },
                            label = { Text(mode.label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(
                    text = if (editor.eraserMode == EraserMode.OBJECT) {
                        "Touching a stroke removes the whole stroke."
                    } else {
                        "Touching a stroke removes only that portion."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Eraser size",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(64.dp),
                    )
                    Slider(
                        value = editor.width,
                        onValueChange = onWidthChange,
                        valueRange = 8f..60f,
                        colors = sliderColors(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                    )
                    WidthPreview(color = 0xFF8A8794, width = editor.width, maxWidth = 60f)
                }
                TextButton(
                    onClick = onClearPage,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(
                        Icons.Outlined.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Clear page")
                }
            }
            return
        }

        if (editor.tool == ToolType.LASER) {
            ToolHintRow(
                icon = Icons.Outlined.FlashOn,
                text = "Drag to point — the trail fades when you lift.",
            )
            return
        }

        if (editor.tool == ToolType.LASSO) {
            ToolHintRow(
                icon = Icons.Outlined.Gesture,
                text = "Draw around items to select, then drag to move.",
            )
            return
        }

        val customColor = editor.color !in InkPalette
        if (editor.recentColors.isNotEmpty()) {
            Text(
                text = "RECENT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                editor.recentColors.forEach { swatch ->
                    ColorSwatch(
                        color = swatch,
                        selected = editor.color == swatch,
                        onClick = { onColorSelected(swatch) },
                    )
                }
            }
        }
        if (compact) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkPalette.forEach { swatch ->
                    ColorSwatch(
                        color = swatch,
                        selected = editor.color == swatch,
                        onClick = { onColorSelected(swatch) },
                    )
                }
                CustomColorSwatch(
                    selected = customColor,
                    onClick = onOpenColorPicker,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val rows = InkPalette.chunked(5)
                rows.forEachIndexed { index, row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row.forEach { swatch ->
                            ColorSwatch(
                                color = swatch,
                                selected = editor.color == swatch,
                                onClick = { onColorSelected(swatch) },
                            )
                        }
                        if (index == rows.lastIndex) {
                            CustomColorSwatch(
                                selected = customColor,
                                onClick = onOpenColorPicker,
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = sizeLabel(editor.tool),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(60.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val range = widthRange(editor.tool)
            Row(
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                widthPresets(editor.tool).forEach { preset ->
                    WidthPreset(
                        selected = abs(editor.width - preset) < 0.55f,
                        color = editor.color,
                        width = preset,
                        maxWidth = range.endInclusive,
                        onClick = { onWidthChange(preset) },
                    )
                }
            }
            Slider(
                value = editor.width,
                onValueChange = onWidthChange,
                valueRange = range,
                colors = sliderColors(),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            )
            WidthPreview(
                color = editor.color,
                width = editor.width,
                maxWidth = range.endInclusive,
            )
        }

        if (editor.tool == ToolType.TEXT) {
            Text(
                text = "ALIGNMENT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextAlignment.entries.forEach { option ->
                    FilterChip(
                        selected = editor.textAlignment == option,
                        onClick = { onTextAlignmentSelected(option) },
                        label = { Text(option.label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                text = "Text wraps inside its box. Drag the box corner on the page to resize it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (editor.tool == ToolType.STICKY) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Text size",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(64.dp),
                )
                Slider(
                    value = editor.stickyFontSize,
                    onValueChange = onStickyFontSizeChange,
                    valueRange = 16f..48f,
                    colors = sliderColors(),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                )
                Text(
                    text = editor.stickyFontSize.roundToInt().toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            ToolHintRow(
                icon = Icons.AutoMirrored.Outlined.StickyNote2,
                text = "Tap the page to add a note. Drag a note to move it; tap it to edit.",
            )
        }

        if (editor.tool == ToolType.SHAPE) {
            if (compact) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ShapeType.entries.forEach { shape ->
                        FilterChip(
                            selected = editor.shapeType == shape,
                            onClick = { onShapeSelected(shape) },
                            label = { Text(shape.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShapeType.entries.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { shape ->
                                FilterChip(
                                    selected = editor.shapeType == shape,
                                    onClick = { onShapeSelected(shape) },
                                    label = { Text(shape.label) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun colorLabel(color: Long): String =
    "#" + (color and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

@Composable
private fun ColorSwatch(
    color: Long,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val swatchColor = Color(color.toInt())
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                onClickLabel = "Select colour",
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics {
                contentDescription = colorLabel(color)
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(swatchColor)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = if (swatchColor.luminance() > 0.55f) Color.Black else Color.White,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

@Composable
private fun CustomColorSwatch(
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                onClickLabel = "Choose a custom colour",
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = "Custom colour" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun ToolHintRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun sizeLabel(tool: ToolType): String = when (tool) {
    ToolType.TEXT -> "Text size"
    ToolType.STICKY -> "Note size"
    ToolType.TAPE -> "Tape"
    ToolType.BRUSH_PEN -> "Brush"
    ToolType.FOUNTAIN_PEN -> "Nib"
    else -> "Size"
}

private fun widthRange(tool: ToolType): ClosedFloatingPointRange<Float> = when (tool) {
    ToolType.TEXT -> 16f..96f
    ToolType.STICKY -> 160f..420f
    ToolType.HIGHLIGHTER -> 8f..48f
    ToolType.ERASER -> 8f..60f
    ToolType.TAPE -> 10f..64f
    ToolType.BRUSH_PEN -> 4f..40f
    ToolType.FOUNTAIN_PEN -> 1f..14f
    ToolType.BALL_PEN -> 1f..10f
    ToolType.LASER -> 2f..16f
    else -> 1f..24f
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)

/** Three quick stroke-weight presets, from fine to bold. */
private fun widthPresets(tool: ToolType): List<Float> {
    val range = widthRange(tool)
    val span = range.endInclusive - range.start
    val presets = listOf(0.16f, 0.42f, 0.78f).map { range.start + span * it }
    return if (tool == ToolType.TEXT || tool == ToolType.ERASER || tool == ToolType.STICKY) {
        presets.map { it.roundToInt().toFloat() }
    } else {
        presets.map { (it * 10f).roundToInt() / 10f }
    }
}

@Composable
private fun WidthPreset(
    selected: Boolean,
    color: Long,
    width: Float,
    maxWidth: Float,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                onClickLabel = "Use width ${width.roundToInt()}",
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics {
                contentDescription = "Width ${width.roundToInt()}"
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(27.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                ),
            contentAlignment = Alignment.Center,
        ) {
        Canvas(modifier = Modifier.size(19.dp)) {
            val fraction = (width / maxWidth).coerceIn(0.05f, 1f)
            drawCircle(
                color = Color(color.toInt()),
                radius = (1.5f + fraction * 7f).dp.toPx(),
            )
        }
        }
    }
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
internal fun SelectionOptions(
    count: Int,
    hasSelection: Boolean,
    clipboardCount: Int,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (hasSelection) {
                "$count selected"
            } else {
                "Clipboard · $clipboardCount"
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        SelectionAction(
            icon = Icons.Outlined.ContentCopy,
            label = "Copy",
            enabled = hasSelection,
            onClick = onCopy,
        )
        SelectionAction(
            icon = Icons.Outlined.ContentCut,
            label = "Cut",
            enabled = hasSelection,
            onClick = onCut,
        )
        SelectionAction(
            icon = Icons.Outlined.ContentPaste,
            label = "Paste",
            enabled = clipboardCount > 0,
            onClick = onPaste,
        )
        SelectionAction(
            icon = Icons.Outlined.ContentCopy,
            label = "Duplicate",
            enabled = hasSelection,
            onClick = onDuplicate,
        )
        SelectionAction(
            icon = Icons.Outlined.Delete,
            label = "Delete",
            enabled = hasSelection,
            tint = MaterialTheme.colorScheme.error,
            onClick = onDelete,
        )
        if (hasSelection) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        onClickLabel = "Clear selection",
                        role = Role.Button,
                        onClick = onClear,
                    ),
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
}

@Composable
private fun SelectionAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(48.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, color = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
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
                Text("Presets", style = MaterialTheme.typography.labelMedium)
                Column(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    InkPalette.chunked(5).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { swatch ->
                                val selected = previewLong == swatch
                                val swatchColor = Color(swatch.toInt())
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clickable(
                                            onClickLabel = "Use colour ${colorLabel(swatch)}",
                                            role = Role.RadioButton,
                                            onClick = {
                                                val hsv = FloatArray(3).also {
                                                    AndroidColor.colorToHSV(swatch.toInt(), it)
                                                }
                                                hue = hsv[0]
                                                saturation = hsv[1]
                                                brightness = hsv[2]
                                            },
                                        )
                                        .semantics {
                                            contentDescription = colorLabel(swatch)
                                            this.selected = selected
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(swatchColor)
                                            .border(
                                                width = if (selected) 2.5.dp else 1.dp,
                                                color = if (selected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
                                                },
                                                shape = CircleShape,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                    if (selected) {
                                        Icon(
                                            Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = if (swatchColor.luminance() > 0.55f) Color.Black else Color.White,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
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
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f, colors = sliderColors())
                Text("Saturation", style = MaterialTheme.typography.labelMedium)
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f, colors = sliderColors())
                Text("Brightness", style = MaterialTheme.typography.labelMedium)
                Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 0f..1f, colors = sliderColors())
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
