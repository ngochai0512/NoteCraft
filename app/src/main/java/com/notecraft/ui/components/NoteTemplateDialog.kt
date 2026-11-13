package com.notecraft.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.notecraft.model.PAGE_WIDTH
import com.notecraft.model.Page
import com.notecraft.model.NoteTemplate
import com.notecraft.ui.editor.drawPageContent
import com.notecraft.ui.theme.LocalDarkPaper

/** Chooser shown by "New from template". A plain "New notebook" uses the default template. */
@Composable
fun NoteTemplateDialog(
    onDismiss: () -> Unit,
    onSelect: (NoteTemplate) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New from template") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                NoteTemplate.entries.forEach { template ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(template) }
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TemplatePreview(template)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = template.label,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = if (template == NoteTemplate.BLANK) {
                                    "Uses your default new-page paper"
                                } else {
                                    template.background.label
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun TemplatePreview(template: NoteTemplate) {
    val page = Page(id = template.name, background = template.background)
    val darkPaper = LocalDarkPaper.current
    Canvas(
        modifier = Modifier
            .size(width = 34.dp, height = 48.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (darkPaper) MaterialTheme.colorScheme.surface else Color.White)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(4.dp),
            ),
    ) {
        val scale = size.width / PAGE_WIDTH
        drawPageContent(
            page = page,
            scale = scale,
            origin = Offset.Zero,
            drawPaper = true,
            drawBackground = true,
            clip = true,
            darkPaper = darkPaper,
        )
    }
}
