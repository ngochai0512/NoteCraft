package com.notecraft.util

import com.notecraft.model.Note

private const val MaxTags = 8
private const val MaxTagLength = 24
private const val MaxFolderLength = 40

/** Splits a comma-separated tag string into trimmed, case-insensitively unique tags. */
fun normalizeTags(raw: String): List<String> = raw
    .split(',')
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .map { it.take(MaxTagLength) }
    .distinctBy { it.lowercase() }
    .take(MaxTags)

/** Trims and bounds a folder name; blank input means "unfiled". */
fun normalizeFolder(raw: String?): String? = raw
    ?.trim()
    ?.take(MaxFolderLength)
    ?.takeIf { it.isNotEmpty() }

/** Title, folder, and tag search plus folder/tag filters. */
fun Note.matchesLibrary(
    query: String,
    folderFilter: String?,
    tagFilter: String?,
): Boolean {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isNotEmpty() &&
        !title.contains(trimmedQuery, ignoreCase = true) &&
        folder?.contains(trimmedQuery, ignoreCase = true) != true &&
        tags.none { it.contains(trimmedQuery, ignoreCase = true) }
    ) {
        return false
    }
    if (folderFilter != null && !folder.equals(folderFilter, ignoreCase = true)) {
        return false
    }
    if (tagFilter != null && tags.none { it.equals(tagFilter, ignoreCase = true) }) {
        return false
    }
    return true
}
