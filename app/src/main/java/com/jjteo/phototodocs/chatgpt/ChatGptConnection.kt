package com.jjteo.phototodocs.chatgpt

import android.content.Context
import java.io.IOException
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Owns OAuth and protected profiles. Only display metadata is exposed by state. */
class ChatGptConnection internal constructor(private val store: ChatGptVault, private val http: ChatGptTransport) {
    private constructor(context: Context) : this(ChatGptStore(context), ChatGptTransport())
    companion object {
        @Volatile private var instance: ChatGptConnection? = null
        fun get(context: Context): ChatGptConnection = instance ?: synchronized(this) {
            instance ?: ChatGptConnection(context.applicationContext).also { instance = it }
        }
    }
    private val mutex = Mutex()
    private var saved: ChatGptSaved? = null
    @Volatile private var listener: LoopbackSignIn? = null
    private val _state = MutableStateFlow(ChatGptUiState())
    val state = _state.asStateFlow()

    suspend fun initialize() = mutex.withLock {
        if (saved != null) return@withLock
        saved = withContext(Dispatchers.IO) { store.read() }
        publish()
    }

    private fun selected(): ChatGptProfile? = saved?.let { s -> s.profiles.find { it.id == s.selected } }
    private suspend fun persist(value: ChatGptSaved) {
        // A rotating refresh token must be committed to disk and memory together, even if its caller cancels.
        withContext(NonCancellable) {
            withContext(Dispatchers.IO) { store.write(value) }
            saved = value
        }
    }
    private suspend fun replace(profile: ChatGptProfile, activate: Boolean = false) {
        val value = requireNotNull(saved)
        val profiles = value.profiles.filterNot { it.id == profile.id } + profile
        persist(value.copy(profiles = profiles, selected = if (activate) profile.id else value.selected))
    }
    private fun publish(message: String? = null, resetModels: Boolean = false) {
        val profile = selected()
        val previous = _state.value
        _state.value = previous.copy(
            accounts = saved?.profiles.orEmpty().map { ChatGptAccount(it.id, it.label, it.connected) },
            selectedId = profile?.id, connected = profile?.connected == true, planEnabled = profile?.planEnabled == true,
            model = profile?.model, verifiedAt = profile?.verifiedAt,
            models = if (resetModels) emptyList() else previous.models,
            status = message ?: when {
                profile == null -> "Not connected. Eligible Plus or Pro accounts can use their ChatGPT plan."
                !profile.connected -> "Sign in to reconnect this account."
                !profile.planEnabled -> "Signed in. Permission to use your ChatGPT plan was not granted."
                profile.verifiedAt != null -> "Connection previously verified. Test again to check current access."
                else -> "Signed in. Load models and use Test connection to verify access."
            },
        )
    }
    suspend fun signIn(profileId: String?, openBrowser: (String) -> Unit) {
        initialize()
        val previous = saved!!.profiles.find { it.id == profileId }
        val state = ChatGptProtocol.secret()
        val nonce = ChatGptProtocol.secret()
        val verifier = ChatGptProtocol.secret()
        val callback = withContext(Dispatchers.IO) { LoopbackSignIn() }
        listener = callback
        _state.value = _state.value.copy(working = true, signingIn = true, status = "Finish sign-in in your browser, then return here. Sign-in expires after 5 minutes.")
        try {
            val url = ChatGptProtocol.authorize(saved!!.host, callback.redirect, state, nonce, verifier,
                previous?.clientId, consent = previous?.connected == true && !previous.planEnabled)
            withContext(Dispatchers.Main) { openBrowser(url) }
            val (code, clientId) = callback.await(state, previous?.clientId)
            callback.close()
            currentCoroutineContext().ensureActive()
            // Retain the issued registration before exchanging its one-use code. It is not an active identity yet.
            val pending = previous ?: ChatGptProfile(UUID.randomUUID().toString(), clientId)
            mutex.withLock { replace(pending, activate = selected() == null) }
            val tokens = http.json(ChatGptProtocol.TOKEN, form = mapOf("grant_type" to "authorization_code", "client_id" to clientId,
                "code" to code, "code_verifier" to verifier, "redirect_uri" to callback.redirect, "resource" to ChatGptProtocol.RESOURCE))
            val discovery = discovery()
            val keysUrl = allowedAuthUrl(discovery.getString("jwks_uri"))
            val keys = http.json(keysUrl).toString()
            val identity = withContext(Dispatchers.Default) {
                ChatGptIdentityVerifier.verify(tokens.getString("id_token"), clientId, nonce, keys)
            }
            require(previous?.subject.isNullOrBlank() || identity.subject == previous.subject) { "A different account was returned. Use Add account instead." }
            val profile = tokens(pending.copy(subject = identity.subject, email = identity.email, verifiedAt = null), tokens)
            mutex.withLock { replace(profile, activate = true) }
            publish(resetModels = true)
            if (profile.planEnabled) loadModels()
        } finally {
            callback.close()
            if (listener === callback) listener = null
            _state.value = _state.value.copy(working = false, signingIn = false)
        }
    }
    fun cancelSignIn() { listener?.close(); listener = null }

