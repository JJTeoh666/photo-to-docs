package com.jjteo.phototodocs.updates

import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import org.json.JSONObject

const val RELEASES_URL = "https://github.com/JJTeoh666/photo-to-docs/releases/latest"
private const val LATEST_API = "https://api.github.com/repos/JJTeoh666/photo-to-docs/releases/latest"

data class AppVersion(val major: Long, val minor: Long, val patch: Long) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    companion object {
        fun parse(value: String): AppVersion? {
            if (value.length > 100) return null
            val match = Regex("^[vV]?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:\\+[0-9A-Za-z.-]+)?$").matchEntire(value.trim()) ?: return null
            return AppVersion(match.groupValues[1].toLongOrNull() ?: return null,
                match.groupValues[2].toLongOrNull() ?: return null, match.groupValues[3].toLongOrNull() ?: return null)
        }
    }
}

enum class UpdateStatus { IDLE, CHECKING, CURRENT, AVAILABLE, ERROR }

data class UpdateUiState(
    val installedVersion: String,
    val status: UpdateStatus = UpdateStatus.IDLE,
    val message: String? = null,
    val latestVersion: String? = null,
    val releaseUrl: String = RELEASES_URL,
)

data class LatestRelease(val tag: String, val version: AppVersion, val url: String)
data class UpdateResponse(val code: Int, val body: String, val remaining: String? = null)

/** Fixed GitHub origin, no redirects or background polling; called only after the user taps Check. */
class GitHubUpdates(private val request: () -> UpdateResponse = ::requestLatest) {
    fun check(installed: String): Pair<LatestRelease, Boolean> {
        val response = try { request() } catch (_: IOException) {
            throw IOException("Could not reach GitHub. Check your connection and try again, or open GitHub in your browser.")
        }
        when (response.code) {
            200 -> Unit
            401 -> throw IOException("GitHub could not read the release. Open GitHub to check the available downloads.")
            403, 429 -> throw IOException(if (response.remaining == "0" || response.code == 429)
                "GitHub's request limit was reached. Try again later, or open GitHub in your browser."
                else "GitHub could not check for updates right now. Try again later, or open GitHub in your browser.")
            404 -> throw IOException("No published release is available yet. Try again later, or open GitHub in your browser.")
            else -> throw IOException("GitHub could not check for updates right now. Try again later, or open GitHub in your browser.")
        }
        val release = parseRelease(response.body)
        val current = AppVersion.parse(installed) ?: throw IOException("The installed version could not be compared. Open GitHub to check the latest release.")
        return release to (release.version > current)
    }

    companion object {
        fun parseRelease(payload: String): LatestRelease {
            try {
                require(payload.length <= 1_048_576)
                val json = JSONObject(payload)
                require(!json.optBoolean("draft") && !json.optBoolean("prerelease"))
                val tag = json.getString("tag_name")
                val version = AppVersion.parse(tag) ?: error("Unsupported release version")
                val uri = URI(json.getString("html_url"))
                require(uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.port == -1)
                require(uri.path.startsWith("/JJTeoh666/photo-to-docs/releases/tag/") && uri.path.length > "/JJTeoh666/photo-to-docs/releases/tag/".length)
                require(uri.query == null && uri.fragment == null)
                val assets = json.getJSONArray("assets")
                require((0 until assets.length()).any { index ->
                    val asset = assets.getJSONObject(index)
                    asset.optString("name") == "PhotoToDocs-debug.apk" && asset.optString("state") == "uploaded" && asset.optLong("size") > 0
                })
                return LatestRelease(tag, version, uri.toASCIIString())
            } catch (_: Exception) {
                throw IOException("GitHub returned an incomplete or unsupported release. Open GitHub to check the available downloads.")
            }
        }
    }
}

private fun requestLatest(): UpdateResponse {
    val connection = URL(LATEST_API).openConnection() as HttpURLConnection
    try {
        connection.apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            connectTimeout = 10_000
            readTimeout = 20_000
            useCaches = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "PhotoToDocs-update-check")
        }
        val code = connection.responseCode
        val body = if (code == 200) connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > 1_048_576) throw IOException("Release response is too large")
                output.write(buffer, 0, count)
            }
            output.toString(Charsets.UTF_8.name())
        } else ""
        return UpdateResponse(code, body, connection.getHeaderField("X-RateLimit-Remaining"))
    } finally { connection.disconnect() }
}
