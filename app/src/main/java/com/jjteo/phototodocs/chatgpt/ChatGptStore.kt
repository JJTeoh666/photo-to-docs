package com.jjteo.phototodocs.chatgpt

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

/** Secret records never cross into Compose state, saved notes, diagnostics, or browser storage. */
internal data class ChatGptProfile(
    val id: String,
    val clientId: String,
    val subject: String = "",
    val email: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val idToken: String = "",
    val scopes: Set<String> = emptySet(),
    val expiresAt: Long = 0,
    val earliestRefreshAt: Long = 0,
    val model: String? = null,
    val verifiedAt: Long? = null,
) {
    val connected: Boolean get() = accessToken.isNotBlank()
    val planEnabled: Boolean get() = ChatGptProtocol.PLAN_SCOPE in scopes
    val label: String get() = "${email.ifBlank { "ChatGPT account" }} · ${id.takeLast(6)}"
    override fun toString() = "ChatGptProfile([protected])"
    fun toJson() = JSONObject().put("id", id).put("client_id", clientId).put("subject", subject).put("email", email)
        .put("access_token", accessToken).put("refresh_token", refreshToken).put("id_token", idToken)
        .put("scopes", JSONArray(scopes.toList())).put("expires_at", expiresAt).put("earliest_refresh_at", earliestRefreshAt)
        .put("model", model ?: JSONObject.NULL).put("verified_at", verifiedAt ?: JSONObject.NULL)
    companion object {
        fun parse(json: JSONObject) = ChatGptProfile(
            id = json.getString("id"), clientId = json.getString("client_id"), subject = json.optString("subject"), email = json.optString("email"),
            accessToken = json.optString("access_token"), refreshToken = json.optString("refresh_token"), idToken = json.optString("id_token"),
            scopes = json.optJSONArray("scopes")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
            expiresAt = json.optLong("expires_at"), earliestRefreshAt = json.optLong("earliest_refresh_at"),
            model = if (json.isNull("model")) null else json.optString("model").takeIf { it.isNotBlank() },
            verifiedAt = if (json.isNull("verified_at")) null else json.optLong("verified_at").takeIf { it > 0 },
        )
    }
}

internal data class ChatGptSaved(val host: String, val selected: String?, val profiles: List<ChatGptProfile>) {
    override fun toString() = "ChatGptSaved([protected])"
}

internal interface ChatGptVault {
    fun read(): ChatGptSaved
    fun write(saved: ChatGptSaved)
}

internal class ChatGptStore(context: Context, namespace: String = "") : ChatGptVault {
    init { require(namespace.matches(Regex("[A-Za-z0-9_-]{0,80}"))) }
    private val suffix = if (namespace.isEmpty()) "" else "-$namespace"
    private val file = AtomicFile(File(context.noBackupFilesDir, "chatgpt/connection$suffix.enc").apply { parentFile?.mkdirs() })
    private val alias = "phototodocs-chatgpt-v1$suffix"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override fun read(): ChatGptSaved {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) {
            return ChatGptSaved("urn:uuid:${UUID.randomUUID()}", null, emptyList()).also(::write)
        }
        val bytes = file.openRead().use { input ->
            require(input.available() <= 1_000_000) { "Saved ChatGPT connections are too large." }
            input.readBytes()
        }
        require(bytes.size > 29 && bytes[0] == 1.toByte()) { "Saved ChatGPT connection format is invalid." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(alias.toByteArray(Charsets.UTF_8))
        val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(13, bytes.size)), Charsets.UTF_8))
        val profiles = json.getJSONArray("profiles")
        return ChatGptSaved(json.getString("host"), if (json.isNull("selected")) null else json.optString("selected"),
            (0 until profiles.length()).map { ChatGptProfile.parse(profiles.getJSONObject(it)) })
    }
    override fun write(saved: ChatGptSaved) {
        val json = JSONObject().put("host", saved.host).put("selected", saved.selected ?: JSONObject.NULL)
            .put("profiles", JSONArray(saved.profiles.map { it.toJson() }))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(alias.toByteArray(Charsets.UTF_8))
        val bytes = byteArrayOf(1) + cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (failure: Exception) { file.failWrite(output); throw failure }
    }
}
