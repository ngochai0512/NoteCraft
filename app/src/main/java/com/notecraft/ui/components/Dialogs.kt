package com.notecraft.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.notecraft.model.TextAlignment
import com.notecraft.model.TextFormatting
import com.notecraft.model.TextListStyle
import kotlinx.coroutines.delay

@Composable
fun TextEntryDialog(
    title: String,
    initialValue: String,
    label: String,
    confirmLabel: String = "Save",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    allowEmpty: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    supportingText: String? = null,
    alignment: TextAlignment? = null,
    onAlignmentChange: ((TextAlignment) -> Unit)? = null,
    formatting: TextFormatting? = null,
    onFormattingChange: ((TextFormatting) -> Unit)? = null,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    val canConfirm = allowEmpty || value.isNotBlank()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(180)
        runCatching { focusRequester.requestFocus() }
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(label) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = maxLines == 1,
                    minLines = minLines,
                    maxLines = maxLines,
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (maxLines == 1) ImeAction.Done else ImeAction.Default,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (canConfirm) {
                                onConfirm(value)
                            }
                        },
                    ),
                )
                if (alignment != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TextAlignment.entries.forEach { option ->
                            FilterChip(
                                selected = alignment == option,
                                onClick = { onAlignmentChange?.invoke(option) },
                                label = { Text(option.label) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                if (formatting != null && onFormattingChange != null) {
                    TextFormattingControls(
                        formatting = formatting,
                        onFormattingChange = onFormattingChange,
                    )
                }
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = { onConfirm(value) },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private val TextHighlightChoices: List<Long> = listOf(
    0xFFFEF08AL,
    0xFFBBF7D0L,
    0xFFFBCFE8L,
    0xFFBFDBFEL,
    0xFFFED7AAL,
)

@Composable
private fun TextFormattingControls(
    formatting: TextFormatting,
    onFormattingChange: (TextFormatting) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(
            text = "STYLE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = formatting.bold,
                onClick = { onFormattingChange(formatting.copy(bold = !formatting.bold)) },
                label = { Text("B", fontWeight = FontWeight.Bold) },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = formatting.italic,
                onClick = { onFormattingChange(formatting.copy(italic = !formatting.italic)) },
                label = { Text("I", fontStyle = FontStyle.Italic) },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = formatting.underline,
                onClick = { onFormattingChange(formatting.copy(underline = !formatting.underline)) },
                label = { Text("U", textDecoration = TextDecoration.Underline) },
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = "LIST",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextListStyle.entries.forEach { style ->
                FilterChip(
                    selected = formatting.listStyle == style,
                    onClick = { onFormattingChange(formatting.copy(listStyle = style)) },
                    label = { Text(style.label) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Line spacing",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "%.1f×".format(formatting.lineSpacing),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = formatting.lineSpacing,
            onValueChange = { onFormattingChange(formatting.copy(lineSpacing = it)) },
            valueRange = 0.8f..2f,
            steps = 5,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "HIGHLIGHT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = formatting.highlightColor == null,
                onClick = { onFormattingChange(formatting.copy(highlightColor = null)) },
                label = { Text("None") },
            )
            TextHighlightChoices.forEach { color ->
                val swatch = Color(color.toInt())
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(
                            width = if (formatting.highlightColor == color) 2.5.dp else 1.dp,
                            color = if (formatting.highlightColor == color) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            },
                            shape = CircleShape,
                        )
                        .clickable {
                            onFormattingChange(formatting.copy(highlightColor = color))
                        },
                )
            }
        }
    }
}

@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    destructive: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) {
                        androidx.compose.material3.MaterialTheme.colorScheme.error
                    } else {
                        androidx.compose.material3.MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
