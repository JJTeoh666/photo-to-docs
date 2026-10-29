package com.jjteo.phototodocs.chatgpt

import java.net.URI
import java.net.URLDecoder
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ChatGptProtocolTest {
    private fun params(url: String) = URI(url).rawQuery.split('&').associate {
        val pair = it.split('=', limit = 2)
        URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
    }
    @Test fun pkceMatchesTheRfc7636TestVector() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", ChatGptProtocol.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        assertEquals(43, ChatGptProtocol.secret().length)
        assertNotEquals(ChatGptProtocol.secret(), ChatGptProtocol.secret())
    }
    @Test fun firstRegistrationUsesDynamicClientAndFullPlanScopes() {
        val p = params(ChatGptProtocol.authorize("urn:uuid:host", "http://127.0.0.1:1234/auth/callback", "state", "nonce", "verifier"))
        assertEquals("dynamic_agent_client", p["client_id"])
        assertEquals("PhotoToDocs", p["agent_name_hint"])
        assertEquals("S256", p["code_challenge_method"])
        assertEquals("urn:uuid:host", p["ext_agent_host_id"])
        assertEquals(ChatGptProtocol.SCOPES, p["scope"])
        assertEquals(ChatGptProtocol.RESOURCE, p["resource"])
        assertFalse(p.containsKey("client_secret"))
    }
    @Test fun returningRegistrationRetainsIssuedIdAndUsesSeparateNonce() {
        val p = params(ChatGptProtocol.authorize("host", "callback", "state2", "nonce2", "verifier", "oaiapp_saved", "saved-id-token"))
        assertEquals("oaiapp_saved", p["client_id"])
        assertEquals("saved-id-token", p["id_token_hint"])
        assertEquals("nonce2", p["nonce"])
        assertFalse(p.containsKey("agent_name_hint"))
    }
    @Test fun callbackUsesIssuedIdAndRejectsCrossAccountOrForgedState() {
        assertEquals("code" to "issued", ChatGptProtocol.callback("/auth/callback?code=code&state=expected&client_id=issued", "expected", null))
        assertEquals("code" to "saved", ChatGptProtocol.callback("/auth/callback?code=code&state=expected", "expected", "saved"))
        for (target in listOf("/auth/callback?code=x&state=wrong&client_id=id", "/auth/callback?code=x&state=expected&state=expected&client_id=id",
            "/auth/callback?code=x&state=expected", "/other?code=x&state=expected&client_id=id")) {
            assertThrows(IllegalArgumentException::class.java) { ChatGptProtocol.callback(target, "expected", null) }
        }
        assertThrows(IllegalArgumentException::class.java) { ChatGptProtocol.callback("/auth/callback?code=x&state=s&client_id=other", "s", "original") }
    }
    @Test fun declinedConsentIsNotSuccessfulAuthentication() {
        val error = assertThrows(ChatGptException::class.java) { ChatGptProtocol.callback("/auth/callback?state=s&error=access_denied", "s", null) }
        assertEquals("consent_declined", error.code)
    }
    @Test fun catalogUsesChatGptSlugsAndServerOrder() {
        val models = ChatGptProtocol.models(JSONObject("""{"models":[{"slug":"hidden","visibility":"hide"},{"slug":"gpt-6-luna","display_name":"Luna","visibility":"list"},{"slug":"another","visibility":"list"}]}"""))
        assertEquals(listOf("gpt-6-luna", "another"), models.map { it.slug })
        assertEquals("Luna", models.first().name)
    }
    @Test fun eligibilityUsageAndTemporaryFailureAreDifferentAndNeverEchoTokens() {
        val ineligible = ChatGptProtocol.failure(403, """{"error":{"code":"subscription_sharing_user_not_eligible","message":"private-token"}}""")
        assertTrue(ineligible.message!!.contains("not eligible"))
        assertFalse(ineligible.message!!.contains("private-token"))
        assertTrue(ChatGptProtocol.failure(429, """{"error":{"code":"subscription_sharing_usage_limit_exceeded"}}""").message!!.contains("usage limit"))
        assertFalse(ChatGptProtocol.failure(503, "unavailable").message!!.contains("not eligible"))
        assertTrue(ChatGptProtocol.failure(403, """{"detail":"private-token"}""").message!!.contains("policy"))
    }
}
