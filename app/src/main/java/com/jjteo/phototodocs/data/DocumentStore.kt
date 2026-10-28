package com.jjteo.phototodocs.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject

/** One atomic JSON file per note: a damaged note never hides the rest of the library. */
class DocumentStore(private val directory: File) {
    var loadErrors: List<String> = emptyList()
        private set

    @Synchronized fun load(): List<NoteDocument> {
        directory.mkdirs()
        val errors = mutableListOf<String>()
        val notes = directory.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { file ->
            runCatching { decode(file.readText()) }.getOrElse {
                errors += file.name
                null
            }
        }
        loadErrors = errors
        return notes.sortedByDescending { it.updatedAt }
    }

    @Synchronized fun save(note: NoteDocument) {
        require(note.id.matches(Regex("[a-zA-Z0-9-]{1,80}"))) { "Invalid note identifier" }
        check(directory.mkdirs() || directory.isDirectory) { "Could not open note storage" }
        val target = File(directory, "${note.id}.json")
        // Explicit image-cleanup saves may reach disk before an older queued keystroke save.
        if (target.isFile) {
            val storedRevision = runCatching { JSONObject(target.readText()).getLong("revision") }.getOrNull()
            if (storedRevision != null && storedRevision > note.revision) return
        }
        val temporary = File(directory, "${note.id}.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(encode(note).toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    @Synchronized fun delete(id: String) {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        val file = File(directory, "$id.json")
        check(!file.exists() || file.delete()) { "Could not delete note" }
    }

    companion object {
        fun encode(note: NoteDocument): String = JSONObject().apply {
            put("version", 1)
            put("id", note.id)
            put("title", note.title)
            put("source", note.source)
            put("revision", note.revision)
            put("createdAt", note.createdAt)
            put("updatedAt", note.updatedAt)
            put("pages", JSONArray().apply {
                note.pages.forEach { page -> put(JSONObject().apply {
                    put("id", page.id); put("path", page.path); put("originalPath", page.originalPath)
                }) }
            })
        }.toString(2)

        fun decode(json: String): NoteDocument {
            val item = JSONObject(json)
            val photos = item.optJSONArray("pages") ?: JSONArray()
            return NoteDocument(
                id = item.getString("id"), title = item.getString("title"), source = item.getString("source"),
                revision = item.optLong("revision"), createdAt = item.getLong("createdAt"), updatedAt = item.getLong("updatedAt"),
                pages = (0 until photos.length()).map { index ->
                    val page = photos.getJSONObject(index)
                    PhotoPage(page.getString("id"), page.getString("path"), page.optString("originalPath", page.getString("path")))
                },
            )
        }
    }
}
