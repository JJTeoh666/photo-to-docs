package com.jjteo.phototodocs.ai

import java.io.IOException

/** Exact CPU/GPU multimodal artifact. Never substitute the web or per-SoC NPU builds. */
internal object ModelArtifact {
    const val FILE_NAME = "gemma-4-E2B-it.litertlm"
    const val REVISION = "1caa527c1e88b55f046fcd8d2b4265606680663d"
    const val SIZE = 2_588_147_712L
    const val SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
    const val URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/$REVISION/$FILE_NAME"

    /** Refuse mismatched resume responses before a single byte is appended. */
    fun checkResponse(code: Int, offset: Long, contentRange: String?, contentLength: Long): Boolean {
        if (code == 200) {
            if (contentLength >= 0 && contentLength != SIZE) {
                throw IOException("The server returned an unexpected model size.")
            }
            return false // The server ignored Range: safely restart, never append.
        }
        if (code != 206) throw IOException("Model download failed (HTTP $code). Try again to resume.")
        val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(contentRange.orEmpty())
            ?: throw IOException("The server did not provide a valid model download range.")
        val start = match.groupValues[1].toLongOrNull()
        val end = match.groupValues[2].toLongOrNull()
        val total = match.groupValues[3].toLongOrNull()
        if (start != offset || end != SIZE - 1 || total != SIZE ||
            (contentLength >= 0 && contentLength != SIZE - offset)) {
            throw IOException("The server returned a different model download range. Please retry.")
        }
        return offset > 0
    }
}

internal class IncompleteAiOutputException(message: String) : IOException(message)

internal object AiDocumentText {
    const val END_MARKER = "<<<END_DOCUMENT>>>"
    const val MAX_SOURCE_CHARS = 4_000
    const val MAX_INSTRUCTION_CHARS = 800

    fun checkRevisionInput(source: String, instruction: String) {
        require(source.isNotBlank()) { "Add some document text before asking AI to edit it." }
        require(instruction.isNotBlank()) { "Describe the change you want AI to make." }
        require(source.length <= MAX_SOURCE_CHARS) {
            "Select a smaller section for AI editing (up to $MAX_SOURCE_CHARS characters). Your document is unchanged."
        }
        require(instruction.length <= MAX_INSTRUCTION_CHARS) {
            "Use a shorter editing instruction (up to $MAX_INSTRUCTION_CHARS characters)."
        }
    }

    /** A completion marker prevents a token-limited fragment being accepted as a complete draft. */
    fun completed(raw: String): String {
        val text = raw.trim()
        if (!text.endsWith(END_MARKER)) throw IncompleteAiOutputException(
            "The AI draft did not finish. Try a smaller photo crop or text selection. Your document is unchanged."
        )
        var body = text.removeSuffix(END_MARKER).trim()
        if (body.startsWith("```")) {
            val firstLine = body.indexOf('\n')
            if (firstLine >= 0 && body.endsWith("```")) {
                body = body.substring(firstLine + 1).removeSuffix("```").trim()
            }
        }
        if (body.isBlank() || body.contains(END_MARKER)) {
            throw IncompleteAiOutputException("AI returned an empty or invalid draft. Your document is unchanged.")
        }
        return body
    }

    fun preview(raw: String): String = raw.substringBefore(END_MARKER)
}
