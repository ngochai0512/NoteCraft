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
import com.notecraft.model.PAGE_HEIGHT
import com.notecraft.model.PAGE_WIDTH
import com.notecraft.model.PageBackground
import kotlin.math.min

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
                PageBackground.entries.forEach { background ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(background) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Canvas(
                            modifier = Modifier
                                .size(width = 38.dp, height = 52.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White),
                        ) {
                            val scale = min(size.width / PAGE_WIDTH, size.height / PAGE_HEIGHT)
                            drawPageBackground(
                                background = background,
                                scale = scale,
                                origin = Offset.Zero,
                            )
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
