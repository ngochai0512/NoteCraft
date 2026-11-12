package com.notecraft.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.notecraft.model.PageBackground
import com.notecraft.ui.theme.PaperSurface
import com.notecraft.util.validPageRange

@Composable
fun PageBackgroundDialog(
    current: PageBackground,
    onDismiss: () -> Unit,
    onSelect: (PageBackground) -> Unit,
) {
    val compact = LocalConfiguration.current.screenHeightDp < 480
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Page background") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Choose the paper that suits this page.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (compact) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                    ) {
                        PageBackground.entries.forEach { background ->
                            CompactBackgroundTile(
                                background = background,
                                selected = current == background,
                                onClick = { onSelect(background) },
                            )
                        }
                    }
                } else {
                    PageBackground.entries.forEach { background ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelect(background) }
                                .padding(vertical = 5.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .size(width = 36.dp, height = 50.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(PaperSurface),
                            ) {
                                drawBackgroundPreview(background)
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = background.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            RadioButton(
                                selected = current == background,
                                onClick = { onSelect(background) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

@Composable
private fun CompactBackgroundTile(
    background: PageBackground,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(70.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 58.dp, height = 82.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(PaperSurface)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = RoundedCornerShape(6.dp),
                ),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawBackgroundPreview(background)
            }
        }
        Spacer(Modifier.size(4.dp))
        Text(
            text = background.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** Simplified, high-contrast paper previews for the background picker. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBackgroundPreview(
    background: PageBackground,
) {
    val width = size.width
    val height = size.height
    val rule = Color(0xFFCBD5E1)
    val margin = Color(0xFFEF4444)
    val dot = Color(0xFF94A3B8)

    when (background) {
        PageBackground.BLANK -> Unit

        PageBackground.LINED -> {
            val step = height / 8f
            var y = step
            while (y < height) {
                drawLine(rule, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
                y += step
            }
            drawLine(
                color = margin.copy(alpha = 0.55f),
                start = Offset(width * 0.16f, 0f),
                end = Offset(width * 0.16f, height),
                strokeWidth = 1f,
            )
        }

        PageBackground.GRID -> {
            val step = width / 5f
            var x = step
            while (x < width) {
                drawLine(rule.copy(alpha = 0.75f), Offset(x, 0f), Offset(x, height), strokeWidth = 0.8f)
                x += step
            }
            var y = step
            while (y < height) {
                drawLine(rule.copy(alpha = 0.75f), Offset(0f, y), Offset(width, y), strokeWidth = 0.8f)
                y += step
            }
        }

        PageBackground.DOTTED -> {
            val stepX = width / 6f
            val stepY = height / 9f
            var x = stepX / 2f
            while (x < width) {
                var y = stepY / 2f
                while (y < height) {
                    drawCircle(dot.copy(alpha = 0.65f), radius = 1.1f, center = Offset(x, y))
                    y += stepY
                }
                x += stepX
            }
        }

        PageBackground.CORNELL -> {
            val top = height * 0.16f
            val bottom = height * 0.78f
            val cue = width * 0.32f
            val step = height / 8f
            var y = top + step
            while (y < bottom) {
                drawLine(rule.copy(alpha = 0.6f), Offset(0f, y), Offset(width, y), strokeWidth = 0.8f)
                y += step
            }
            drawLine(rule, Offset(0f, top), Offset(width, top), strokeWidth = 1f)
            drawLine(rule, Offset(0f, bottom), Offset(width, bottom), strokeWidth = 1f)
            drawLine(
                color = margin.copy(alpha = 0.55f),
                start = Offset(cue, top),
                end = Offset(cue, bottom),
                strokeWidth = 1f,
            )
        }

        PageBackground.PLANNER -> {
            val header = height * 0.12f
            drawRect(rule, Offset(0f, 0f), Size(width, header), style = DrawStroke(1f))
            drawRect(rule, Offset(width * 0.05f, header + height * 0.04f), Size(width * 0.28f, height * 0.5f), style = DrawStroke(1f))
            drawRect(rule, Offset(width * 0.37f, header + height * 0.04f), Size(width * 0.58f, height * 0.5f), style = DrawStroke(1f))
            drawRect(margin.copy(alpha = 0.5f), Offset(width * 0.05f, height * 0.69f), Size(width * 0.9f, height * 0.24f), style = DrawStroke(1f))
            var y = header + height * 0.1f
            while (y < height * 0.56f) {
                drawLine(rule.copy(alpha = 0.55f), Offset(width * 0.05f, y), Offset(width * 0.33f, y), 0.7f)
                drawLine(rule.copy(alpha = 0.55f), Offset(width * 0.37f, y), Offset(width * 0.95f, y), 0.7f)
                y += height * 0.055f
            }
            var task = height * 0.72f
            while (task < height * 0.9f) {
                drawRect(rule, Offset(width * 0.08f, task), Size(width * 0.035f, width * 0.035f), style = DrawStroke(0.8f))
                drawLine(rule.copy(alpha = 0.5f), Offset(width * 0.13f, task + width * 0.018f), Offset(width * 0.92f, task + width * 0.018f), 0.7f)
                task += height * 0.04f
            }
        }

        PageBackground.WEEKLY -> {
            val top = height * 0.08f
            val headerBottom = top + height * 0.05f
            drawRect(rule, Offset(0f, top), Size(width, headerBottom - top), style = DrawStroke(1f))
            for (i in 0..7) {
                val x = width * i / 7f
                drawLine(rule.copy(alpha = 0.8f), Offset(x, top), Offset(x, height * 0.94f), 0.8f)
            }
            var y = headerBottom + height * 0.08f
            while (y < height * 0.94f) {
                drawLine(rule.copy(alpha = 0.5f), Offset(0f, y), Offset(width, y), 0.7f)
                y += height * 0.09f
            }
        }

        PageBackground.MUSIC -> {
            val gap = height * 0.011f
            var top = height * 0.08f
            while (top + gap * 4 < height * 0.94f) {
                for (line in 0..4) {
                    val y = top + gap * line
                    drawLine(rule.copy(alpha = 0.8f), Offset(width * 0.08f, y), Offset(width * 0.92f, y), 0.8f)
                }
                top += gap * 4 + height * 0.12f
            }
        }

        PageBackground.STORYBOARD -> {
            val left = width * 0.06f
            val top = height * 0.1f
            val gap = width * 0.04f
            val cellWidth = (width - left * 2 - gap) / 2f
            val cellHeight = (height * 0.82f - gap * 2) / 3f
            for (row in 0..2) {
                for (column in 0..1) {
                    val x = left + column * (cellWidth + gap)
                    val y = top + row * (cellHeight + gap)
                    drawRect(rule, Offset(x, y), Size(cellWidth, cellHeight), style = DrawStroke(0.9f))
                    drawLine(
                        color = rule.copy(alpha = 0.5f),
                        start = Offset(x + width * 0.02f, y + cellHeight - height * 0.025f),
                        end = Offset(x + cellWidth - width * 0.02f, y + cellHeight - height * 0.025f),
                        strokeWidth = 0.6f,
                    )
                }
            }
        }

        PageBackground.CHECKLIST -> {
            val gap = width * 0.06f
            val columnWidth = (width - gap) / 2f
            for (column in 0..1) {
                val x = column * (columnWidth + gap)
                var y = height * 0.1f
                while (y < height * 0.9f) {
                    drawRect(rule, Offset(x, y), Size(width * 0.045f, width * 0.045f), style = DrawStroke(0.8f))
                    drawLine(
                        color = rule.copy(alpha = 0.5f),
                        start = Offset(x + width * 0.08f, y + width * 0.022f),
                        end = Offset(x + columnWidth - width * 0.03f, y + width * 0.022f),
                        strokeWidth = 0.7f,
                    )
                    y += height * 0.08f
                }
            }
        }
    }
}

@Composable
fun PageRangeDialog(
    pageCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (IntRange) -> Unit,
) {
    var startText by remember { mutableStateOf("1") }
    var endText by remember { mutableStateOf(pageCount.toString()) }
    val start = startText.toIntOrNull()
    val end = endText.toIntOrNull()
    val range = if (start != null && end != null) {
        validPageRange(start, end, pageCount)
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export pages") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Choose an inclusive page range from 1 to $pageCount.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it.filter(Char::isDigit) },
                        label = { Text("From") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it.filter(Char::isDigit) },
                        label = { Text("To") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (range == null) {
                    Text(
                        text = "Enter a valid range within the notebook.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = range != null,
                onClick = { range?.let(onConfirm) },
            ) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
