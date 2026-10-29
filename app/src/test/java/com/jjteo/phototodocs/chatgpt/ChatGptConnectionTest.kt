package com.jjteo.phototodocs.chatgpt

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class ChatGptConnectionTest {
    private class Vault(var value: ChatGptSaved) : ChatGptVault {
        override fun read() = value
        override fun write(saved: ChatGptSaved) { value = saved }
    }
    private fun profile(id: String = "a", expiry: Long = System.currentTimeMillis() + 3_600_000) = ChatGptProfile(id, "client-$id", "subject-$id", "$id@example.com",
        "access-$id", "refresh-$id", "id-$id", setOf(ChatGptProtocol.PLAN_SCOPE), expiry, model = "gpt-6-luna")
    private val catalog = """{"models":[{"slug":"gpt-6-luna","display_name":"Luna","visibility":"list"}]}"""
    @Test fun concurrentRequestsRefreshRotatingTokensOnlyOnce() = runBlocking {
        val vault = Vault(ChatGptSaved("urn:uuid:test", "a", listOf(profile(expiry = 0))))
        val refreshes = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = if (request.url.encodedPath.endsWith("/oauth/token")) {
                refreshes.incrementAndGet()
                Thread.sleep(50)
                val form = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
                assertTrue(form.contains("refresh_token=refresh-a"))
                assertFalse(form.contains("dynamic_agent_client"))
                """{"access_token":"new-access","refresh_token":"new-refresh","token_type":"Bearer","expires_in":3600,"scope":"chatgpt.tokens.use.direct"}"""
            } else {
                assertEquals("Bearer new-access", request.header("Authorization"))
                catalog
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val connection = ChatGptConnection(vault, ChatGptTransport(client))
        connection.initialize()
        listOf(async { connection.loadModels() }, async { connection.loadModels() }).awaitAll()
        assertEquals(1, refreshes.get())
        assertEquals("new-refresh", vault.value.profiles.single().refreshToken)
        assertEquals("gpt-6-luna", connection.state.value.model)
        assertFalse(connection.state.value.verifiedAt != null)
    }
    @Test fun switchingAccountsKeepsTokenClientAndModelRegistrationsSeparate() = runBlocking {
        val vault = Vault(ChatGptSaved("urn:uuid:test", "a", listOf(profile(), profile("b"))))
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("Bearer access-b", chain.request().header("Authorization"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(catalog.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val connection = ChatGptConnection(vault, ChatGptTransport(client))
        connection.initialize(); connection.selectAccount("b")
        assertEquals("b", connection.state.value.selectedId)
        assertEquals("refresh-a", vault.value.profiles.find { it.id == "a" }!!.refreshToken)
        assertEquals("client-b", vault.value.profiles.find { it.id == "b" }!!.clientId)
    }
    @Test fun identityOnlyConnectionCannotCallInferenceOrLoadModels() = runBlocking {
        val vault = Vault(ChatGptSaved("urn:uuid:test", "a", listOf(profile().copy(scopes = setOf("openid")))))
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { calls.incrementAndGet(); error("Network must not be called") }.build()
        val connection = ChatGptConnection(vault, ChatGptTransport(client))
        connection.initialize()
        assertTrue(connection.state.value.connected)
        assertFalse(connection.state.value.ready)
        try { connection.testConnection(); fail("Expected missing plan permission") } catch (_: IllegalArgumentException) { }
        assertEquals(0, calls.get())
    }
    @Test fun failedNewSignInDoesNotClearAnExistingSelectedAccount() = runBlocking {
        val vault = Vault(ChatGptSaved("urn:uuid:test", "a", listOf(profile())))
        val connection = ChatGptConnection(vault, ChatGptTransport())
        connection.initialize()
        connection.reportFailure(ChatGptProtocol.failure(400, """{"error":"invalid_grant"}"""))
        assertEquals("refresh-a", vault.value.profiles.single().refreshToken)
        assertTrue(connection.state.value.connected)
    }
    @Test fun expiredRefreshClearsOnlyItsOwnTokensAndKeepsRegistration() = runBlocking {
        val vault = Vault(ChatGptSaved("urn:uuid:test", "a", listOf(profile(expiry = 0), profile("b"))))
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(400).message("Bad Request")
                .body("""{"error":"invalid_grant"}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val connection = ChatGptConnection(vault, ChatGptTransport(client))
        connection.initialize()
        try { connection.loadModels(); fail("Expected expired refresh token") } catch (_: ChatGptException) { }
        val a = vault.value.profiles.find { it.id == "a" }!!
        assertFalse(a.connected); assertEquals("", a.refreshToken); assertEquals("client-a", a.clientId)
        assertEquals("refresh-b", vault.value.profiles.find { it.id == "b" }!!.refreshToken)
        assertFalse(connection.state.value.connected)
    }
    @Test fun successfulTestNeedsACompletedResponseAndUsesOnlySupportedRequestFields() = runBlocking {
        val vault = Vault(ChatGptSaved("urn:uuid:test", "a", listOf(profile())))
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val payload = org.json.JSONObject(Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8())
            assertEquals("gpt-6-luna", payload.getString("model"))
            assertFalse(payload.getBoolean("store")); assertTrue(payload.getBoolean("stream"))
            assertEquals(setOf("model", "instructions", "input", "store", "stream"), payload.keys().asSequence().toSet())
            val data = "data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"Connection works.\"}]}]}}\n\n"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(data.toResponseBody("text/event-stream".toMediaType())).build()
        }.build()
        val connection = ChatGptConnection(vault, ChatGptTransport(client))
        connection.initialize(); assertNull(connection.state.value.verifiedAt)
        connection.testConnection()
        assertNotNull(connection.state.value.verifiedAt)
        assertTrue(connection.state.value.status.contains("verified"))
    }
    @Test fun offlineDisconnectClearsLocalTokensButDoesNotClaimRemoteRevocation() = runBlocking {
        val vault = Vault(ChatGptSaved("urn:uuid:test", "a", listOf(profile(), profile("b"))))
        val client = OkHttpClient.Builder().addInterceptor { throw java.io.IOException("Offline test") }.build()
        val connection = ChatGptConnection(vault, ChatGptTransport(client))
        connection.initialize()
        assertFalse(connection.disconnect())
        assertFalse(connection.state.value.connected)
        assertTrue(connection.state.value.status.contains("not confirmed"))
        assertEquals("refresh-b", vault.value.profiles.find { it.id == "b" }!!.refreshToken)
        assertEquals("urn:uuid:test", vault.value.host)
    }
    @Test fun cancellationDuringTokenCommitKeepsDiskAndMemoryOnTheSameRotatingToken() = runBlocking {
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val initial = ChatGptSaved("urn:uuid:test", "a", listOf(profile(expiry = 0)))
        val vault = object : ChatGptVault {
            @Volatile var value = initial
            override fun read() = value
            override fun write(saved: ChatGptSaved) {
                value = saved
                if (saved.profiles.single().accessToken == "committed-access") {
                    entered.countDown()
                    check(finish.await(5, TimeUnit.SECONDS))
                }
            }
        }
        val refreshes = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val body = if (chain.request().url.encodedPath.endsWith("/oauth/token")) {
                refreshes.incrementAndGet()
                """{"access_token":"committed-access","refresh_token":"committed-refresh","token_type":"Bearer","expires_in":3600,"scope":"chatgpt.tokens.use.direct"}"""
            } else catalog
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val connection = ChatGptConnection(vault, ChatGptTransport(client))
        connection.initialize()
        val request = launch { connection.loadModels() }
        assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
        request.cancel(); finish.countDown(); request.cancelAndJoin()
        connection.loadModels()
        assertEquals(1, refreshes.get())
        assertEquals("committed-refresh", vault.value.profiles.single().refreshToken)
    }
}
