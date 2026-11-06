package com.notecraft.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.notecraft.model.TextAlignment
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
            Column(modifier = Modifier.fillMaxWidth()) {
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
