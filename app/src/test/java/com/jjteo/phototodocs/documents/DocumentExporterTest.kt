package com.jjteo.phototodocs.documents

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class DocumentExporterTest {
    @Test fun extractsMathFromAllSupportedBlocksWithoutEscapedDollars() {
        val source = """
            # Title \(x^2\)
            Price \${'$'}10 and ${'$'}y_1${'$'}; **bold** and *italic*.

            ${'$'}${'$'}
            \frac{1}{x}
            ${'$'}${'$'}

            - Value ${'$'}z${'$'}
            | Name | Formula |
            | --- | --- |
            | A | ${'$'}a+b${'$'} |
        """.trimIndent()
        assertEquals(listOf("x^2", "y_1", "\\frac{1}{x}", "z", "a+b"), NoteParser.equations(source))
    }

    @Test fun latexEscapesProseAndKeepsMathAndFormatting() {
        val source = "# Notes\nA&B costs 20%: **bold** and *italic*, ${'$'}x_1^2${'$'}.\n\n- first\n- second"
        val latex = DocumentExporter.latex("R&D_1", source)
        assertTrue(latex.contains("\\title{R\\&D\\_1}"))
        assertTrue(latex.contains("A\\&B costs 20\\%"))
        assertTrue(latex.contains("\\textbf{bold}"))
        assertTrue(latex.contains("\\emph{italic}"))
        assertTrue(latex.contains("\\(x_1^2\\)"))
        assertTrue(latex.contains("\\begin{itemize}"))
        assertTrue(latex.endsWith("\\end{document}\n"))
    }

    @Test fun realDocxHasValidXmlAndNativeEditableMath() {
        val formula = "\\frac{1}{x}"
        val math = """<m:oMath xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math"><m:f><m:num><m:r><m:t>1</m:t></m:r></m:num><m:den><m:r><m:t>x</m:t></m:r></m:den></m:f></m:oMath>"""
        val files = unzip(DocumentExporter.docx("A&B", "Text <tag> ${'$'}$formula${'$'}\n\n${'$'}${'$'}x^2${'$'}${'$'}", mapOf(formula to math)))
        assertTrue(files.keys.containsAll(listOf("[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/styles.xml", "word/_rels/document.xml.rels")))
        files.values.forEach { xml -> DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray())) }
        val document = files.getValue("word/document.xml")
        assertTrue(document.contains("<m:f>"))
        assertTrue(document.contains("A&amp;B"))
        assertTrue(document.contains("&lt;tag&gt;"))
        assertTrue(document.contains("Equation needs review: \\[x^2\\]"))
        assertFalse(document.contains("<w:drawing"))
    }

    @Test fun malformedOrUnsafeMathFallsBackToExactEditableSource() {
        val formulas = listOf("x", "y", "z")
        val bad = mapOf("x" to "<broken>", "y" to "<!DOCTYPE x SYSTEM \"https://example.com\"><x/>", "z" to "<w:altChunk xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"/>")
        val source = formulas.joinToString(" ") { "${'$'}$it${'$'}" }
        val document = unzip(DocumentExporter.docx("Safe", source, bad)).getValue("word/document.xml")
        formulas.forEach { assertTrue(document.contains("Equation needs review: \\($it\\)")) }
        assertFalse(document.contains("DOCTYPE"))
        assertFalse(document.contains("altChunk"))
    }

    @Test fun tablesBecomeEditableCellsAndLatexTabular() {
        val source = "| A | B |\n| --- | --- |\n| text | ${'$'}x${'$'} |"
        assertTrue(DocumentExporter.latex("Table", source).contains("\\begin{tabular}{|l|l|}"))
        val document = unzip(DocumentExporter.docx("Table", source, emptyMap())).getValue("word/document.xml")
        assertTrue(document.contains("<w:tbl>"))
        assertEquals(4, "<w:tc>".toRegex().findAll(document).count())
    }

    @Test fun incompleteMathAndMarkdownRemainVisible() {
        val source = "Unclosed ${'$'}x + 1\n\n${'$'}${'$'}\n\\frac{1}{2}\n\n**unclosed"
        val latex = DocumentExporter.latex("Draft", source)
        assertTrue(latex.contains("Unclosed \\${'$'}x + 1"))
        assertTrue(latex.contains("**unclosed"))
        assertEquals(emptyList<String>(), NoteParser.equations(source))
    }

    @Test fun tablePipesInsideMathAndEscapedProseStayInTheirCells() {
        val source = "| Formula | Meaning |\n| --- | --- |\n| ${'$'}|x|${'$'} | left \\| right |\n| \\(a|b\\) | conditional |"
        val table = NoteParser.blocks(source).single() as NoteBlock.Table
        assertEquals(listOf(2, 2, 2), table.rows.map { it.size })
        assertEquals(listOf("|x|", "a|b"), NoteParser.equations(source))
        assertTrue(DocumentExporter.latex("Pipes", source).contains("left | right"))
        assertTrue(DocumentExporter.latex("Pipes", source).contains("\\(|x|\\)"))
    }

    @Test fun escapedMathClosersAndMultilineEquationsPreserveExactContents() {
        val slash = 92.toChar()
        val aligned = listOf("${slash}begin{aligned}", "x &= 1 $slash$slash", "y &= 2", "${slash}end{aligned}").joinToString("\n")
        val source = "Inline ${'$'}x $slash$slash${'$'} and ${'$'}y${'$'}.\n\n${slash}[\n$aligned\n${slash}]"
        val equations = NoteParser.equations(source)
        assertEquals("x $slash$slash", equations.first())
        assertEquals("y", equations[1])
        assertEquals(aligned, equations.last())
        assertTrue(DocumentExporter.latex("Multiline", source).contains(aligned))
    }

    @Test fun inlineDisplayPairsRemainLiteralAndUnmatchedStrongMarkersDoNotDisappear() {
        val tokens = NoteParser.inlines("literal ${'$'}${'$'}broken and **unclosed")
        assertFalse(tokens.any { it.math || it.bold || it.italic })
        assertEquals("literal ${'$'}${'$'}broken and **unclosed", tokens.joinToString("") { it.text })
    }

    @Test fun bareMathTextThatWordWouldDropFallsBackToExactSource() {
        val source = "${'$'}i=2${'$'}"
        val broken = """<m:oMath xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math"><m:r><m:t>i</m:t></m:r>=<m:r><m:t>2</m:t></m:r></m:oMath>"""
        val document = unzip(DocumentExporter.docx("Relation", source, mapOf("i=2" to broken))).getValue("word/document.xml")
        assertTrue(document.contains("Equation needs review: \\(i=2\\)"))
        assertFalse(document.contains("</m:r>="))
    }

    private fun unzip(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes().toString(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }
}
