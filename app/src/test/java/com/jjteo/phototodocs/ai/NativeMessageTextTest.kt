package com.jjteo.phototodocs.ai

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import org.junit.Assert.assertEquals
import org.junit.Test

/** Exercises the shipped SDK's pure message model without loading a native engine or weights. */
class NativeMessageTextTest {
    @Test fun streamingMessageRenderingKeepsOnlyDocumentText() {
        val formula = "${'$'}${'$'}\\frac{a}{b}${'$'}${'$'}"
        val chunk = Message.model(
            contents = Contents.of(Content.Text("# Notes\n\n"), Content.Text(formula)),
            channels = mapOf("thought" to "Internal reasoning must not become document text."),
        )
        assertEquals("# Notes\n\n$formula", chunk.toString())
    }

    @Test fun separateNativeChunksPreserveWhitespaceAndCompletionBoundary() {
        val chunks = listOf(
            Message.model("First paragraph."),
            Message.model("\n\n${'$'}x + 1${'$'}"),
            Message.model("\n${AiDocumentText.END_MARKER}"),
        )
        assertEquals("First paragraph.\n\n${'$'}x + 1${'$'}",
            AiDocumentText.completed(chunks.joinToString("") { it.toString() }))
    }
}
