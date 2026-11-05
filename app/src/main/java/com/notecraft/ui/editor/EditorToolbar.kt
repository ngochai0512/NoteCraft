package com.notecraft.ui.editor

import android.graphics.Color as AndroidColor
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixNormal
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.notecraft.model.PrimaryTools
import com.notecraft.model.SecondaryTools
import com.notecraft.model.ShapeType
import com.notecraft.model.ToolType
import com.notecraft.ui.EditorState
import com.notecraft.ui.theme.InkPalette
import kotlin.math.abs
import kotlin.math.roundToInt

private fun toolIcon(tool: ToolType): ImageVector = when (tool) {
    ToolType.FOUNTAIN_PEN -> Icons.Outlined.Draw
    ToolType.BALL_PEN -> Icons.Outlined.Edit
    ToolType.BRUSH_PEN -> Icons.Outlined.Brush
    ToolType.PENCIL -> Icons.Outlined.Create
    ToolType.HIGHLIGHTER -> Icons.Outlined.BorderColor
    ToolType.ERASER -> Icons.Outlined.AutoFixNormal
    ToolType.SHAPE -> Icons.Outlined.Category
    ToolType.TAPE -> Icons.Outlined.Layers
    ToolType.LASER -> Icons.Outlined.FlashOn
    ToolType.TEXT -> Icons.Outlined.TextFields
    ToolType.LASSO -> Icons.Outlined.Gesture
}

/**
 * Tool ribbon shown above the canvas. Tapping a tool selects it and opens that tool's
 * color/width dropdown; tapping the same tool again or touching elsewhere dismisses it.
 */
@Composable
fun EditorToolPanel(
    editor: EditorState,
    expandedTool: ToolType?,
    compact: Boolean,
    wide: Boolean,
    onToolSelected: (ToolType) -> Unit,
    onDismissOptions: () -> Unit,
    onColorSelected: (Long) -> Unit,
    onWidthChange: (Float) -> Unit,
    onShapeSelected: (ShapeType) -> Unit,
    onOpenColorPicker: () -> Unit,
    onDeleteSelection: () -> Unit,
    onDuplicateSelection: () -> Unit,
    onClearSelection: () -> Unit,
    onClearPage: () -> Unit,
    onMoreMenuVisibilityChanged: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = if (compact) 4.dp else 6.dp,
            ),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            modifier = Modifier.border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(20.dp),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 8.dp,
                        vertical = if (compact) 4.dp else 7.dp,
                    )
                    .animateContentSize(
                        animationSpec = tween(durationMillis = 220),
                    ),
            ) {
                ToolRow(
                    selectedTool = editor.tool,
                    expandedTool = expandedTool,
                    compact = compact,
                    wide = wide,
                    onToolSelected = onToolSelected,
                    onDismissOptions = onDismissOptions,
                    onMoreMenuVisibilityChanged = onMoreMenuVisibilityChanged,
                    optionsContent = { _ ->
                        ToolOptions(
                            editor = editor,
                            compact = compact,
                            onColorSelected = onColorSelected,
                            onWidthChange = onWidthChange,
                            onShapeSelected = onShapeSelected,
                            onOpenColorPicker = onOpenColorPicker,
                            onClearPage = onClearPage,
                        )
                    },
                )
                if (editor.hasSelection) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    )
                    SelectionOptions(
                        count = editor.selectionCount,
                        onDuplicate = onDuplicateSelection,
                        onDelete = onDeleteSelection,
                        onClear = onClearSelection,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolRow(
    selectedTool: ToolType,
    expandedTool: ToolType?,
    compact: Boolean,
    wide: Boolean,
    onToolSelected: (ToolType) -> Unit,
    onDismissOptions: () -> Unit,
    onMoreMenuVisibilityChanged: (Boolean) -> Unit,
    optionsContent: @Composable (ToolType) -> Unit,
) {
    var showMore by remember { mutableStateOf(false) }
    val secondarySelected = selectedTool in SecondaryTools
    val rowModifier = if (wide) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    }

    Row(
        modifier = rowModifier,
        horizontalArrangement = if (wide) {
            Arrangement.SpaceEvenly
        } else {
            Arrangement.spacedBy(2.dp)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrimaryTools.forEach { tool ->
            Box {
                ToolButton(
                    tool = tool,
                    selected = tool == selectedTool,
                    showLabel = !compact,
                    onClick = {
                        showMore = false
                        onToolSelected(tool)
                    },
                )
                ToolOptionsMenu(
                    tool = tool,
                    expanded = expandedTool == tool,
                    onDismiss = onDismissOptions,
                    content = { optionsContent(tool) },
                )
            }
        }
        Box {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        onDismissOptions()
                        showMore = true
                        onMoreMenuVisibilityChanged(true)
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (secondarySelected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "More tools",
                        tint = if (secondarySelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(19.dp),
                    )
                }
                if (!compact) {
                    Text(
                        text = "More",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (secondarySelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                }
            }
            DropdownMenu(
                expanded = showMore,
                onDismissRequest = {
                    showMore = false
                    onMoreMenuVisibilityChanged(false)
                },
            ) {
                SecondaryTools.forEach { tool ->
                    DropdownMenuItem(
                        text = { Text(toolShortLabel(tool)) },
                        leadingIcon = { Icon(toolIcon(tool), contentDescription = null) },
                        trailingIcon = {
                            if (tool == selectedTool) Icon(Icons.Outlined.Check, contentDescription = null)
                        },
                        onClick = {
                            showMore = false
                            onMoreMenuVisibilityChanged(false)
                            onToolSelected(tool)
                        },
                    )
                }
            }
            val secondaryOptionsTool = expandedTool?.takeIf { it in SecondaryTools }
            if (secondaryOptionsTool != null) {
                ToolOptionsMenu(
                    tool = secondaryOptionsTool,
                    expanded = true,
                    onDismiss = onDismissOptions,
                    content = { optionsContent(secondaryOptionsTool) },
                )
            }
        }
    }
}

@Composable
private fun ToolOptionsMenu(
    tool: ToolType,
    expanded: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!expanded) return

    val configuration = LocalConfiguration.current
    val maxHeight = if (configuration.screenHeightDp < 480) 176.dp else 440.dp
    val marginPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val positionProvider = remember {
        BelowAnchorPopupPositionProvider(marginPx)
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            modifier = Modifier.border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(16.dp),
            ),
        ) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = toolShortLabel(tool).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                content()
            }
        }
    }
}

