package com.jjteo.phototodocs.chatgpt

import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.Socket
import java.net.URI
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeNoException
import org.junit.Test
import android.graphics.Bitmap
import com.jjteo.phototodocs.ai.AiDocumentText
import java.util.concurrent.ConcurrentLinkedQueue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject

class AndroidSignInTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun encryptedCredentialsSurviveReopenWithoutPlaintextTokens() {
        val namespace = "test-${UUID.randomUUID()}"
        val file = File(context.noBackupFilesDir, "chatgpt/connection-$namespace.enc")
        try {
            val vault = ChatGptStore(context, namespace)
            val original = vault.read()
            val profile = ChatGptProfile("test-profile", "issued-test-client", "test-subject", "test@example.com",
                "synthetic-access-value", "synthetic-refresh-value", "synthetic-id-token", setOf(ChatGptProtocol.PLAN_SCOPE), System.currentTimeMillis() + 3600000)
            vault.write(original.copy(selected = profile.id, profiles = listOf(profile)))
            val bytes = file.readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(bytes.contains(profile.accessToken)); assertFalse(bytes.contains(profile.refreshToken)); assertFalse(bytes.contains(profile.email))
            val reopened = ChatGptStore(context, namespace).read()
            assertEquals(original.host, reopened.host)
            assertEquals(profile, reopened.profiles.single())
        } finally {
            file.delete(); File(file.path + ".bak").delete()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("phototodocs-chatgpt-v1-$namespace") }
        }
    }
    @Test fun loopbackRejectsForgedStateThenAcceptsTheMatchingCallback() = runBlocking {
        withContext(Dispatchers.IO) {
            LoopbackSignIn().use { listener ->
                val pending = async { listener.await("expected-state", "issued-client") }
                val port = URI(listener.redirect).port
                fun request(state: String): String = Socket("127.0.0.1", port).use { socket ->
                    socket.soTimeout = 5000
                    socket.getOutputStream().write("GET /auth/callback?code=synthetic-code&state=$state HTTP/1.1\r\nHost: 127.0.0.1:$port\r\n\r\n".toByteArray())
                    socket.getInputStream().bufferedReader().readLine()
                }
                // A separate dispatcher is needed while the blocking socket waits for the listener.
                val bad = async(Dispatchers.IO) { request("forged-state") }
                assertTrue(bad.await().contains("400"))
                val good = async(Dispatchers.IO) { request("expected-state") }
                assertTrue(good.await().contains("200"))
                assertEquals("synthetic-code" to "issued-client", withTimeout(10_000) { pending.await() })
            }
        }
    }
    @Test fun externalBrowserCanReturnToTheNativeLoopbackListener() = runBlocking {
        val browser = Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1/")).setPackage("com.android.chrome").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        withContext(Dispatchers.IO) {
            LoopbackSignIn().use { listener ->
                val pending = async { listener.await("browser-state", "browser-issued-client") }
                browser.data = Uri.parse("${listener.redirect}?code=browser-test-code&state=browser-state")
                try { context.startActivity(browser) } catch (missing: android.content.ActivityNotFoundException) { assumeNoException(missing) }
                assertEquals("browser-test-code" to "browser-issued-client", withTimeout(45_000) { pending.await() })
            }
        }
    }
    @Test fun cloudPhotoAndTextEditingUseTheActualProviderWithoutLocalWeights() = runBlocking {
        val photo = File.createTempFile("chatgpt-photo-test-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(128, 96, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.WHITE)
            photo.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            val original = photo.readBytes()
            val requests = ConcurrentLinkedQueue<JSONObject>()
            val output = "## Test note\n\n${'$'}x^2${'$'}\n${AiDocumentText.END_MARKER}"
            val event = JSONObject().put("type", "response.completed").put("response", JSONObject().put("object", "response").put("status", "completed")
                .put("output", JSONArray().put(JSONObject().put("type", "message").put("role", "assistant").put("content",
                    JSONArray().put(JSONObject().put("type", "output_text").put("text", output))))))
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                requests.add(JSONObject(Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8()))
                val stream = requests.size == 1
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body((if (stream) "data: $event\n\n" else event.getJSONObject("response").toString())
                        .toResponseBody((if (stream) "text/event-stream" else "application/json").toMediaType())).build()
            }.build()
            val profile = ChatGptProfile("test-cloud", "test-client", "test-subject", "test@example.com", "synthetic-access", "synthetic-refresh", "synthetic-id",
                setOf(ChatGptProtocol.PLAN_SCOPE), System.currentTimeMillis() + 3_600_000, model = "gpt-6-luna")
            val vault = object : ChatGptVault {
                var value = ChatGptSaved("urn:uuid:test", profile.id, listOf(profile))
                override fun read() = value
                override fun write(saved: ChatGptSaved) { value = saved }
            }
            val connection = ChatGptConnection(vault, ChatGptTransport(client))
            connection.initialize()
            val provider = ChatGptAi(connection)
            assertEquals(output.substringBefore(AiDocumentText.END_MARKER).trim(), provider.transcribe(photo, "OCR hint") {})
            assertEquals(output.substringBefore(AiDocumentText.END_MARKER).trim(), provider.revise("Original text", "Improve formatting") {})
            assertEquals(2, requests.size)
            val first = requests.poll()
            assertFalse(first.getBoolean("store")); assertTrue(first.getBoolean("stream"))
            val content = first.getJSONArray("input").getJSONObject(0).getJSONArray("content")
            val image = content.getJSONObject(1).getString("image_url")
            assertTrue(image.startsWith("data:image/jpeg;base64,"))
            val decoded = java.util.Base64.getDecoder().decode(image.substringAfter(','))
            assertArrayEquals(original, decoded)
            val edit = requests.poll().getJSONArray("input").getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("text")
            assertEquals("Original text", JSONObject(edit).getString("document"))
            assertArrayEquals(original, photo.readBytes())
        } finally { bitmap.recycle(); photo.delete() }
    }
}
