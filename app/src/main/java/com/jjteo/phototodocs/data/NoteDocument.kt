package com.jjteo.phototodocs.data

import java.util.UUID

data class PhotoPage(
    val id: String = UUID.randomUUID().toString(),
    val path: String,
    val originalPath: String = path,
)

data class NoteDocument(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled note",
    val source: String = "",
    val revision: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val pages: List<PhotoPage> = emptyList(),
)

/** A revision is checked even when the user changes text back to its earlier value. */
data class EditProposal(
    val documentId: String,
    val baseRevision: Long,
    val oldText: String,
    val newText: String,
    val start: Int,
    val end: Int,
    val label: String,
) {
    fun applyTo(document: NoteDocument): String? {
        if (document.id != documentId || document.revision != baseRevision) return null
        if (start !in 0..document.source.length || end !in start..document.source.length) return null
        if (document.source.substring(start, end) != oldText) return null
        return document.source.replaceRange(start, end, newText)
    }
}
