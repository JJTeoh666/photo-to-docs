package com.jjteo.phototodocs.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jjteo.phototodocs.AppViewModel
import com.jjteo.phototodocs.MainActivity
import com.jjteo.phototodocs.data.ThemeMode
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real print-spooler -> system Save as PDF -> DocumentsUI, without clearing app data. */
@RunWith(AndroidJUnit4::class)
class PdfExportTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private fun vm() = ViewModelProvider(compose.activity)[AppViewModel::class.java]
    private var noteId: String? = null
    private var originalTheme: ThemeMode? = null

    @After fun cleanup() {
        // Return from a system dialog even if a test assertion fails.
        shell("am start -W -n com.jjteo.phototodocs/.MainActivity -f 0x30020000")
        compose.runOnIdle {
            vm().cancelTask()
            noteId?.let(vm()::deleteDocument)
            originalTheme?.let(vm()::setThemeMode)
        }
    }

    @Test fun savesPaginatedEquationsAsPdfAndCanCancelWithoutChangingSource() {
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val filename = "PhotoToDocs-PDF-${UUID.randomUUID().toString().take(8)}.pdf"
        val source = """
            # Calculus review

            **Bold text**, *emphasis*, and inline math ${'$'}x_1^2 + y^2 = 1${'$'}.
            Text stays selectable in the PDF. A&B, 20%, and Unicode: café, 中文.

            - Check the units
            - Review the equations

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
            \begin{aligned} x+y &= 3 \\ x-y &= 1 \end{aligned}
            ${'$'}${'$'}

            | Quantity | Formula |
            | --- | --- |
            | Absolute value | ${'$'}|x|${'$'} |
            | Area | ${'$'}A=\pi r^2${'$'} |

            ## Long equation
            ${'$'}${'$'}
            a_1+a_2+a_3+a_4+a_5+a_6+a_7+a_8+a_9+a_{10}+a_{11}+a_{12}+a_{13}+a_{14}+a_{15}=100
            ${'$'}${'$'}

            Invalid equation remains visible: ${'$'}\frac{1}${'$'}.
        """.trimIndent() + (1..28).joinToString("", prefix = "\n\n") {
            "## Review section $it\n\nThis paragraph checks pagination and readable spacing. The saved note remains editable. Section $it ends here.\n\n"
        } + "END OF PDF VERIFICATION"
        compose.waitUntil(15_000) { !vm().state.value.loading }
        compose.runOnIdle {
            originalTheme = vm().state.value.themeMode
            vm().setThemeMode(ThemeMode.DARK)
            vm().createDocument()
            noteId = vm().state.value.current!!.id
            vm().renameDocument("PDF export verification")
            vm().updateSource(source)
        }
        compose.onNodeWithTag("export-document").performClick()
        compose.onNodeWithText("Save PDF (.pdf)").performClick()
        choosePdfDestination()
        waitNative { it.viewIdResourceName?.endsWith(":id/print_button") == true }
        shell("input keyevent 4")
        compose.waitUntil(15_000) { !vm().state.value.busy }
        compose.onNodeWithTag("document-source").assertTextEquals(source)
        compose.onNodeWithText("Task cancelled. Your saved note is unchanged.").assertDoesNotExist()

        compose.onNodeWithTag("export-document").performClick()
        compose.onNodeWithText("Save PDF (.pdf)").performClick()
        choosePdfDestination()
        clickNative { it.viewIdResourceName?.endsWith(":id/print_button") == true && it.isEnabled }
        val nameField = waitNative { it.className?.toString() == "android.widget.EditText" }
        assertTrue(nameField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, filename)
        }))
        clickNative { it.text?.toString().equals("Save", ignoreCase = true) && it.isEnabled }
        compose.waitUntil(20_000) { !vm().state.value.busy }
        val bytes = shellBytes("cat /sdcard/Download/$filename")
        assertTrue("System saved a PDF", bytes.take(5).toByteArray().toString(Charsets.US_ASCII) == "%PDF-")
        val directory = File(requireNotNull(compose.activity.getExternalFilesDir(null)), "verification").apply { mkdirs() }
        val file = File(directory, "sample.pdf").apply { writeBytes(bytes) }
        File(directory, "sample-pdf-source.md").writeText(source)
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            assertTrue("Long notes paginate", pdf.pageCount >= 3)
            repeat(pdf.pageCount) { index ->
                pdf.openPage(index).use { page ->
                    val bitmap = Bitmap.createBitmap(300, 424, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    assertEquals("Dark theme must not create dark PDF pages", Color.WHITE, bitmap.getPixel(5, 5))
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    assertTrue("Page ${index + 1} is not blank", pixels.count { Color.red(it) < 200 } > 150)
                    bitmap.recycle()
                }
            }
        }
        assertEquals(source, vm().state.value.current!!.source)
        shell("rm /sdcard/Download/$filename")
    }

    private fun shell(command: String) = shellBytes(command).toString(Charsets.UTF_8)
    private fun shellBytes(command: String): ByteArray = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).use { it.readBytes() }

    private fun choosePdfDestination() {
        clickNative { it.viewIdResourceName?.endsWith(":id/destination_spinner") == true }
        clickNative { it.text?.toString().equals("Save as PDF", ignoreCase = true) }
    }

    private fun waitNative(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 25_000
        while (SystemClock.uptimeMillis() < deadline) {
            fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null) return null
                if (predicate(node)) return node
                repeat(node.childCount) { index -> find(node.getChild(index))?.let { return it } }
                return null
            }
            find(automation.rootInActiveWindow)?.let { return it }
            SystemClock.sleep(150)
        }
        fun describe(node: AccessibilityNodeInfo?): String = if (node == null) "" else buildString {
            appendLine("${node.viewIdResourceName}: ${node.text} (${node.contentDescription}) enabled=${node.isEnabled}")
            repeat(node.childCount) { append(describe(node.getChild(it))) }
        }
        error("Expected Android PDF save control did not appear.\n${describe(automation.rootInActiveWindow)}")
    }

    private fun clickNative(predicate: (AccessibilityNodeInfo) -> Boolean) {
        var node: AccessibilityNodeInfo? = waitNative(predicate)
        while (node != null && !node.isClickable) node = node.parent
        assertTrue("Control is clickable", node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
    }
}
