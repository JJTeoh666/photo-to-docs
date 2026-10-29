package com.jjteo.phototodocs.chatgpt

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ChatGptTransportTest {
    private fun result(status: String = "completed") = JSONObject().put("object", "response").put("status", status).put("error", JSONObject.NULL)
        .put("output", JSONArray().put(JSONObject().put("type", "message").put("role", "assistant")
            .put("content", JSONArray().put(JSONObject().put("type", "output_text").put("text", "Connection works.")))))
    private fun stream() = "data: ${JSONObject().put("type", "response.completed").put("response", result())}\n\n"
    private suspend fun call(body: String, type: String? = "text/event-stream", status: Int = 200, requestId: String = "req_test_123", calls: AtomicInteger = AtomicInteger()): String {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Reply")
                .header("x-request-id", requestId).body(body.toResponseBody(type?.toMediaType())).build()
        }.build()
        return ChatGptTransport(client).response("synthetic-access", "gpt-6-luna", "Test connection", JSONArray().put(
            JSONObject().put("type", "input_text").put("text", "Connection works."))) {}
    }
    @Test fun eventStreamContentTypeIsCaseInsensitive() = runBlocking {
        assertEquals("Connection works.", call(stream(), "Text/Event-Stream; charset=utf-8"))
    }
    @Test fun validEventsAreAcceptedWithMissingOrMislabelledHeaders() = runBlocking {
        assertEquals("Connection works.", call(stream(), null))
        assertEquals("Connection works.", call("\uFEFF" + stream(), "application/octet-stream"))
        assertEquals("Connection works.", call(stream(), "application/json"))
    }
    @Test fun completedJsonResponseIsAcceptedWithoutSendingASecondRequest() = runBlocking {
        val calls = AtomicInteger()
        assertEquals("Connection works.", call(result().toString(), "application/json", calls = calls))
        assertEquals(1, calls.get())
    }
    @Test fun jsonCompletionEventIsAccepted() = runBlocking {
        assertEquals("Connection works.", call(JSONObject().put("type", "response.completed").put("response", result()).toString(), "application/json"))
    }
    @Test fun incompleteJsonIsNeverTreatedAsSuccess() = runBlocking {
        try { call(result("incomplete").toString(), "application/json"); fail("Expected incomplete result rejection") }
        catch (failure: IOException) {
            assertTrue(failure.message!!.contains("did not confirm"))
            assertTrue(failure.message!!.contains("HTTP 200"))
            assertTrue(failure.message!!.contains("req_test_123"))
        }
    }
    @Test fun successStatusJsonErrorPreservesItsRealErrorCode() = runBlocking {
        try { call("""{"error":{"code":"subscription_sharing_user_not_eligible","message":"synthetic-private-value"}}""", "application/json"); fail("Expected error") }
        catch (failure: ChatGptException) {
            assertEquals("subscription_sharing_user_not_eligible", failure.code)
            assertTrue(failure.message!!.contains("not eligible"))
            assertTrue(failure.message!!.contains("JSON error"))
            assertFalse(failure.message!!.contains("synthetic-private-value"))
        }
    }
    @Test fun htmlReplyShowsProtocolDiagnosticsWithoutEchoingItsBody() = runBlocking {
        try { call("<html>private@example.com synthetic-token</html>", "text/html"); fail("Expected HTML rejection") }
        catch (failure: IOException) {
            assertTrue(failure.message!!.contains("web page"))
            assertTrue(failure.message!!.contains("HTTP 200 · text/html · HTML page"))
            assertFalse(failure.message!!.contains("private@example.com"))
            assertFalse(failure.message!!.contains("synthetic-token"))
        }
    }
    @Test fun detailOnlyErrorKeepsStatusAndRequestIdWithoutLeakingDetailText() = runBlocking {
        try { call("""{"detail":"Bearer synthetic-token private@example.com"}""", "application/json", 403); fail("Expected admission error") }
        catch (failure: ChatGptException) {
            assertEquals(403, failure.status)
            assertTrue(failure.message!!.contains("JSON detail"))
            assertTrue(failure.message!!.contains("req_test_123"))
            assertFalse(failure.message!!.contains("synthetic-token"))
        }
    }
    @Test fun unrelatedJsonCannotBecomeAVerifiedConnection() = runBlocking {
        try { call("""{"message":"hello","access_token":"synthetic-secret"}""", "application/json"); fail("Expected unknown JSON rejection") }
        catch (failure: IOException) {
            assertTrue(failure.message!!.contains("unexpected reply"))
            assertTrue(failure.message!!.contains("JSON object"))
            assertFalse(failure.message!!.contains("synthetic-secret"))
        }
    }
    @Test fun failedStreamIncludesRequestIdAndKeepsUsageLimitClassification() = runBlocking {
        val body = "data: {\"type\":\"response.failed\",\"response\":{\"error\":{\"code\":\"subscription_sharing_usage_limit_exceeded\"}}}\n\n"
        try { call(body); fail("Expected usage failure") }
        catch (failure: ChatGptException) {
            assertEquals("subscription_sharing_usage_limit_exceeded", failure.code)
            assertTrue(failure.message!!.contains("req_test_123"))
        }
    }
}
