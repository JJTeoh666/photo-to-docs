package com.jjteo.phototodocs.chatgpt

import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/** No credential logging, redirects, implicit retries, or alternate billing route. */
internal class ChatGptTransport(private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).callTimeout(180, TimeUnit.SECONDS)
    .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()) {
    suspend fun json(url: String, token: String? = null, form: Map<String, String>? = null): JSONObject {
        val request = Request.Builder().url(url).header("Accept", "application/json")
        token?.let { request.header("Authorization", "Bearer $it") }
        form?.let { values -> request.post(FormBody.Builder().apply { values.forEach { (k, v) -> add(k, v) } }.build()) }
        return execute(request.build()) { response ->
            val body = response.body ?: throw IOException("ChatGPT returned an empty response.")
            val source = body.source()
            if (source.request(2_000_001) && source.buffer.size > 2_000_000) throw IOException("ChatGPT returned an oversized response.")
            val text = source.readUtf8()
            if (!response.isSuccessful) throw ChatGptProtocol.failure(response.code, text)
            if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    suspend fun response(token: String, model: String, instructions: String, content: JSONArray, onPartial: (String) -> Unit): String {
        val payload = JSONObject().put("model", model).put("instructions", instructions)
            .put("input", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            .put("store", false).put("stream", true)
        val request = Request.Builder().url("${ChatGptProtocol.RESOURCE}/responses")
            .header("Authorization", "Bearer $token").header("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        return execute(request) { response ->
            val body = response.body ?: throw IOException("ChatGPT returned an empty stream.")
            val source = body.source()
            // Header values are case-insensitive, and intermediaries can mislabel an otherwise valid stream.
            // Peek without consuming data; never retry a request that might already have used the plan allowance.
            source.request(64)
            val prefix = source.buffer.clone().readUtf8(minOf(source.buffer.size, 64)).removePrefix("\uFEFF").trimStart()
            val eventStream = body.contentType()?.let { it.type.equals("text", true) && it.subtype.equals("event-stream", true) } == true
            val looksLikeEvents = prefix.startsWith("data:") || prefix.startsWith("event:") || prefix.startsWith(":")
            if (response.isSuccessful && (looksLikeEvents || eventStream && !prefix.startsWith("{") && !prefix.startsWith("<"))) {
                try { return@execute ResponsesStream.read(body.charStream().buffered(), onPartial) }
                catch (failure: IOException) { throw ReplyDiagnostics.withContext(response, "event stream", failure) }
            }
            if (source.request(2_000_001) && source.buffer.size > 2_000_000) {
                throw ReplyDiagnostics.withContext(response, "oversized reply", IOException("OpenAI returned an oversized reply. Your note is unchanged."))
            }
            val text = source.readUtf8().removePrefix("\uFEFF")
            val json = runCatching { JSONObject(text) }.getOrNull()
            val hasError = json?.let { it.has("error") && !it.isNull("error") } == true
            val hasDetail = json?.let { it.has("detail") && !it.isNull("detail") } == true
            val shape = when {
                text.isBlank() -> "empty reply"
                hasError -> "JSON error"
                hasDetail -> "JSON detail"
                json != null -> "JSON object"
                prefix.startsWith("<") -> "HTML page"
                else -> "unrecognized reply"
            }
            if (!response.isSuccessful || hasError || hasDetail) {
                throw ReplyDiagnostics.withContext(response, shape, ChatGptProtocol.failure(response.code, text))
            }
            // Some routes return a complete Responses object despite stream:true. It is safe to use only
            // a terminal completed response, never an arbitrary JSON message or an in-progress draft.
            val completed = when {
                json?.optString("object") == "response" -> json
                json?.optString("type") == "response.completed" -> json.optJSONObject("response")
                else -> null
            }
            if (completed != null) {
                try { return@execute ResponsesStream.completed(completed) }
                catch (failure: IOException) { throw ReplyDiagnostics.withContext(response, "JSON response", failure) }
            }
            val message = if (shape == "HTML page") "OpenAI returned a web page instead of an AI result. A network gateway or service error may be interfering."
                else "OpenAI returned an unexpected reply instead of an AI result. Retry Test connection; if it repeats, share the details below."
            throw ReplyDiagnostics.withContext(response, shape, IOException(message))
        }
    }

    private suspend fun <T> execute(request: Request, read: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException("Could not reach OpenAI. Check your internet connection and try again."))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (!continuation.isActive) return
                        val value = read(response)
                        if (continuation.isActive) continuation.resume(value)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            }
        })
    }
}

