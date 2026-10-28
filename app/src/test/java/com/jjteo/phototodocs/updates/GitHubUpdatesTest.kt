package com.jjteo.phototodocs.updates

import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GitHubUpdatesTest {
    private fun release(tag: String = "v1.0.2"): JSONObject = JSONObject()
        .put("tag_name", tag).put("html_url", "https://github.com/JJTeoh666/photo-to-docs/releases/tag/$tag")
        .put("draft", false).put("prerelease", false)
        .put("assets", JSONArray().put(JSONObject().put("name", "PhotoToDocs-debug.apk").put("state", "uploaded").put("size", 52_000_000)))

    @Test fun comparesNumericVersionsInsteadOfSortingText() {
        assertTrue(AppVersion.parse("v1.10.0")!! > AppVersion.parse("1.9.9")!!)
        assertTrue(AppVersion.parse("2.0.0")!! > AppVersion.parse("v1.99.99")!!)
        assertEquals(AppVersion.parse("1.0.1"), AppVersion.parse("v1.0.1+build.2"))
    }

    @Test fun rejectsUnknownOrPrereleaseVersionFormats() {
        listOf("latest", "1.2", "1.0.2-beta", "v01.2.3", "1.0.999999999999999999999999").forEach { assertNull(it, AppVersion.parse(it)) }
    }

    @Test fun identifiesANewerPublishedApk() {
        val (info, newer) = GitHubUpdates { UpdateResponse(200, release().toString()) }.check("1.0.1")
        assertTrue(newer)
        assertEquals("v1.0.2", info.tag)
        assertEquals("https://github.com/JJTeoh666/photo-to-docs/releases/tag/v1.0.2", info.url)
    }

    @Test fun equalAndOlderReleasesNeverOfferADowngrade() {
        val checker = GitHubUpdates { UpdateResponse(200, release("v1.0.1").toString()) }
        assertFalse(checker.check("1.0.1").second)
        assertFalse(checker.check("1.1.0").second)
    }

    @Test fun incompleteApkOrDraftReleaseCannotLookCurrent() {
        val malformed = listOf(
            release().put("draft", true), release().put("prerelease", true), release().put("assets", JSONArray()),
            release().put("assets", JSONArray().put(JSONObject().put("name", "PhotoToDocs-debug.apk").put("state", "starter").put("size", 1))),
        )
        malformed.forEach { expectFailure("incomplete") { GitHubUpdates { UpdateResponse(200, it.toString()) }.check("1.0.1") } }
    }

    @Test fun releaseLinksStayOnTheAppGithubRepository() {
        listOf("http://github.com/JJTeoh666/photo-to-docs/releases/tag/v2.0.0", "https://example.com/update.apk",
            "https://github.com/another/project/releases/tag/v2.0.0", "https://github.com@evil.example/updates").forEach { url ->
            expectFailure("incomplete") { GitHubUpdates.parseRelease(release().put("html_url", url).toString()) }
        }
    }

    @Test fun errorsAndRateLimitsNeverReportUpToDate() {
        listOf(401, 403, 404, 429, 500, 302).forEach { status ->
            expectFailure(if (status == 429) "limit" else if (status == 404) "No published" else "GitHub") {
                GitHubUpdates { UpdateResponse(status, "", if (status == 429) "0" else null) }.check("1.0.1")
            }
        }
    }

    @Test fun offlineAndInvalidJsonHaveActionableErrors() {
        expectFailure("connection") { GitHubUpdates { throw IOException("Socket details") }.check("1.0.1") }
        expectFailure("incomplete") { GitHubUpdates { UpdateResponse(200, "not json") }.check("1.0.1") }
    }

    private fun expectFailure(fragment: String, action: () -> Unit) {
        try { action(); fail("Expected the check to fail") }
        catch (exception: IOException) { assertTrue(exception.message, exception.message.orEmpty().contains(fragment, ignoreCase = true)) }
    }
}
