package com.jjteo.phototodocs.ui

import androidx.compose.ui.test.performScrollTo

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jjteo.phototodocs.AppViewModel
import com.jjteo.phototodocs.BuildConfig
import com.jjteo.phototodocs.MainActivity
import com.jjteo.phototodocs.data.DocumentStore
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs without a downloaded model or external camera/file-picker interaction.
 * Creates uniquely named notes and cleans up only IDs created by this test.
 * Never clears app data or touches a user's other notes or downloaded model.
 */
@RunWith(AndroidJUnit4::class)
class EditorSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val createdIds = linkedSetOf<String>()

    private fun viewModel(): AppViewModel = ViewModelProvider(compose.activity)[AppViewModel::class.java]

    private fun createNote(title: String = "Physics notes ${UUID.randomUUID().toString().take(6)}"): String {
        compose.waitUntil(15_000) { !viewModel().state.value.loading }
        compose.runOnIdle { viewModel().closeDocument() }
        compose.onNodeWithTag("document-library").performScrollToNode(hasTestTag("new-document"))
        compose.onNodeWithTag("new-document").performClick()
        compose.runOnIdle { createdIds += viewModel().state.value.current!!.id }
        if (compose.onAllNodesWithTag("document-title").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithContentDescription("Rename document").performClick()
        }
        compose.onNodeWithTag("document-title").performTextReplacement(title)
        if (compose.onAllNodesWithText("Done").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Done").performClick()
        }
        hideKeyboard()
        return title
    }

    @After
    fun removeOnlyTestNotes() {
        if (createdIds.isEmpty()) return
        compose.runOnIdle {
            val vm = viewModel()
            if (vm.state.value.busy) vm.cancelTask()
            createdIds.forEach(vm::deleteDocument)
        }
        compose.waitUntil(10_000) { viewModel().state.value.documents.none { it.id in createdIds } }
    }

    @Test
    fun noteCanBeEditedPreviewedAndReopenedFromDisk() {
        val title = createNote()
        val source = "# Physics notes\n\nEnergy is \$E=mc^2\$.\n\n- Check units\n- Review equations"
        compose.onNodeWithTag("document-source").performTextReplacement(source)
        saveScreenshot("editor.png")
        compose.onNodeWithText("Preview", useUnmergedTree = false).performClick()
        compose.onNodeWithTag("document-preview").assertIsDisplayed()
        waitForRenderedPreview()
        saveScreenshot("preview.png")
        compose.onNodeWithText("Edit", useUnmergedTree = false).performClick()
        compose.onNodeWithTag("document-source").assertTextEquals(source)

        // Wait for the asynchronous atomic save instead of assuming UI state proves persistence.
        compose.waitUntil(10_000) {
            DocumentStore(File(compose.activity.filesDir, "documents")).load()
                .any { it.id in createdIds && it.title == title && it.source == source }
        }
        compose.onNodeWithContentDescription("Library").performClick()
        compose.onNodeWithTag("document-library").performScrollToNode(hasText(title))
        compose.onNodeWithText(title).assertIsDisplayed().performClick()
        compose.onNodeWithTag("document-source").assertTextEquals(source)

        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("document-title").assertTextContains(title)
        compose.onNodeWithTag("document-source").assertTextEquals(source)
    }

    @Test
    fun settingsIncludesInstalledVersionAndUpdateButton() {
        compose.waitUntil(15_000) { !viewModel().state.value.loading }
        compose.runOnIdle { viewModel().closeDocument() }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("App updates").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Installed version ${BuildConfig.VERSION_NAME}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("check-updates-button").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
    }

    @Test
    fun undoRestoresTextWithoutLosingTheDocument() {
        val title = createNote()
        val original = "The quadratic equation is \$ax^2+bx+c=0\$."
        compose.onNodeWithTag("document-source").performTextReplacement(original)
        compose.onNodeWithTag("document-source").performTextReplacement("An accidental replacement")
        compose.onNodeWithText("Undo").performClick()
        compose.onNodeWithTag("document-source").assertTextEquals(original)
        compose.runOnIdle {
            assertEquals(title, viewModel().state.value.current?.title)
            assertEquals(original, viewModel().state.value.current?.source)
        }
    }

    @Test
    fun nativeTypingKeepsEveryCharacter() {
        createNote()
        val noteId = viewModel().state.value.current!!.id
        val expected = "rapid typing 12345"
        compose.onNodeWithTag("document-source").performTextReplacement("")
        compose.onNodeWithTag("document-source").performClick()
        compose.waitForIdle()

        // Native key events exercise rapid incremental input rather than one semantic replacement.
        InstrumentationRegistry.getInstrumentation().sendStringSync(expected)

        compose.waitUntil(10_000) { viewModel().state.value.current?.source == expected }
        compose.onNodeWithTag("document-source").assertTextEquals(expected)
        compose.waitUntil(10_000) {
            DocumentStore(File(compose.activity.filesDir, "documents")).load()
                .any { it.id == noteId && it.source == expected }
        }
    }

    @Test
    fun missingLocalModelShowsSetupAndManualEditingStillWorks() {
        compose.waitUntil(15_000) { !viewModel().state.value.loading }
        assumeFalse("This test never removes an existing local model", viewModel().state.value.model.installed)
        createNote()
        val source = "A note I can edit without AI."
        compose.onNodeWithTag("document-source").performTextReplacement(source)
        compose.onNodeWithTag("ai-edit").assertIsDisplayed().performClick()
        compose.onNodeWithText("Set up local AI or connect ChatGPT in Settings to suggest changes.").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()

        // Exercise the ViewModel's defensive guard as well as the UI's setup route.
        compose.runOnIdle { viewModel().requestEdit("Improve the writing") }
        compose.onNodeWithText("Something needs attention").assertIsDisplayed()
        compose.onNodeWithText("Download or import the local model in Local AI setup first. Basic printed-text extraction is available immediately.").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithTag("document-source").assertTextEquals(source)
        val revised = "$source Still fully editable."
        compose.onNodeWithTag("document-source").performTextReplacement(revised)
        compose.runOnIdle {
            assertEquals(revised, viewModel().state.value.current?.source)
            assertTrue(!viewModel().state.value.busy)
        }
    }

    @Test
    fun searchFindsTitlesAndSourceAndTabsPreserveEdits() {
        val suffix = UUID.randomUUID().toString().take(8)
        val firstTitle = createNote("Mechanics $suffix")
        val marker = "conservation-$suffix"
        val source = "# Mechanics\n\nEnergy is conserved. Search marker: $marker\n\n\$E=mc^2\$"
        compose.onNodeWithTag("document-source").performTextReplacement(source)
        val secondTitle = createNote("Algebra $suffix")
        compose.onNodeWithTag("document-source").performTextReplacement("Quadratic equations and polynomial notes.")
        hideKeyboard()
        compose.onNodeWithContentDescription("Library").performClick()

        fun search(query: String) {
            compose.onNodeWithTag("document-library").performScrollToNode(hasTestTag("document-search"))
            compose.onNodeWithTag("document-search").performTextReplacement(query)
            hideKeyboard()
        }
        search(secondTitle)
        val secondResult = hasText(secondTitle) and !hasTestTag("document-search")
        compose.onNodeWithTag("document-library").performScrollToNode(secondResult)
        compose.onNode(secondResult).assertIsDisplayed()

        search("no-result-$suffix")
        compose.onNodeWithTag("document-library").performScrollToNode(hasText("No matching documents"))
        compose.onNodeWithText("No matching documents").assertIsDisplayed()

        search(marker)
        val firstResult = hasText(firstTitle) and !hasTestTag("document-search")
        compose.onNodeWithTag("document-library").performScrollToNode(firstResult)
        compose.onNode(firstResult).assertIsDisplayed().performClick()
        compose.onNodeWithText("Edit").assertIsSelected()
        compose.onNodeWithText("Preview").performClick().assertIsSelected()
        compose.onNodeWithTag("document-preview").assertIsDisplayed()
        compose.onNodeWithText("Photos").performClick().assertIsSelected()
        compose.onNodeWithText("Edit").performClick().assertIsSelected()
        compose.onNodeWithTag("document-source").assertTextEquals(source)
    }

    private fun waitForRenderedPreview() {
        val ready = AtomicReference(false)
        val renderedPhysics = """
            (() => {
                const page = document.getElementById('document');
                return !!page && page.innerText.includes('Physics notes') &&
                    !!page.querySelector('.katex-mathml math') &&
                    !!page.querySelector('.katex-html') &&
                    document.fonts.status === 'loaded';
            })()
        """.trimIndent()
        compose.waitUntil(15_000) {
            compose.runOnIdle {
                findWebView(compose.activity.findViewById(android.R.id.content))
                    ?.evaluateJavascript(renderedPhysics) { ready.set(it == "true") }
            }
            ready.get()
        }
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findWebView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun hideKeyboard() {
        compose.runOnIdle {
            val activity = compose.activity
            val keyboard = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            keyboard.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
            activity.currentFocus?.clearFocus()
        }
        compose.waitForIdle()
    }

    private fun saveScreenshot(name: String) {
        hideKeyboard()
        SystemClock.sleep(300)
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            val folder = File(compose.activity.getExternalFilesDir(null), "verification").apply { mkdirs() }
            File(folder, name).outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { screenshot.recycle() }
    }
}
