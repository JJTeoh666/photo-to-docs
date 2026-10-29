package com.jjteo.phototodocs.chatgpt

import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Bound before the browser opens. It accepts a single matching OAuth transaction, never LAN traffic. */
internal class LoopbackSignIn : Closeable {
    private val server = ServerSocket().apply {
        reuseAddress = false
        bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 4)
        soTimeout = 500
    }
    val redirect = "http://127.0.0.1:${server.localPort}/auth/callback"
    suspend fun await(state: String, client: String?): Pair<String, String> = withContext(Dispatchers.IO) {
        val deadline = System.nanoTime() + 300_000_000_000L
        while (System.nanoTime() < deadline) {
            currentCoroutineContext().ensureActive()
            val socket = try { server.accept() }
                catch (_: SocketTimeoutException) { continue }
                catch (failure: java.io.IOException) {
                    // Closing accept() can throw IOException after cancellation. Preserve cancellation semantics.
                    currentCoroutineContext().ensureActive()
                    throw failure
                }
            socket.use {
                it.soTimeout = 2000
                try {
                    val input = it.getInputStream()
                    val line = StringBuilder()
                    while (line.length <= 8192) {
                        val char = input.read()
                        if (char < 0 || char == 10) break
                        if (char != 13) line.append(char.toChar())
                    }
                    val parts = line.toString().split(' ')
                    require(line.length <= 8192 && parts.size == 3 && parts[0] == "GET" && parts[1].startsWith("/auth/callback?"))
                    // Drain only bounded HTTP headers; never accept a request body.
                    var boundary = 0
                    var bytes = 0
                    while (boundary < 4 && bytes++ < 8192) {
                        val char = input.read()
                        if (char < 0) break
                        boundary = when { (boundary == 0 || boundary == 2) && char == 13 -> boundary + 1
                            (boundary == 1 || boundary == 3) && char == 10 -> boundary + 1
                            char == 13 -> 1
                            else -> 0 }
                        // First request line CRLF was already read, so an empty header line also ends the headers.
                        if (bytes == 2 && boundary == 2) break
                    }
                    val result = ChatGptProtocol.callback(parts[1], state, client)
                    reply(it.getOutputStream(), 200, "Sign-in received. Return to PhotoToDocs to finish connecting.")
                    return@withContext result
                } catch (declined: ChatGptException) {
                    reply(it.getOutputStream(), 200, "Sign-in was declined. Return to PhotoToDocs.")
                    throw declined
                } catch (_: Exception) {
                    runCatching { reply(it.getOutputStream(), 400, "This sign-in callback was not accepted. Return to PhotoToDocs and try again.") }
                }
            }
        }
        throw java.io.IOException("Sign-in timed out. Return to Settings and start again.")
    }
    private fun reply(output: java.io.OutputStream, status: Int, message: String) {
        val body = "<!doctype html><html><head><meta name=viewport content=\"width=device-width\"><title>PhotoToDocs</title></head><body><h1>PhotoToDocs</h1><p>$message</p></body></html>".toByteArray(Charsets.UTF_8)
        output.write("HTTP/1.1 $status ${if (status == 200) "OK" else "Bad Request"}\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nContent-Security-Policy: default-src 'none'\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
        output.write(body)
        output.flush()
    }
    override fun close() { server.close() }
}
