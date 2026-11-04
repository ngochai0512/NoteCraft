package com.notecraft.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.notecraft.model.PageBackground
import com.notecraft.ui.theme.PaperSurface

@Composable
fun PageBackgroundDialog(
    current: PageBackground,
    onDismiss: () -> Unit,
    onSelect: (PageBackground) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Page background") },
        text = {
            Column {
                Text(
                    text = "Choose the paper that suits this page.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
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
    }
}
