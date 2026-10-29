package com.jjteo.phototodocs.documents

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintJob
import android.print.PrintManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Uses Android's supported print flow: select Save as PDF, then choose a destination. */
suspend fun printPdf(activity: Activity, title: String, source: String) = withContext(Dispatchers.Main.immediate) {
    check(!activity.isFinishing && !activity.isDestroyed) { "Reopen the note and try exporting again." }
    val manager = activity.getSystemService(PrintManager::class.java)
        ?: error("PDF saving is unavailable because this phone's Android print service is disabled.")
    val documentTitle = title.ifBlank { "Untitled note" }
    val view = preparePdfView(activity, documentTitle, source)
    try {
        suspendCancellableCoroutine<Unit> { continuation ->
            var job: PrintJob? = null
            val delegate = view.createPrintDocumentAdapter(documentTitle)
            val adapter = object : PrintDocumentAdapter() {
                override fun onStart() = delegate.onStart()

                override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes,
                    cancellationSignal: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
                    // Match the selected paper and margins before Chromium paginates the content.
                    // Save as PDF can replace the initial attributes with zero margins.
                    val pageAttributes = pdfPageAttributes(newAttributes)
                    val margins = pageAttributes.minMargins
                    val widthMils = (pageAttributes.mediaSize?.widthMils ?: 8270) -
                        (margins?.leftMils ?: 0) - (margins?.rightMils ?: 0)
                    val widthCss = (widthMils * 96.0 / 1000).coerceAtLeast(96.0)
                    view.evaluateJavascript("""
                        (() => {
                            document.body.style.width = '${widthCss}px';
                            for (const equation of document.querySelectorAll('.equation')) {
                                equation.style.fontSize = '';
                                const math = equation.querySelector('.katex-html');
                                if (math && math.getBoundingClientRect().width > equation.clientWidth) {
                                    equation.style.fontSize = Math.max(0.1, (equation.clientWidth - 4) / math.getBoundingClientRect().width) + 'em';
                                }
                            }
                        })()
                    """.trimIndent()) {
                        if (cancellationSignal.isCanceled) callback.onLayoutCancelled()
                        else if (!view.released) delegate.onLayout(oldAttributes?.let(::pdfPageAttributes), pageAttributes, cancellationSignal, callback, extras)
                        else callback.onLayoutCancelled()
                    }
                }

                override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor,
                    cancellationSignal: CancellationSignal, callback: WriteResultCallback) =
                    delegate.onWrite(pages, destination, cancellationSignal, callback)

                override fun onFinish() {
                    try { delegate.onFinish() }
                    finally {
                        view.release()
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }
            continuation.invokeOnCancellation {
                Handler(Looper.getMainLooper()).post { job?.cancel(); view.release() }
            }
            try {
                val filename = documentTitle.replace(Regex("[\\p{Cc}\\\\/:*?\"<>|]"), "_").take(80)
                job = manager.print("$filename.pdf", adapter, PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                    .setMinMargins(PrintAttributes.Margins(709, 709, 709, 709)) // 18 mm
                    .build())
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    } finally { view.release() }
}

private fun pdfPageAttributes(attributes: PrintAttributes): PrintAttributes {
    val margins = attributes.minMargins
    return PrintAttributes.Builder().apply {
        attributes.mediaSize?.let { setMediaSize(it) }
        // PDF uses points (72/in). A consistent resolution also avoids WebView
        // implementations scaling the margins with the printer's device DPI.
        setResolution(PrintAttributes.Resolution("pdf", "PDF", 72, 72))
        if (attributes.colorMode != 0) setColorMode(attributes.colorMode)
        if (attributes.duplexMode != 0) setDuplexMode(attributes.duplexMode)
    }.setMinMargins(PrintAttributes.Margins(
        maxOf(709, margins?.leftMils ?: 0), maxOf(709, margins?.topMils ?: 0),
        maxOf(709, margins?.rightMils ?: 0), maxOf(709, margins?.bottomMils ?: 0),
    )).build()
}

private class PdfWebView(context: Context) : WebView(context) {
    var released = false
        private set
    fun release() {
        if (!released) { released = true; stopLoading(); destroy() }
    }
}

/** Waits for the actual bundled renderer and equation fonts; no network or account required. */
private suspend fun preparePdfView(activity: Activity, title: String, source: String): PdfWebView = try {
    withTimeout(30_000L) { loadPdfView(activity, title, source) }
} catch (_: TimeoutCancellationException) {
    throw IllegalStateException("PDF rendering took too long. Reopen the note and try exporting again.")
}

private suspend fun loadPdfView(activity: Activity, title: String, source: String): PdfWebView =
    suspendCancellableCoroutine { continuation ->
        val view = PdfWebView(activity)
        val handler = Handler(Looper.getMainLooper())
        var started = false
        fun fail(error: Exception) {
            if (continuation.isActive) continuation.resumeWithException(error)
            view.release()
        }
        fun waitForFonts() {
            if (!continuation.isActive || view.released) return
            view.evaluateJavascript("window.pdfReady === true") { ready ->
                if (!continuation.isActive) return@evaluateJavascript
                if (ready == "true") continuation.resume(view)
                else handler.postDelayed({ waitForFonts() }, 50)
            }
        }
        configureOffline(view)
        view.settings.textZoom = 100
        val loader = assetLoader(activity)
        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url) ?: blockedResponse()
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) fail(IllegalStateException("Could not load the offline PDF renderer. Reopen the note and try again."))
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (started || !continuation.isActive || url != ASSET_URL) return
                started = true
                view.evaluateJavascript("""
                    (() => {
                        if (!window.documentReady) return false;
                        document.body.classList.add('pdf-export');
                        document.body.style.width = '658px';
                        window.renderNote(${previewPayload(source)});
                        const root = document.getElementById('document');
                        if (${source.isBlank()}) root.replaceChildren();
                        const heading = document.createElement('h1');
                        heading.className = 'pdf-title';
                        heading.textContent = ${JSONObject.quote(title)};
                        root.prepend(heading);
                        for (const table of root.querySelectorAll('table')) {
                            const rows = Array.from(table.rows);
                            const head = document.createElement('thead');
                            const body = document.createElement('tbody');
                            if (rows.length) head.append(rows.shift());
                            rows.forEach(row => body.append(row));
                            table.replaceChildren(head, body);
                        }
                        void root.offsetHeight;
                        document.fonts.ready.then(() => { window.pdfReady = true; });
                        return true;
                    })()
                """.trimIndent()) { result ->
                    if (result == "true") waitForFonts()
                    else fail(IllegalStateException("The offline PDF renderer could not start. Your saved note is unchanged."))
                }
            }
        }
        continuation.invokeOnCancellation { handler.post { view.release() } }
        view.loadUrl(ASSET_URL)
    }
