package com.jjteo.phototodocs.chatgpt

import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.json.JSONObject

data class ChatGptModel(val slug: String, val name: String)
data class ChatGptAccount(val id: String, val label: String, val connected: Boolean)
data class ChatGptUiState(
    val accounts: List<ChatGptAccount> = emptyList(),
    val selectedId: String? = null,
    val models: List<ChatGptModel> = emptyList(),
    val model: String? = null,
    val connected: Boolean = false,
    val planEnabled: Boolean = false,
    val working: Boolean = false,
    val signingIn: Boolean = false,
    val status: String = "Not connected. Eligible Plus or Pro accounts can use their ChatGPT plan.",
    val verifiedAt: Long? = null,
) {
    val ready: Boolean get() = connected && planEnabled && model != null
}

internal object ChatGptProtocol {
    const val ISSUER = "https://auth.openai.com"
    const val AUTHORIZE = "$ISSUER/api/accounts/authorize"
    const val TOKEN = "$ISSUER/api/accounts/oauth/token"
    const val RESOURCE = "https://api.openai.com/v1"
    const val PLAN_SCOPE = "chatgpt.tokens.use.direct"
    const val SCOPES = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
    const val USAGE = "https://chatgpt.com/settings/usage"
    private val random = SecureRandom()
    fun secret(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
    fun challenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    fun authorize(host: String, redirect: String, state: String, nonce: String, verifier: String,
                  clientId: String? = null, idToken: String? = null, consent: Boolean = false): String {
        val params = linkedMapOf("client_id" to (clientId ?: "dynamic_agent_client"), "ext_agent_host_id" to host,
            "response_type" to "code", "redirect_uri" to redirect, "scope" to SCOPES, "resource" to RESOURCE,
            "state" to state, "nonce" to nonce, "code_challenge_method" to "S256", "code_challenge" to challenge(verifier))
        if (clientId == null) params["agent_name_hint"] = "PhotoToDocs"
        else if (!idToken.isNullOrBlank()) params["id_token_hint"] = idToken
        if (consent) params["prompt"] = "consent"
        return AUTHORIZE + "?" + params.entries.joinToString("&") { encode(it.key) + "=" + encode(it.value) }
    }
    fun callback(target: String, state: String, existingClient: String?): Pair<String, String> {
        val uri = URI(target)
        require(uri.path == "/auth/callback" && uri.fragment == null) { "Unexpected callback path." }
        val params = linkedMapOf<String, String>()
        uri.rawQuery.orEmpty().split('&').filter { it.isNotBlank() }.forEach {
            val pair = it.split('=', limit = 2)
            val key = URLDecoder.decode(pair[0], "UTF-8")
            require(!params.containsKey(key)) { "Duplicate callback parameter." }
            params[key] = URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
        }
        require(MessageDigest.isEqual(state.toByteArray(), params["state"].orEmpty().toByteArray())) { "Sign-in state did not match. Start sign-in again." }
        if (params.containsKey("error")) throw ChatGptException("consent_declined", "Sign-in was declined. Your notes are unchanged.")
        val client = params["client_id"] ?: existingClient
        require(!client.isNullOrBlank() && client != "dynamic_agent_client" && client.length <= 256) { "The app registration did not finish. Sign in again." }
        require(existingClient == null || client == existingClient) { "The returned account registration changed." }
        val code = params["code"].orEmpty()
        require(code.isNotBlank() && code.length <= 4096) { "The sign-in code is missing." }
        return code to client
    }
    fun models(json: JSONObject): List<ChatGptModel> {
        val array = json.getJSONArray("models")
        return (0 until array.length()).mapNotNull { i ->
            val value = array.getJSONObject(i)
            val slug = value.optString("slug")
            if (value.optString("visibility") != "list" || slug.isBlank() || slug.length > 200) null
            else ChatGptModel(slug, value.optString("display_name", slug).take(200))
        }.distinctBy { it.slug }
    }
    fun failure(status: Int, body: String): ChatGptException {
        val json = runCatching { JSONObject(body) }.getOrNull()
        val code = json?.optJSONObject("error")?.optString("code")?.takeIf { it.isNotBlank() }
            ?: json?.optString("error")?.takeIf { it.matches(Regex("[a-z_]{1,100}")) }
            ?: "http_$status"
        val message = when (code) {
            "subscription_sharing_user_not_eligible" -> "This account or workspace is not eligible for ChatGPT plan use in this app. Check your Plus/Pro account and workspace policy."
            "subscription_sharing_usage_limit_exceeded" -> "A ChatGPT usage limit was reached. Open Manage usage to check your plan and this app's allowance."
            "subscription_sharing_usage_unavailable", "subscription_sharing_user_unavailable" -> "ChatGPT usage could not be checked. Try again later."
            "subscription_sharing_unsupported_capability" -> "This model or request feature is not available through ChatGPT plan use. Choose another available model."
            "invalid_grant", "invalid_refresh_token", "refresh_token_expired", "refresh_token_reused", "token_expired", "refresh_token_invalidated", "refresh_token_invalid", "subscription_sharing_invalid_user" -> "Your ChatGPT connection needs a new sign-in. Reconnect in Settings."
            else -> when (status) {
                0 -> "ChatGPT could not complete the streamed request. Your document is unchanged."
                401 -> "ChatGPT did not accept this connection. Reconnect in Settings."
                403 -> "ChatGPT restricted this request. Check account permissions, workspace policy, and serving-region availability."
                429 -> "ChatGPT is limiting requests. Check Manage usage and try again later."
                503 -> "ChatGPT is temporarily unavailable. Your connection is saved; try again later."
                else -> "ChatGPT could not complete the request (HTTP $status). Your document is unchanged."
            }
        }
        return ChatGptException(code, message, status)
    }
}

internal class ChatGptException(val code: String, message: String, val status: Int = 0) : IOException(message)
