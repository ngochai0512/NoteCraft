package com.notecraft.ui.editor

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.AutoFixNormal
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.notecraft.model.EraserMode
import com.notecraft.model.ShapeType
import com.notecraft.model.TextAlignment
import com.notecraft.model.ToolType
import com.notecraft.ui.EditorState

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
    ToolType.STICKY -> Icons.AutoMirrored.Outlined.StickyNote2
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
    onStickyFontSizeChange: (Float) -> Unit,
    onShapeSelected: (ShapeType) -> Unit,
    onEraserModeSelected: (EraserMode) -> Unit,
    onTextAlignmentSelected: (TextAlignment) -> Unit,
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
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = if (compact) 4.dp else 6.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val surfaceModifier = if (wide && compact) {
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
        } else {
            Modifier.fillMaxWidth()
        }.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(20.dp),
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            modifier = surfaceModifier,
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
                            onStickyFontSizeChange = onStickyFontSizeChange,
                            onShapeSelected = onShapeSelected,
                            onEraserModeSelected = onEraserModeSelected,
                            onTextAlignmentSelected = onTextAlignmentSelected,
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
