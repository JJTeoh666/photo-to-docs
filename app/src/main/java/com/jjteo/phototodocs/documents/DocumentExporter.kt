package com.jjteo.phototodocs.documents

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Exports the same source shown in the editor. All exports run without a server. */
object DocumentExporter {
    fun latex(title: String, source: String): String = buildString {
        append("\\documentclass[12pt,a4paper]{article}\n")
        append("\\usepackage[utf8]{inputenc}\n\\usepackage[T1]{fontenc}\n")
        append("\\usepackage{amsmath,amssymb}\n\\usepackage[margin=25mm]{geometry}\n")
        append("\\title{").append(texEscape(title)).append("}\n\\date{}\n\\begin{document}\n\\maketitle\n\n")
        val blocks = NoteParser.blocks(source)
        var openList: Boolean? = null
        fun closeList() {
            openList?.let { append(if (it) "\\end{enumerate}\n\n" else "\\end{itemize}\n\n") }
            openList = null
        }
        blocks.forEach { block ->
            if (block !is NoteBlock.ListItem) closeList()
            when (block) {
                is NoteBlock.Heading -> {
                    val command = when (block.level) { 1 -> "section"; 2 -> "subsection"; 3 -> "subsubsection"; else -> "paragraph" }
                    append("\\$command*{").append(texInlines(block.text)).append("}\n\n")
                }
                is NoteBlock.Paragraph -> append(texInlines(block.text)).append("\n\n")
                is NoteBlock.Equation -> append("\\[\n").append(block.latex).append("\n\\]\n\n")
                is NoteBlock.ListItem -> {
                    if (openList != block.ordered) {
                        closeList()
                        openList = block.ordered
                        append(if (block.ordered) "\\begin{enumerate}\n" else "\\begin{itemize}\n")
                    }
                    append("\\item ").append(texInlines(block.text)).append('\n')
                }
                is NoteBlock.Table -> {
                    val columns = block.rows.maxOfOrNull { it.size } ?: 1
                    append("\\begin{center}\n\\begin{tabular}{").append("|l".repeat(columns)).append("|}\n\\hline\n")
                    block.rows.forEach { row ->
                        append((0 until columns).joinToString(" & ") { texInlines(row.getOrElse(it) { "" }) })
                        append(" \\\\\n\\hline\n")
                    }
                    append("\\end{tabular}\n\\end{center}\n\n")
                }
            }
        }
        closeList()
        append("\\end{document}\n")
    }

