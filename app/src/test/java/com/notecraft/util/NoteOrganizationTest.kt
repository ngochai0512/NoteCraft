package com.notecraft.util

import com.notecraft.model.newNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteOrganizationTest {

    @Test
    fun tagsAreTrimmedDeduplicatedAndBounded() {
        assertEquals(
            listOf("lecture", "Biology"),
            normalizeTags(" lecture, Biology , lecture,  , "),
        )
        assertEquals(8, normalizeTags((1..12).joinToString(",") { "tag$it" }).size)
        assertEquals(24, normalizeTags("x".repeat(80)).single().length)
    }

    @Test
    fun folderIsTrimmedAndBlankMeansUnfiled() {
        assertEquals("Biology", normalizeFolder("  Biology  "))
        assertEquals(null, normalizeFolder("   "))
        assertEquals(null, normalizeFolder(null))
    }

    @Test
    fun searchMatchesTitleFolderAndTags() {
        val note = newNote("Cell Biology").copy(
            folder = "Science",
            tags = listOf("exam", "lecture"),
        )

        assertTrue(note.matchesLibrary("biology", null, null))
        assertTrue(note.matchesLibrary("science", null, null))
        assertTrue(note.matchesLibrary("exam", null, null))
        assertFalse(note.matchesLibrary("history", null, null))
    }

    @Test
    fun folderAndTagFiltersAreCaseInsensitive() {
        val note = newNote("Cell Biology").copy(
            folder = "Science",
            tags = listOf("Exam"),
        )

        assertTrue(note.matchesLibrary("", "science", null))
        assertTrue(note.matchesLibrary("", null, "exam"))
        assertFalse(note.matchesLibrary("", "Art", null))
        assertFalse(note.matchesLibrary("", null, "art"))
    }
}
