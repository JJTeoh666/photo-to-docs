package com.jjteo.phototodocs.documents

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A read-only, entirely bundled preview of the current editable note source. */
@Composable
fun DocumentPreview(source: String, modifier: Modifier = Modifier) {
    val payload = remember(source) { previewPayload(source) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PreviewWebView(context).apply {
                documentPayload = payload
                contentDescription = "Rendered document preview"
                loadUrl(ASSET_URL)
            }
        },
        update = { view -> view.documentPayload = payload; view.renderDocument() },
        onRelease = { view -> view.stopLoading(); view.destroy() },
    )
}

/**
 * Converts only valid equations; missing entries are exported as visible, exact TeX.
 * May be called from any coroutine dispatcher. It never starts a network request.
 */
suspend fun convertEquations(context: Context, source: String): Map<String, String> {
    val equations = NoteParser.equations(source)
    if (equations.isEmpty()) return emptyMap()
    return withContext(Dispatchers.Main.immediate) {
        withTimeout(20_000L) {
            suspendCancellableCoroutine { continuation ->
                val webView = WebView(context)
                var started = false
                var destroyed = false
                fun destroy() {
                    if (!destroyed) { destroyed = true; webView.stopLoading(); webView.destroy() }
                }
                configureOffline(webView)
                val loader = assetLoader(context)
                webView.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        loader.shouldInterceptRequest(request.url) ?: blockedResponse()

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = request.url.toString() != ASSET_URL

                    override fun onPageFinished(view: WebView, url: String) {
                        if (started || !continuation.isActive || url != ASSET_URL) return
                        started = true
                        val array = JSONArray(equations).toString()
                        view.evaluateJavascript("JSON.stringify(window.convertBatch($array))") { result ->
                            if (continuation.isActive) {
                                try {
                                    val decoded = JSONTokener(result).nextValue() as? String ?: error("Equation converter failed to load.")
                                    val converted = JSONObject(decoded)
                                    val map = buildMap {
                                        equations.forEach { key ->
                                            if (converted.has(key)) put(key, converted.getString(key))
                                        }
                                    }
                                    continuation.resume(map)
                                } catch (error: Exception) {
                                    continuation.resumeWithException(IllegalStateException("Could not convert equations. Their TeX source is still available.", error))
                                }
                            }
                            destroy()
                        }
                    }
                }
                continuation.invokeOnCancellation { Handler(Looper.getMainLooper()).post { destroy() } }
                webView.loadUrl(ASSET_URL)
            }
        }
    }
}

private class PreviewWebView(context: Context) : WebView(context) {
    var documentPayload = "[]"
    private var ready = false

    init {
        configureOffline(this)
        val loader = assetLoader(context)
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url) ?: blockedResponse()

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

            override fun onPageFinished(view: WebView, url: String) {
                if (url == ASSET_URL) { ready = true; renderDocument() }
            }
        }
    }

    fun renderDocument() {
        if (ready) evaluateJavascript("window.renderNote($documentPayload)", null)
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureOffline(view: WebView) {
    view.settings.apply {
        javaScriptEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        domStorageEnabled = false
        blockNetworkLoads = true
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
    }
    view.setBackgroundColor(android.graphics.Color.WHITE)
}

private fun assetLoader(context: Context) = WebViewAssetLoader.Builder()
    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
    .build()

private fun blockedResponse() = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), "".byteInputStream())

private fun previewPayload(source: String): String {
    fun tokens(text: String): JSONArray = JSONArray().apply {
        NoteParser.inlines(text).forEach { token ->
            put(JSONObject().put("text", token.text).put("math", token.math).put("bold", token.bold).put("italic", token.italic))
        }
    }
    return JSONArray().apply {
        NoteParser.blocks(source).forEach { block ->
            put(when (block) {
                is NoteBlock.Heading -> JSONObject().put("type", "heading").put("level", block.level).put("tokens", tokens(block.text))
                is NoteBlock.Paragraph -> JSONObject().put("type", "paragraph").put("tokens", tokens(block.text))
                is NoteBlock.ListItem -> JSONObject().put("type", "list").put("ordered", block.ordered).put("number", block.number).put("tokens", tokens(block.text))
                is NoteBlock.Equation -> JSONObject().put("type", "equation").put("latex", block.latex)
                is NoteBlock.Table -> JSONObject().put("type", "table").put("rows", JSONArray().apply {
                    block.rows.forEach { row -> put(JSONArray().apply { row.forEach { put(tokens(it)) } }) }
                })
            })
        }
    }.toString()
}

private const val ASSET_URL = "https://appassets.androidplatform.net/assets/preview.html"