    /** Math map keys are the exact normalized source returned by [NoteParser.equations]. */
    fun docx(title: String, source: String, equationOmml: Map<String, String>): ByteArray {
        val math = equationOmml.mapValues { (_, value) -> validatedOmml(value) }
        val document = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<w:document xmlns:w=\"$WORD_NS\" xmlns:m=\"$MATH_NS\"><w:body>")
            append(paragraph(listOf(NoteInline(title)), math, "Title"))
            NoteParser.blocks(source).forEach { block ->
                when (block) {
                    is NoteBlock.Heading -> append(paragraph(NoteParser.inlines(block.text), math, "Heading${block.level.coerceAtMost(3)}"))
                    is NoteBlock.Paragraph -> append(paragraph(NoteParser.inlines(block.text), math))
                    is NoteBlock.ListItem -> {
                        val prefix = if (block.ordered) "${block.number}. " else "• "
                        append(paragraph(listOf(NoteInline(prefix)) + NoteParser.inlines(block.text), math))
                    }
                    is NoteBlock.Equation -> {
                        append("<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr>")
                        val omml = math[block.latex]
                        if (omml != null) append("<m:oMathPara>").append(omml).append("</m:oMathPara>")
                        else append(run("[Equation needs review: \\[${block.latex}\\]]", color = "B45309"))
                        append("</w:p>")
                    }
                    is NoteBlock.Table -> {
                        val columns = block.rows.maxOfOrNull { it.size } ?: 1
                        append("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders>")
                        listOf("top", "left", "bottom", "right", "insideH", "insideV").forEach { append("<w:$it w:val=\"single\" w:sz=\"4\" w:color=\"CBD5E1\"/>") }
                        append("</w:tblBorders></w:tblPr><w:tblGrid>")
                        repeat(columns) { append("<w:gridCol w:w=\"${9000 / columns}\"/>") }
                        append("</w:tblGrid>")
                        block.rows.forEachIndexed { rowIndex, row ->
                            append("<w:tr>")
                            repeat(columns) { column ->
                                append("<w:tc><w:tcPr><w:tcW w:w=\"${9000 / columns}\" w:type=\"dxa\"/></w:tcPr>")
                                val inlines = NoteParser.inlines(row.getOrElse(column) { "" }).map { if (rowIndex == 0) it.copy(bold = true) else it }
                                append(paragraph(inlines, math)).append("</w:tc>")
                            }
                            append("</w:tr>")
                        }
                        append("</w:tbl>")
                    }
                }
            }
            append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1417\" w:right=\"1417\" w:bottom=\"1417\" w:left=\"1417\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/></w:sectPr>")
            append("</w:body></w:document>")
        }
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            fun entry(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name).apply { time = 0 })
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>""")
            entry("_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            entry("word/document.xml", document)
            entry("word/_rels/document.xml.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            entry("word/styles.xml", styles)
        }
        return bytes.toByteArray()
    }

    private fun paragraph(inlines: List<NoteInline>, math: Map<String, String?>, style: String? = null): String = buildString {
        append("<w:p>")
        if (style != null) append("<w:pPr><w:pStyle w:val=\"$style\"/></w:pPr>")
        inlines.forEach { inline ->
            if (inline.math) {
                append(math[inline.text] ?: run("[Equation needs review: \\(${inline.text}\\)]", color = "B45309"))
            } else append(run(inline.text, inline.bold, inline.italic))
        }
        append("</w:p>")
    }

    private fun run(text: String, bold: Boolean = false, italic: Boolean = false, color: String? = null): String = buildString {
        append("<w:r>")
        if (bold || italic || color != null) {
            append("<w:rPr>")
            if (bold) append("<w:b/>")
            if (italic) append("<w:i/>")
            if (color != null) append("<w:color w:val=\"$color\"/>")
            append("</w:rPr>")
        }
        text.split('\n').forEachIndexed { index, line ->
            if (index > 0) append("<w:br/>")
            append("<w:t xml:space=\"preserve\">").append(xmlEscape(line)).append("</w:t>")
        }
        append("</w:r>")
    }

    private fun texInlines(source: String): String = NoteParser.inlines(source).joinToString("") { inline ->
        if (inline.math) "\\(${inline.text}\\)"
        else {
            var text = texEscape(inline.text).replace("\n", "\n")
            if (inline.italic) text = "\\emph{$text}"
            if (inline.bold) text = "\\textbf{$text}"
            text
        }
    }

    private fun texEscape(text: String): String = buildString {
        text.forEach { character ->
            append(when (character) {
                '\\' -> "\\textbackslash{}"; '{' -> "\\{"; '}' -> "\\}"; '$' -> "\\$"
                '&' -> "\\&"; '#' -> "\\#"; '%' -> "\\%"; '_' -> "\\_"
                '^' -> "\\textasciicircum{}"; '~' -> "\\textasciitilde{}"; else -> character.toString()
            })
        }
    }

    private fun xmlEscape(text: String): String = buildString {
        text.codePoints().forEach { code ->
            when (code) {
                38 -> append("&amp;"); 60 -> append("&lt;"); 62 -> append("&gt;"); 34 -> append("&quot;")
                else -> if (code == 9 || code == 10 || code == 13 || code in 32..0xD7FF || code in 0xE000..0xFFFD || code in 0x10000..0x10FFFF) appendCodePoint(code)
            }
        }
    }

    private fun validatedOmml(fragment: String): String? = runCatching {
        require(fragment.length <= 1_000_000 && !fragment.contains("<!", ignoreCase = true) && !fragment.contains("<?", ignoreCase = true))
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(fragment.toByteArray(Charsets.UTF_8)))
        require(document.documentElement.namespaceURI == MATH_NS && document.documentElement.localName == "oMath")
        val elements = document.getElementsByTagName("*")
        for (index in 0 until elements.length) {
            val element = elements.item(index)
            require(element.namespaceURI == MATH_NS || (element.namespaceURI == WORD_NS && element.localName in setOf("rPr", "rFonts", "b", "i", "bCs", "iCs", "sz", "szCs", "color", "lang")))
            val children = element.childNodes
            for (childIndex in 0 until children.length) {
                val child = children.item(childIndex)
                if ((child.nodeType == org.w3c.dom.Node.TEXT_NODE || child.nodeType == org.w3c.dom.Node.CDATA_SECTION_NODE) && !child.nodeValue.isNullOrBlank()) {
                    require(element.namespaceURI == MATH_NS && element.localName == "t")
                }
            }
            val attributes = element.attributes
            for (attributeIndex in 0 until attributes.length) {
                val attribute = attributes.item(attributeIndex)
                require(attribute.namespaceURI in setOf(null, "", MATH_NS, WORD_NS, "http://www.w3.org/2000/xmlns/", "http://www.w3.org/XML/1998/namespace"))
            }
        }
        fragment
    }.getOrNull()

    private const val WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val MATH_NS = "http://schemas.openxmlformats.org/officeDocument/2006/math"
    private val styles = """<?xml version="1.0" encoding="UTF-8"?><w:styles xmlns:w="$WORD_NS"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/><w:sz w:val="24"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="160"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style><w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:rPr><w:b/><w:sz w:val="40"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:pPr><w:keepNext/></w:pPr><w:rPr><w:b/><w:sz w:val="32"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:pPr><w:keepNext/></w:pPr><w:rPr><w:b/><w:sz w:val="28"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/><w:basedOn w:val="Normal"/><w:pPr><w:keepNext/></w:pPr><w:rPr><w:b/><w:sz w:val="26"/></w:rPr></w:style></w:styles>"""
}
