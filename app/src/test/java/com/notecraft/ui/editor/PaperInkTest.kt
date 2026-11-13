package com.notecraft.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperInkTest {

    private val inkBlack = 0xFF0F172AL
    private val highlighterYellow = 0xFFFEF08AL

    @Test
    fun lightPaperKeepsTheStoredColorUntouched() {
        assertEquals(Color(inkBlack), paperInkColor(inkBlack, darkPaper = false))
    }

    @Test
    fun darkPaperLiftsDarkInkSoItStaysReadable() {
        val rendered = paperInkColor(inkBlack, darkPaper = true)

        assertTrue(rendered.luminance() > 0.5f)
        assertNotEquals(Color(inkBlack), rendered)
    }

    @Test
    fun darkPaperKeepsLightInksInTheirHue() {
        assertEquals(Color(highlighterYellow), paperInkColor(highlighterYellow, darkPaper = true))
    }

    @Test
    fun adaptationPreservesAlpha() {
        val translucentRed = 0x80EF4444L
        val rendered = paperInkColor(translucentRed, darkPaper = true)

        assertEquals(Color(translucentRed).alpha, rendered.alpha, 0.001f)
    }
}
