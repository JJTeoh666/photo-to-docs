package com.jjteo.phototodocs.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jjteo.phototodocs.AppViewModel
import com.jjteo.phototodocs.MainActivity
import com.jjteo.phototodocs.data.DocumentStore
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises actual Android image processing and bundled ML Kit recognition, without model downloads. */
@RunWith(AndroidJUnit4::class)
class PhotoPipelineTest {
    @Test
    fun importedPhotoCanBeCroppedRotatedAndTranscribedOffline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.cacheDir, "photo-test-${UUID.randomUUID()}.png")
        createPrintedPage(fixture)
        var vm: AppViewModel? = null
        var createdId: String? = null
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { vm = ViewModelProvider(it)[AppViewModel::class.java] }
            val model = requireNotNull(vm)
            waitFor("library to load") { !model.state.value.loading }
            scenario.onActivity {
                model.createDocument()
                model.renameDocument("Photo pipeline ${UUID.randomUUID()}")
                createdId = model.state.value.current!!.id
                model.importPhoto(Uri.fromFile(fixture))
            }
            waitFor("photo import") { !model.state.value.busy }
            assertNull("Import error: ${model.state.value.error}", model.state.value.error)
            val imported = requireNotNull(model.state.value.current).pages.single()
            assertTrue("Original photo must be stored", File(imported.originalPath).isFile)
            assertTrue("Working photo must be stored", File(imported.path).isFile)
            assertNotEquals(imported.originalPath, imported.path)
            assertEquals(1200 to 900, dimensions(imported.path))
            val originalDigest = digest(File(imported.originalPath))

            scenario.onActivity { model.cropPhoto(imported.id, .1f, .1f, .9f, .9f) }
            waitFor("crop") { !model.state.value.busy }
            assertNull("Crop error: ${model.state.value.error}", model.state.value.error)
            val cropped = model.state.value.current!!.pages.single()
            assertNotEquals(imported.path, cropped.path)
            assertEquals(960 to 720, dimensions(cropped.path))
            assertEquals(1200 to 900, dimensions(cropped.originalPath))
            assertArrayEquals("Cropping must preserve original bytes", originalDigest, digest(File(cropped.originalPath)))

            scenario.onActivity { model.rotatePhoto(imported.id) }
            waitFor("rotation") { !model.state.value.busy }
            assertNull("Rotation error: ${model.state.value.error}", model.state.value.error)
            val rotated = model.state.value.current!!.pages.single()
            assertNotEquals(cropped.path, rotated.path)
            assertEquals(720 to 960, dimensions(rotated.path))
            assertArrayEquals("Rotation must preserve original bytes", originalDigest, digest(File(rotated.originalPath)))

            // Return to upright before OCR. The test does not assume sideways-text recognition.
            repeat(3) {
                scenario.onActivity { model.rotatePhoto(imported.id) }
                waitFor("restoring upright orientation") { !model.state.value.busy }
                assertNull(model.state.value.error)
            }
            assertEquals(960 to 720, dimensions(model.state.value.current!!.pages.single().path))
            scenario.onActivity { model.transcribePage(imported.id, basicOnly = true) }
            waitFor("bundled ML Kit transcription", 60_000) {
                !model.state.value.busy && (model.state.value.proposal != null || model.state.value.error != null)
            }
            assertNull("OCR error: ${model.state.value.error}", model.state.value.error)
            val proposal = model.state.value.proposal
            assertNotNull("OCR must create a reviewable draft", proposal)
            val extracted = proposal!!.newText.replace(Regex("\\s+"), " ").uppercase()
            assertTrue("Actual OCR output was: ${proposal.newText}", extracted.contains("PHYSICS STUDY NOTES"))
            assertEquals("Recognition must not modify the note before acceptance", "", model.state.value.current!!.source)
            scenario.onActivity { model.acceptProposal() }
            assertNull(model.state.value.proposal)
            val accepted = model.state.value.current!!.source
            assertTrue(accepted.contains("PHYSICS", ignoreCase = true))
            assertFalse(model.state.value.busy)
            waitFor("accepted transcription to reach disk") {
                DocumentStore(File(context.filesDir, "documents")).load()
                    .any { it.id == createdId && it.source == accepted && it.pages.size == 1 }
            }
        } finally {
            try {
                val model = vm
                if (model != null && createdId != null) {
                    scenario.onActivity { model.cancelTask(); model.rejectProposal(); model.dismissError() }
                    waitFor("test task cleanup") { !model.state.value.busy }
                    scenario.onActivity { model.deleteDocument(createdId!!) }
                    waitFor("test note cleanup") { model.state.value.documents.none { it.id == createdId } }
                }
            } finally {
                fixture.delete()
                scenario.close()
            }
        }
    }

    private fun createPrintedPage(file: File) {
        val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textSize = 64f
            }
            canvas.drawText("PHYSICS STUDY NOTES", 155f, 230f, paint)
            paint.textSize = 39f
            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            canvas.drawText("Energy equals mass times light squared.", 155f, 350f, paint)
            canvas.drawText("Remember to check the units.", 155f, 440f, paint)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }

    private fun dimensions(path: String): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        return options.outWidth to options.outHeight
    }

    private fun digest(file: File): ByteArray = MessageDigest.getInstance("SHA-256").digest(file.readBytes())

    private fun waitFor(label: String, timeout: Long = 20_000, ready: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) {
            if (ready()) return
            SystemClock.sleep(50)
        }
        assertTrue("Timed out waiting for $label", ready())
    }
}