/** Only protocol metadata is shown. Never expose the response body, tokens, account info, or note text. */
internal object ReplyDiagnostics {
    fun withContext(response: Response, shape: String, failure: IOException): IOException {
        val type = response.body?.contentType()?.let { "${it.type}/${it.subtype}" } ?: "missing content type"
        val requestId = response.header("x-request-id")?.takeIf { it.matches(Regex("[A-Za-z0-9_.:-]{1,160}")) }
        val details = "HTTP ${response.code} · $type · $shape" + (requestId?.let { " · request $it" } ?: " · no request ID")
        val message = "${failure.message.orEmpty()}\n$details"
        return if (failure is ChatGptException) ChatGptException(failure.code, message, failure.status) else IOException(message)
    }
}

/** Only visible output-text events are included; reasoning and tool payloads are never document text. */
internal object ResponsesStream {
    private const val MAX_TEXT = 200_000
    fun completed(response: JSONObject, streamedText: String = ""): String {
        if (response.optString("status") != "completed") {
            val error = response.optJSONObject("error")
            if (error != null) throw ChatGptProtocol.failure(0, JSONObject().put("error", error).toString())
            throw IOException("ChatGPT did not confirm a complete response. Your note is unchanged.")
        }
        val final = StringBuilder()
        val output = response.optJSONArray("output") ?: JSONArray()
        for (i in 0 until output.length()) {
            val item = output.getJSONObject(i)
            if (item.optString("type") != "message" || item.optString("role") != "assistant") continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.getJSONObject(j)
                if (part.optString("type") == "refusal") throw IOException("ChatGPT declined this request. Your note is unchanged.")
                if (part.optString("type") == "output_text") final.append(part.optString("text"))
            }
        }
        val complete = if (final.isNotEmpty()) final.toString() else streamedText
        if (complete.isBlank() || complete.length > MAX_TEXT) throw IOException("ChatGPT returned an empty or oversized result.")
        return complete
    }
    fun read(reader: BufferedReader, onPartial: (String) -> Unit): String {
        val text = StringBuilder()
        val data = StringBuilder()
        var total = 0
        var lastPreview = 0L
        fun event(): String? {
            if (data.isEmpty()) return null
            val raw = data.toString().trimEnd('\n')
            data.setLength(0)
            if (raw == "[DONE]") throw IOException("ChatGPT ended before confirming a complete response. Your note is unchanged.")
            val value = JSONObject(raw)
            when (value.optString("type")) {
                "response.output_text.delta" -> {
                    text.append(value.optString("delta"))
                    require(text.length <= MAX_TEXT) { "The AI draft is too long. Use a smaller photo or text selection." }
                    val now = System.nanoTime()
                    if (lastPreview == 0L || now - lastPreview >= 80_000_000L) {
                        onPartial(text.toString())
                        lastPreview = now
                    }
                }
                "response.failed", "error" -> {
                    val error = value.optJSONObject("response")?.optJSONObject("error") ?: value.optJSONObject("error") ?: value
                    throw ChatGptProtocol.failure(0, JSONObject().put("error", error).toString())
                }
                "response.incomplete" -> throw IOException("ChatGPT did not finish the draft. Use a smaller photo or text selection. Your note is unchanged.")
                "response.completed" -> {
                    return completed(value.getJSONObject("response"), text.toString())
                }
            }
            return null
        }
        while (true) {
            val line = reader.readLine()?.removePrefix("\uFEFF") ?: break
            total += line.length
            require(total <= 8_000_000) { "ChatGPT returned an oversized stream." }
            if (line.isEmpty()) event()?.let { return it }
            else if (line.startsWith("data:")) {
                data.append(line.substring(5).removePrefix(" ")).append('\n')
                require(data.length <= 2_000_000) { "ChatGPT returned an oversized event." }
            }
        }
        event()?.let { return it }
        throw IOException("The ChatGPT stream was interrupted. Your note is unchanged; retry the request.")
    }
}
