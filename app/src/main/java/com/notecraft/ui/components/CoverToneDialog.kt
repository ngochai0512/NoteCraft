package com.notecraft.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.notecraft.ui.theme.NotebookCoverTone
import com.notecraft.ui.theme.NotebookCoverTones
import com.notecraft.ui.theme.notebookToneFor

/**
 * Chooses the notebook cover tone. [currentTone] is null when the cover follows the automatic
 * tone derived from the notebook id.
 */
@Composable
fun CoverToneDialog(
    noteId: String,
    currentTone: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    val automatic = notebookToneFor(noteId)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notebook cover") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Choose a cover tone, or let NoteCraft pick one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                CoverToneRow(
                    label = "Automatic",
                    description = "Picked from the notebook name",
                    tone = automatic,
                    selected = currentTone == null,
                    onClick = { onSelect(null) },
                )
                NotebookCoverTones.forEach { tone ->
                    CoverToneRow(
                        label = tone.name,
                        description = null,
                        tone = tone,
                        selected = currentTone == tone.name,
                        onClick = { onSelect(tone.name) },
                    )
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
private fun CoverToneRow(
    label: String,
    description: String?,
    tone: NotebookCoverTone,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Brush.linearGradient(listOf(tone.cover, tone.coverDeep))),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 9.dp)
                    .width(6.dp)
                    .height(18.dp)
                    .background(tone.ribbon.copy(alpha = 0.95f)),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        RadioButton(selected = selected, onClick = onClick)
    }
}
