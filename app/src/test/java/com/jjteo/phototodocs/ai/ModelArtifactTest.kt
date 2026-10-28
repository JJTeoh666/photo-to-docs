package com.jjteo.phototodocs.ai

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ModelArtifactTest {
    @Test fun interruptedDownloadCanResumeOnlyAtTheRequestedByte() {
        val offset = 1_000_000L
        assertTrue(ModelArtifact.checkResponse(206, offset,
            "bytes $offset-${ModelArtifact.SIZE - 1}/${ModelArtifact.SIZE}", ModelArtifact.SIZE - offset))
        assertThrows(IOException::class.java) {
            ModelArtifact.checkResponse(206, offset,
                "bytes 0-${ModelArtifact.SIZE - 1}/${ModelArtifact.SIZE}", ModelArtifact.SIZE)
        }
    }

    @Test fun rangeIgnoredRestartsInsteadOfAppendingDuplicateBytes() {
        assertFalse(ModelArtifact.checkResponse(200, 1_000_000, null, ModelArtifact.SIZE))
    }

    @Test fun changedArtifactAndMissingRangeAreRejected() {
        assertThrows(IOException::class.java) { ModelArtifact.checkResponse(200, 0, null, 42) }
        assertThrows(IOException::class.java) { ModelArtifact.checkResponse(206, 42, null, -1) }
        assertThrows(IOException::class.java) {
            ModelArtifact.checkResponse(206, 42, "bytes 42-500/501", 459)
        }
    }

    @Test fun completeDraftPreservesMathematics() {
        val source = "# Exercise\n\nSolve ${'$'}x^2+1=0${'$'}."
        assertEquals(source, AiDocumentText.completed("$source\n${AiDocumentText.END_MARKER}"))
        assertEquals(source, AiDocumentText.completed("```markdown\n$source\n```\n${AiDocumentText.END_MARKER}"))
    }

    @Test fun incompleteGenerationCannotBecomeACompleteDocument() {
        assertThrows(IncompleteAiOutputException::class.java) { AiDocumentText.completed("Partial equation") }
        assertThrows(IncompleteAiOutputException::class.java) { AiDocumentText.completed(AiDocumentText.END_MARKER) }
    }

    @Test fun oversizedSelectionIsRejectedInsteadOfSilentlyTruncated() {
        assertThrows(IllegalArgumentException::class.java) {
            AiDocumentText.checkRevisionInput("a".repeat(AiDocumentText.MAX_SOURCE_CHARS + 1), "Fix grammar")
        }
        AiDocumentText.checkRevisionInput("A paragraph.", "Fix grammar")
    }
}
