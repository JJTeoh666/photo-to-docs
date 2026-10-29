package com.jjteo.phototodocs.ai

import java.io.File

enum class AiProvider(val label: String) { LOCAL("Local AI"), CHATGPT("ChatGPT") }

interface DocumentAi {
    suspend fun transcribe(image: File, ocrText: String, onPartial: (String) -> Unit): String
    suspend fun revise(source: String, instruction: String, onPartial: (String) -> Unit): String
}
