package com.jjteo.phototodocs.data

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentStoreTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun sourcePhotosAndRevisionSurviveReopening() {
        val dir = temp.newFolder()
        val note = NoteDocument(title = "Math α & notes", source = "# Sample\n\n\\(x^2\\)\n\n$$\\frac{a}{b}$$", revision = 9,
            pages = listOf(PhotoPage("page1", "/private/photo.jpg", "/private/original.jpg")))
        DocumentStore(dir).save(note)
        assertEquals(note, DocumentStore(dir).load().single())
        assertFalse(File(dir, "${note.id}.tmp").exists())
    }

    @Test fun corruptedNoteIsKeptAndDoesNotHideHealthyNotes() {
        val dir = temp.newFolder()
        val store = DocumentStore(dir)
        val note = NoteDocument(source = "safe")
        store.save(note)
        File(dir, "broken.json").writeText("{bad")
        assertEquals(listOf(note), store.load())
        assertEquals(listOf("broken.json"), store.loadErrors)
        assertTrue(File(dir, "broken.json").exists())
    }

    @Test fun delayedSaveCannotReplaceANewerDurableRevision() {
        val store = DocumentStore(temp.newFolder())
        val original = NoteDocument(source = "old", revision = 1)
        val newer = original.copy(source = "latest", revision = 3)
        store.save(newer)
        store.save(original)
        assertEquals(newer, store.load().single())
    }

    @Test fun proposalRejectsManualEditsAndWrongDocument() {
        val note = NoteDocument(source = "before and after", revision = 2)
        val proposal = EditProposal(note.id, 2, "before", "revised", 0, 6, "Fix grammar")
        assertEquals("revised and after", proposal.applyTo(note))
        assertNull(proposal.applyTo(note.copy(revision = 3)))
        assertNull(proposal.applyTo(note.copy(id = "another")))
        assertNull(proposal.copy(oldText = "wrong").applyTo(note))
    }

    @Test fun transcriptionAppendPreservesExistingSource() {
        val note = NoteDocument(source = "existing")
        val proposal = EditProposal(note.id, 0, "", "\n\nnew page", note.source.length, note.source.length, "Scan")
        assertEquals("existing\n\nnew page", proposal.applyTo(note))
    }
}
