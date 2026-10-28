package com.jjteo.phototodocs.documents

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

/** Exercises the actual Android WebView, bundled scripts/fonts, and native export. */
@RunWith(AndroidJUnit4::class)
class EquationConversionTest {
    @Test fun writesRepresentativeExportsFromTheActualOfflineAppPipeline() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = """
            # Calculus study notes

            **Editable text**, *emphasis*, and an inline power ${'$'}x_1^2 + y^2 = 1${'$'}.
            Prose escaping: A&B, 20%, a_b, #1, {braces}, and cost \${'$'}10.

            ## Review checklist
            - Fractions and radicals
            - Integrals, sums, and matrices

            ${'$'}${'$'}
            \frac{1}{x+1} + \sqrt{x} + \sqrt[3]{y}
            ${'$'}${'$'}

            ${'$'}${'$'}
            \int_0^1 x^2\,dx = \frac{1}{3}, \qquad \sum_{i=1}^{n} i = \frac{n(n+1)}{2}
            ${'$'}${'$'}

            ${'$'}${'$'}
            \begin{pmatrix} 1 & 2 \\ 3 & 4 \end{pmatrix}
            ${'$'}${'$'}

            ${'$'}${'$'}
            \begin{aligned}
            x + y &= 3 \\
            x - y &= 1
            \end{aligned}
            ${'$'}${'$'}

            | Quantity | Formula |
            | --- | --- |
            | Absolute value | ${'$'}|x|${'$'} |
            | Area | ${'$'}A = \pi r^2${'$'} |
        """.trimIndent()
        val equations = convertEquations(context, source)
        assertTrue("Every valid fixture equation must become editable Word math", NoteParser.equations(source).all { equations.containsKey(it) })
        val word = DocumentExporter.docx("Photo to Docs verification", source, equations)
        val xml = documentXml(word)
        assertFalse("Every valid fixture equation must pass native XML validation", xml.contains("Equation needs review:"))
        val directory = File(requireNotNull(context.getExternalFilesDir(null)), "verification").apply { mkdirs() }
        File(directory, "sample.tex").writeText(DocumentExporter.latex("Photo to Docs verification", source))
        File(directory, "sample.docx").writeBytes(word)
        File(directory, "sample-source.md").writeText(source)
        assertTrue(File(directory, "sample.tex").length() > 100)
        assertTrue(File(directory, "sample.docx").length() > 100)
    }

    @Test fun bundledOfflineConverterProducesNativeWordEquations() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fraction = "\\frac{1}{x+1}"
        val source = "Inline ${'$'}x^2${'$'}.\n\n${'$'}${'$'}$fraction${'$'}${'$'}\n\nInvalid ${'$'}\\frac{1}${'$'}."
        val equations = convertEquations(context, source)
        assertTrue(equations["x^2"].orEmpty().contains("<m:sSup>"))
        assertTrue(equations[fraction].orEmpty().contains("<m:f>"))
        assertFalse(equations.containsKey("\\frac{1}"))
        val document = documentXml(DocumentExporter.docx("Offline math", source, equations))
        assertTrue(document.contains("<m:sSup>"))
        assertTrue(document.contains("<m:f>"))
        assertTrue(document.contains("Equation needs review:"))
    }

    private fun documentXml(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") return zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        error("No Word main document part was generated.")
    }
}