    private fun tokens(profile: ChatGptProfile, value: JSONObject): ChatGptProfile {
        val access = value.getString("access_token")
        val refresh = value.getString("refresh_token")
        val duration = value.getLong("expires_in")
        require(access.isNotBlank() && refresh.isNotBlank() && duration in 1..86_400 && value.optString("token_type").equals("Bearer", true)) { "The sign-in token response was incomplete." }
        val scopes = value.getString("scope").split(' ').filter { it.isNotBlank() }.toSet()
        val earliest = value.opt("earliest_refresh_at")?.let {
            when (it) {
                is Number -> it.toLong().let { seconds -> if (seconds < 100_000_000_000) seconds * 1000 else seconds }
                is String -> runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrDefault(0)
                else -> 0
            }
        } ?: 0
        return profile.copy(accessToken = access, refreshToken = refresh, idToken = value.optString("id_token").takeIf { it.isNotBlank() } ?: profile.idToken,
            scopes = scopes, expiresAt = System.currentTimeMillis() + duration * 1000, earliestRefreshAt = earliest)
    }
    private suspend fun access(profileId: String): ChatGptProfile = mutex.withLock {
        var profile = saved!!.profiles.find { it.id == profileId } ?: throw IOException("Choose a ChatGPT account in Settings.")
        require(profile.connected && profile.planEnabled) { "Connect ChatGPT and enable plan use in Settings first." }
        if (profile.expiresAt <= System.currentTimeMillis() + 60_000) {
            require(profile.refreshToken.isNotBlank()) { "Reconnect ChatGPT in Settings." }
            if (profile.earliestRefreshAt > System.currentTimeMillis()) {
                if (profile.expiresAt <= System.currentTimeMillis()) throw IOException("ChatGPT has not allowed token renewal yet. Try again shortly.")
                return@withLock profile
            }
            val fresh = try {
                http.json(ChatGptProtocol.TOKEN, form = mapOf("grant_type" to "refresh_token", "client_id" to profile.clientId,
                    "refresh_token" to profile.refreshToken, "resource" to ChatGptProtocol.RESOURCE))
            } catch (failure: ChatGptException) {
                if (terminalTokenFailure(failure)) { replace(cleared(profile)); publish(failure.message) }
                throw failure
            }
            profile = tokens(profile, fresh)
            replace(profile)
            publish()
        }
        profile
    }
    suspend fun loadModels() {
        val id = selected()?.id ?: throw IOException("Sign in to ChatGPT first.")
        val profile = access(id)
        val models = try { ChatGptProtocol.models(http.json("${ChatGptProtocol.RESOURCE}/models", profile.accessToken)) }
            catch (failure: ChatGptException) { invalidateIfNeeded(profile, failure); throw failure }
        currentCoroutineContext().ensureActive()
        mutex.withLock {
            require(selected()?.id == id) { "The selected ChatGPT account changed." }
            val current = requireNotNull(selected())
            val model = current.model?.takeIf { slug -> models.any { it.slug == slug } }
                ?: models.find { it.slug == "gpt-6-luna" }?.slug
            replace(current.copy(model = model, verifiedAt = if (model == current.model) current.verifiedAt else null))
            _state.value = _state.value.copy(models = models)
            publish(if (models.isEmpty()) "No models are available to this account. Check your plan and workspace policy."
                else if (model == null) "Choose a model, then use Test connection."
                else "Models loaded. Use Test connection to verify this account and model.")
        }
    }
    suspend fun selectAccount(id: String) {
        initialize()
        mutex.withLock {
            require(saved!!.profiles.any { it.id == id }) { "The saved account is unavailable." }
            persist(saved!!.copy(selected = id))
            publish(resetModels = true)
        }
        if (selected()?.connected == true && selected()?.planEnabled == true) loadModels()
    }
    suspend fun selectModel(slug: String) = mutex.withLock {
        require(_state.value.models.any { it.slug == slug }) { "Choose a model returned for this account." }
        replace(requireNotNull(selected()).copy(model = slug, verifiedAt = null))
        publish("Model selected. Use Test connection to verify access.")
    }
    suspend fun testConnection() {
        val id = selected()?.id ?: throw IOException("Sign in to ChatGPT first.")
        complete(id, "Reply briefly to confirm this connection works.", JSONArray().put(JSONObject().put("type", "input_text").put("text", "Reply with: Connection works."))) {}
    }
    internal suspend fun complete(id: String, instructions: String, content: JSONArray, onPartial: (String) -> Unit): String {
        val profile = access(id)
        val model = profile.model ?: throw IOException("Choose a ChatGPT model in Settings first.")
        val result = try { http.response(profile.accessToken, model, instructions, content, onPartial) }
            catch (failure: ChatGptException) { invalidateIfNeeded(profile, failure); throw failure }
        currentCoroutineContext().ensureActive()
        mutex.withLock {
            require(selected()?.id == id && selected()?.model == model) { "The ChatGPT account or model changed. Run the request again." }
            replace(requireNotNull(selected()).copy(verifiedAt = System.currentTimeMillis()))
            publish("Connection verified with ${_state.value.models.find { it.slug == model }?.name ?: model}.")
        }
        return result
    }
    internal fun activeProfileId(): String = selected()?.id ?: throw IOException("Connect ChatGPT in Settings first.")
    suspend fun disconnect(): Boolean {
        val profile = selected() ?: return true
        var revoked = profile.refreshToken.isBlank()
        try {
            if (!revoked) {
                val endpoint = allowedAuthUrl(discovery().getString("revocation_endpoint"))
                http.json(endpoint, form = mapOf("token" to profile.refreshToken, "token_type_hint" to "refresh_token", "client_id" to profile.clientId))
                revoked = true
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Still clear local tokens, while explicitly reporting remote revocation uncertainty. */ }
        mutex.withLock {
            replace(cleared(profile))
            publish(if (revoked) "Disconnected. Saved notes and local AI are unchanged." else "Disconnected on this phone. Remote revocation was not confirmed; disconnect PhotoToDocs in ChatGPT Settings too.", resetModels = true)
        }
        return revoked
    }
    suspend fun reportFailure(failure: Exception) {
        publish(when (failure) {
            is ChatGptException -> failure.message
            is IOException -> failure.message?.take(400) ?: "ChatGPT could not connect. Check your network and retry."
            else -> "The ChatGPT connection could not be validated. Retry sign-in or load models again."
        })
    }
    private fun terminalTokenFailure(failure: ChatGptException) = failure.status == 401 || failure.code in setOf(
        "invalid_grant", "invalid_refresh_token", "refresh_token_expired", "refresh_token_reused", "token_expired", "refresh_token_invalidated", "refresh_token_invalid", "subscription_sharing_invalid_user")
    private fun cleared(profile: ChatGptProfile) = profile.copy(accessToken = "", refreshToken = "", idToken = "", scopes = emptySet(), expiresAt = 0, earliestRefreshAt = 0, verifiedAt = null)
    private suspend fun invalidateIfNeeded(profile: ChatGptProfile, failure: ChatGptException) {
        if (!terminalTokenFailure(failure)) return
        currentCoroutineContext().ensureActive()
        mutex.withLock {
            val current = saved?.profiles?.find { it.id == profile.id }
            if (current?.accessToken == profile.accessToken) replace(cleared(current))
            publish(failure.message)
        }
    }
    fun setWorking(value: Boolean) { _state.value = _state.value.copy(working = value) }
    fun cancelled() { _state.value = _state.value.copy(working = false, signingIn = false, status = "Sign-in or connection check cancelled. Your notes are unchanged.") }
    private suspend fun discovery(): JSONObject = http.json("${ChatGptProtocol.ISSUER}/.well-known/openid-configuration").also {
        require(it.getString("issuer") == ChatGptProtocol.ISSUER && it.getString("authorization_endpoint") == ChatGptProtocol.AUTHORIZE &&
            it.getString("token_endpoint") == ChatGptProtocol.TOKEN) { "OpenAI sign-in metadata was not accepted." }
    }
    private fun allowedAuthUrl(value: String): String {
        val uri = URI(value)
        require(uri.scheme == "https" && uri.host == "auth.openai.com" && uri.userInfo == null && uri.port == -1 && uri.fragment == null) { "OpenAI returned an unexpected account endpoint." }
        return value
    }
}
