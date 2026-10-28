package com.jjteo.phototodocs.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    private fun createNote(): String {
        compose.waitUntil(15_000) { !viewModel().state.value.loading }
        compose.runOnIdle { viewModel().closeDocument() }
        compose.onNodeWithText("+ New note").performClick()
        val title = "UI smoke ${UUID.randomUUID()}"
        compose.onNodeWithTag("document-title").performTextReplacement(title)
        compose.runOnIdle { createdIds += viewModel().state.value.current!!.id }
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
        SystemClock.sleep(1_000) // Visual artifact only; assertions do not depend on this delay.
        saveScreenshot("preview.png")
        compose.onNodeWithText("Edit", useUnmergedTree = false).performClick()
        compose.onNodeWithTag("document-source").assertTextEquals(source)

        // Wait for the asynchronous atomic save instead of assuming UI state proves persistence.
        compose.waitUntil(10_000) {
            DocumentStore(File(compose.activity.filesDir, "documents")).load()
                .any { it.id in createdIds && it.title == title && it.source == source }
        }
        compose.onNodeWithText("‹ Library").performClick()
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
        compose.onNodeWithText("App updates").assertIsDisplayed()
        compose.onNodeWithText("Installed version ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        compose.onNodeWithTag("check-updates-button").assertIsDisplayed()
        compose.onNodeWithText("‹ Back").performClick()
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
    fun missingLocalModelShowsSetupAndManualEditingStillWorks() {
        compose.waitUntil(15_000) { !viewModel().state.value.loading }
        assumeFalse("This test never removes an existing local model", viewModel().state.value.model.installed)
        createNote()
        val source = "A note I can edit without AI."
        compose.onNodeWithTag("document-source").performTextReplacement(source)
        compose.onNodeWithText("✦ AI").performClick()
        compose.onNodeWithText("Download the local AI model in AI setup first.").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()

        // Exercise the ViewModel's defensive guard as well as the UI's setup route.
        compose.runOnIdle { viewModel().requestEdit("Improve the writing") }
        compose.onNodeWithText("Something needs attention").assertIsDisplayed()
        compose.onNodeWithText("Download or import the local model in Local AI setup first.").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithTag("document-source").assertTextEquals(source)
        val revised = "$source Still fully editable."
        compose.onNodeWithTag("document-source").performTextReplacement(revised)
        compose.runOnIdle {
            assertEquals(revised, viewModel().state.value.current?.source)
            assertTrue(!viewModel().state.value.busy)
        }
    }

    private fun saveScreenshot(name: String) {
        compose.runOnIdle {
            val activity = compose.activity
            val keyboard = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            keyboard.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
            activity.currentFocus?.clearFocus()
        }
        compose.waitForIdle()
        SystemClock.sleep(300)
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            val folder = File(compose.activity.getExternalFilesDir(null), "verification").apply { mkdirs() }
            File(folder, name).outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { screenshot.recycle() }
    }
}
