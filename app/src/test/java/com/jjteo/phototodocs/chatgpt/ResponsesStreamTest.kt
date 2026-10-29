package com.jjteo.phototodocs.chatgpt

import com.jjteo.phototodocs.ai.AiDocumentText
import java.io.IOException
import java.io.StringReader
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ResponsesStreamTest {
    private fun event(type: String, extra: JSONObject = JSONObject()) = "data: ${extra.put("type", type)}\n\n"
    private fun complete(text: String? = null): String {
        val response = JSONObject().put("status", "completed")
        if (text != null) response.put("output", JSONArray().put(JSONObject().put("type", "message").put("role", "assistant")
            .put("content", JSONArray().put(JSONObject().put("type", "output_text").put("text", text)))))
        return event("response.completed", JSONObject().put("response", response))
    }
    private fun read(stream: String, partial: (String) -> Unit = {}) = ResponsesStream.read(StringReader(stream).buffered(), partial)
    @Test fun visibleTextPreservesWhitespaceAndIgnoresReasoning() {
        val stream = ": keepalive\n\n" + event("response.reasoning_text.delta", JSONObject().put("delta", "private reasoning")) +
            event("response.output_text.delta", JSONObject().put("delta", "First\n\n")) +
            event("response.output_text.delta", JSONObject().put("delta", "Equation ${'$'}x${'$'}\n${AiDocumentText.END_MARKER}")) + complete()
        assertEquals("First\n\nEquation ${'$'}x${'$'}", AiDocumentText.completed(read(stream)))
    }
    @Test fun completedCanonicalOutputCanSupplyTextWithoutDeltas() {
        assertEquals("Connection works.", read(complete("Connection works.")))
    }
    @Test fun multilineDataAndCrlfEventsAreSupported() {
        val stream = "data: {\r\ndata: \"type\":\"response.output_text.delta\",\"delta\":\"Hello\"}\r\n\r\n" + complete()
        assertEquals("Hello", read(stream))
    }
    @Test fun failureAfterVisibleDeltasNeverReturnsASuccessfulDraft() {
        val seen = mutableListOf<String>()
        val stream = event("response.output_text.delta", JSONObject().put("delta", "Draft text")) +
            event("response.failed", JSONObject().put("response", JSONObject().put("error", JSONObject().put("code", "subscription_sharing_usage_limit_exceeded"))))
        assertThrows(ChatGptException::class.java) { read(stream, seen::add) }
        assertEquals(listOf("Draft text"), seen)
    }
    @Test fun disconnectedOrIncompleteStreamsNeverReturnPartialText() {
        val delta = event("response.output_text.delta", JSONObject().put("delta", "unfinished"))
        assertThrows(IOException::class.java) { read(delta) }
        assertThrows(IOException::class.java) { read(delta + "data: [DONE]\n\n") }
        assertThrows(IOException::class.java) { read(delta + event("response.incomplete")) }
    }
    @Test fun bothTransportCompletionAndDocumentMarkerAreRequired() {
        assertThrows(IOException::class.java) { AiDocumentText.completed(read(complete("unfinished document"))) }
        assertThrows(IOException::class.java) { read(event("response.output_text.delta", JSONObject().put("delta", "Note\n${AiDocumentText.END_MARKER}"))) }
    }
}