private class BelowAnchorPopupPositionProvider(
    private val marginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val x = anchorBounds.left.coerceIn(marginPx, maxX)
        return IntOffset(x, anchorBounds.bottom + marginPx)
    }
}

@Composable
private fun ToolButton(
    tool: ToolType,
    selected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "toolScale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(if (showLabel) 44.dp else 40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
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
                modifier = Modifier.size(19.dp),
            )
        }
        if (showLabel) {
            Text(
                text = toolShortLabel(tool),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

private fun toolShortLabel(tool: ToolType): String = when (tool) {
    ToolType.FOUNTAIN_PEN -> "Fountain"
    ToolType.BALL_PEN -> "Ball"
    ToolType.BRUSH_PEN -> "Brush"
    ToolType.HIGHLIGHTER -> "Marker"
    ToolType.LASER -> "Laser"
    else -> tool.label
}

@Composable
private fun ToolOptions(
    editor: EditorState,
    compact: Boolean,
    onColorSelected: (Long) -> Unit,
    onWidthChange: (Float) -> Unit,
    onShapeSelected: (ShapeType) -> Unit,
    onOpenColorPicker: () -> Unit,
    onClearPage: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (editor.tool == ToolType.ERASER) {
            Column {
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
                val rows = InkPalette.chunked(6)
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

@Composable
private fun ColorSwatch(
    color: Long,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val swatchColor = Color(color.toInt())
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
            )
            .clickable(onClick = onClick),
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

@Composable
private fun CustomColorSwatch(
    selected: Boolean,
    onClick: () -> Unit,
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
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Palette,
            contentDescription = "Custom color",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
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
    ToolType.TAPE -> "Tape"
    ToolType.BRUSH_PEN -> "Brush"
    ToolType.FOUNTAIN_PEN -> "Nib"
    else -> "Size"
}

private fun widthRange(tool: ToolType): ClosedFloatingPointRange<Float> = when (tool) {
    ToolType.TEXT -> 16f..96f
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
    return if (tool == ToolType.TEXT || tool == ToolType.ERASER) {
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
            .size(27.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .clickable(onClick = onClick),
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
                    InkPalette.chunked(6).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { swatch ->
                                val selected = previewLong == swatch
                                val swatchColor = Color(swatch.toInt())
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
                                        )
                                        .clickable {
                                            val hsv = FloatArray(3).also {
                                                AndroidColor.colorToHSV(swatch.toInt(), it)
                                            }
                                            hue = hsv[0]
                                            saturation = hsv[1]
                                            brightness = hsv[2]
                                        },
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
