package com.notecraft.util

import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.notecraft.model.StickyNote
import com.notecraft.model.TextAlignment
import com.notecraft.model.TextItem
import kotlin.math.ceil

/** Builds the same page-unit layout used by rendering and PDF export. */
internal fun buildTextLayout(item: TextItem): StaticLayout {
    val displayText = item.displayText
    val typefaceStyle = when {
        item.formatting.bold && item.formatting.italic -> Typeface.BOLD_ITALIC
        item.formatting.bold -> Typeface.BOLD
        item.formatting.italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
    }
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = item.color.toInt()
        textSize = item.fontSize
        typeface = Typeface.create("sans-serif", typefaceStyle)
        isUnderlineText = item.formatting.underline
    }
    val availableWidth = if (item.width > 0f) {
        item.width.toInt().coerceAtLeast(1)
    } else {
        val longestLine = displayText.split('\n').maxOfOrNull { line -> paint.measureText(line) } ?: 0f
        ceil(longestLine).toInt().coerceAtLeast(1)
    }
    val alignment = when (item.alignment) {
        TextAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
        TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
        TextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
    }
    return StaticLayout.Builder
        .obtain(displayText, 0, displayText.length, paint, availableWidth)
        .setAlignment(alignment)
        .setIncludePad(false)
        .setLineSpacing(0f, item.formatting.lineSpacing.coerceIn(0.8f, 2f))
        .build()
}

internal fun buildStickyLayout(note: StickyNote): StaticLayout {
    val textColor = if (Color(note.color.toInt()).luminance() > 0.55f) {
        Color(0xFF0F172A)
    } else {
        Color.White
    }
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor.toArgb()
        textSize = note.fontSize
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    val availableWidth = (note.width - StickyTextPadding * 2f).toInt().coerceAtLeast(1)
    return StaticLayout.Builder
        .obtain(note.text, 0, note.text.length, paint, availableWidth)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .setLineSpacing(0f, 1.05f)
        .build()
}

/** Page-unit measurements used for hit testing, selection bounds, and resize handles. */
object TextMeasurer {
    fun textHeight(item: TextItem): Float = buildTextLayout(item).height.toFloat()

    fun stickyHeight(note: StickyNote): Float =
        buildStickyLayout(note).height + StickyTextPadding * 2f
}
