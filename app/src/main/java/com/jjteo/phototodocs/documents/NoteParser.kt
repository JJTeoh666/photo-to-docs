package com.jjteo.phototodocs.documents

/** A deliberately small, shared Markdown + TeX parser for preview and both exports. */
internal sealed interface NoteBlock {
    data class Heading(val level: Int, val text: String) : NoteBlock
    data class Paragraph(val text: String) : NoteBlock
    data class ListItem(val ordered: Boolean, val number: Int, val text: String) : NoteBlock
    data class Equation(val latex: String) : NoteBlock
    data class Table(val rows: List<List<String>>) : NoteBlock
}

internal data class NoteInline(
    val text: String,
    val math: Boolean = false,
    val bold: Boolean = false,
    val italic: Boolean = false,
)

internal object NoteParser {
    private val heading = Regex("^(#{1,6})\\s+(.+)$")
    private val list = Regex("^\\s*(?:([-+*])|([0-9]+)[.)])\\s+(.+)$")
    private val tableRule = Regex("^:?-{3,}:?$")

    fun blocks(source: String): List<NoteBlock> {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = mutableListOf<NoteBlock>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index].trim()
            if (line.isEmpty()) { index++; continue }
            val opener = when {
                line.startsWith("$$") -> "$$"
                line.startsWith("\\[") -> "\\["
                else -> null
            }
            if (opener != null) {
                val closer = if (opener == "$$") "$$" else "\\]"
                val rest = line.substring(opener.length)
                val sameLine = rest.indexOf(closer)
                if (sameLine >= 0 && rest.substring(sameLine + closer.length).isBlank()) {
                    out += NoteBlock.Equation(rest.substring(0, sameLine).trim())
                    index++
                    continue
                }
                var end = index + 1
                while (end < lines.size && !lines[end].trimEnd().endsWith(closer)) end++
                if (end < lines.size) {
                    val mathLines = mutableListOf(rest)
                    mathLines += lines.subList(index + 1, end)
                    mathLines += lines[end].trimEnd().removeSuffix(closer)
                    out += NoteBlock.Equation(mathLines.joinToString("\n").trim())
                    index = end + 1
                    continue
                }
                // Unclosed math stays ordinary, editable source text.
            }
            val h = heading.matchEntire(line)
            if (h != null) {
                out += NoteBlock.Heading(h.groupValues[1].length, h.groupValues[2])
                index++
                continue
            }
            val item = list.matchEntire(line)
            if (item != null) {
                out += NoteBlock.ListItem(item.groupValues[2].isNotEmpty(), item.groupValues[2].toIntOrNull() ?: 1, item.groupValues[3])
                index++
                continue
            }
            if ('|' in line && index + 1 < lines.size) {
                val cells = tableCells(lines[index + 1])
                if (cells.isNotEmpty() && cells.all { tableRule.matches(it) }) {
                    val rows = mutableListOf(tableCells(line))
                    index += 2
                    while (index < lines.size && '|' in lines[index] && lines[index].isNotBlank()) {
                        rows += tableCells(lines[index++])
                    }
                    out += NoteBlock.Table(rows)
                    continue
                }
            }
            val paragraph = mutableListOf(lines[index++].trim())
            while (index < lines.size && lines[index].isNotBlank()) {
                val next = lines[index].trim()
                if (heading.matches(next) || list.matches(next) || next.startsWith("$$") || next.startsWith("\\[")) break
                if ('|' in next && index + 1 < lines.size && tableCells(lines[index + 1]).all { tableRule.matches(it) }) break
                paragraph += lines[index++].trim()
            }
            out += NoteBlock.Paragraph(paragraph.joinToString("\n"))
        }
        return out
    }

    fun inlines(source: String): List<NoteInline> {
        val out = mutableListOf<NoteInline>()
        val plain = StringBuilder()
        var bold = false
        var italic = false
        var index = 0
        fun flush() {
            if (plain.isNotEmpty()) {
                out += NoteInline(plain.toString(), bold = bold, italic = italic)
                plain.clear()
            }
        }
        while (index < source.length) {
            if (source.startsWith("$$", index)) {
                // Display delimiters belong to block parsing; never consume half a pair.
                plain.append("$$"); index += 2; continue
            }
            val inlineOpener = when {
                source.startsWith("\\(", index) -> "\\("
                source[index] == '$' && !source.startsWith("$$", index) -> "$"
                else -> null
            }
            if (inlineOpener != null) {
                val closer = if (inlineOpener == "$" ) "$" else "\\)"
                var end = source.indexOf(closer, index + inlineOpener.length)
                while (end >= 0 && (isEscaped(source, end) || (closer == "$" && (source.startsWith("$$", end) || (end > 0 && source[end - 1] == '$'))))) {
                    end = source.indexOf(closer, end + closer.length)
                }
                if (end > index + inlineOpener.length) {
                    flush()
                    out += NoteInline(source.substring(index + inlineOpener.length, end).trim(), math = true, bold = bold, italic = italic)
                    index = end + closer.length
                    continue
                }
            }
            if (source[index] == '\\' && index + 1 < source.length && source[index + 1] in "*_$#[]{}\\|") {
                plain.append(source[index + 1]); index += 2; continue
            }
            val strongMarker = if (source.startsWith("**", index)) "**" else if (source.startsWith("__", index)) "__" else null
            if (strongMarker != null && (bold || source.indexOf(strongMarker, index + 2) >= 0)) {
                flush(); bold = !bold; index += 2; continue
            }
            if (strongMarker != null) {
                // An unmatched strong delimiter is literal text, not two italic delimiters.
                plain.append(strongMarker); index += 2; continue
            }
            if (source[index] == '*' && (italic || source.indexOf('*', index + 1) >= 0)) {
                flush(); italic = !italic; index++; continue
            }
            plain.append(source[index++])
        }
        flush()
        return out
    }

    fun equations(source: String): List<String> = buildList {
        fun addInline(text: String) { inlines(text).filter { it.math }.forEach { add(it.text) } }
        blocks(source).forEach { block ->
            when (block) {
                is NoteBlock.Equation -> add(block.latex)
                is NoteBlock.Heading -> addInline(block.text)
                is NoteBlock.Paragraph -> addInline(block.text)
                is NoteBlock.ListItem -> addInline(block.text)
                is NoteBlock.Table -> block.rows.flatten().forEach(::addInline)
            }
        }
    }.distinct()

    private fun tableCells(line: String): List<String> {
        val text = line.trim()
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var mathCloser: String? = null
        var index = 0
        while (index < text.length) {
            val closer = mathCloser
            if (closer != null && text.startsWith(closer, index) && !isEscaped(text, index)) {
                cell.append(closer); index += closer.length; mathCloser = null; continue
            }
            if (closer == null && !isEscaped(text, index)) {
                val opener = when {
                    text.startsWith("\\(", index) -> "\\("
                    text.startsWith("$$", index) -> "$$"
                    text[index] == '$' -> "$"
                    else -> null
                }
                if (opener != null) {
                    mathCloser = if (opener == "\\(") "\\)" else opener
                    cell.append(opener); index += opener.length; continue
                }
                if (text[index] == '|') {
                    cells += cell.toString().trim(); cell.clear(); index++; continue
                }
            }
            cell.append(text[index++])
        }
        cells += cell.toString().trim()
        if (text.startsWith('|') && cells.firstOrNull().isNullOrEmpty()) cells.removeAt(0)
        if (text.endsWith('|') && !isEscaped(text, text.lastIndex) && cells.lastOrNull().isNullOrEmpty()) cells.removeAt(cells.lastIndex)
        return cells
    }

    private fun isEscaped(text: String, index: Int): Boolean {
        var slashes = 0
        var previous = index - 1
        while (previous >= 0 && text[previous--] == '\\') slashes++
        return slashes % 2 == 1
    }
}
