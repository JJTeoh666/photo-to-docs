package com.jjteo.phototodocs.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.jjteo.phototodocs.AppViewModel
import com.jjteo.phototodocs.MainActivity
import com.jjteo.phototodocs.data.AppearancePreferences
import com.jjteo.phototodocs.data.ThemeMode
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Restores appearance and removes only the note created by this test. */
class AppearanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var original: ThemeMode
    private var createdId: String? = null
    private fun vm() = ViewModelProvider(compose.activity)[AppViewModel::class.java]

    @Before fun rememberPreference() {
        compose.waitUntil(15_000) { !vm().state.value.loading }
        original = AppearancePreferences(compose.activity).read()
    }

    @After fun restoreOwnChanges() {
        compose.runOnIdle {
            vm().setThemeMode(original)
            createdId?.let(vm()::deleteDocument)
        }
    }

    private fun openSettings() {
        compose.runOnIdle { vm().closeDocument() }
        compose.onNodeWithTag("document-library").performScrollToNode(hasText("Settings"))
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
    }

    private fun assertCanvas(expected: Color) {
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("app-root").captureToImage().toPixelMap()
        assertEquals(expected, pixels[1, 1])
    }

    @Test fun choosingDarkAndLightPersistsAndRecolorsImmediately() {
        openSettings()
        compose.onNodeWithTag("theme-dark").performClick().assertIsSelected()
        assertCanvas(Color(0xFF18212B))
        compose.runOnIdle { assertEquals(ThemeMode.DARK, AppearancePreferences(compose.activity).read()) }
        compose.waitUntil(10_000) {
            File(compose.activity.applicationInfo.dataDir, "shared_prefs/appearance.xml").let { it.isFile && it.readText().contains("dark") }
        }
        saveScreenshot("settings-graphite.png")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("theme-dark").assertIsSelected()
        assertCanvas(Color(0xFF18212B))
        compose.onNodeWithTag("theme-light").performClick().assertIsSelected()
        assertCanvas(Color(0xFFF4F8FC))
        compose.runOnIdle { assertEquals(ThemeMode.LIGHT, AppearancePreferences(compose.activity).read()) }
        saveScreenshot("settings-ocean.png")
    }

    @Test fun systemRestoresThePhonesAppearance() {
        openSettings()
        compose.onNodeWithTag("theme-dark").performClick()
        compose.onNodeWithTag("theme-system").performClick().assertIsSelected()
        val phoneDark = compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        assertCanvas(if (phoneDark) Color(0xFF18212B) else Color(0xFFF4F8FC))
        compose.runOnIdle { assertEquals(ThemeMode.SYSTEM, AppearancePreferences(compose.activity).read()) }
    }

    @Test fun themeChangesUpdateRealPreviewAndKeepSource() {
        val source = "# Theme preview\n\nEnergy is \$E=mc^2\$."
        compose.runOnIdle {
            vm().createDocument()
            createdId = vm().state.value.current!!.id
            vm().updateSource(source)
            vm().setThemeMode(ThemeMode.DARK)
        }
        compose.onNodeWithText("Preview").performClick()
        waitForPreview("rgb(35, 48, 62)")
        saveScreenshot("preview-graphite.png")
        compose.runOnIdle { vm().setThemeMode(ThemeMode.LIGHT) }
        waitForPreview("rgb(255, 255, 255)")
        compose.onNodeWithTag("ai-edit-preview").assertIsDisplayed().performClick()
        compose.onNodeWithText("Edit with AI").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithTag("document-source").assertTextEquals(source)
        compose.runOnIdle { assertEquals(source, vm().state.value.current!!.source) }
    }

    private fun waitForPreview(background: String) {
        val ready = AtomicBoolean(false)
        compose.waitUntil(15_000) {
            compose.runOnIdle {
                findWebView(compose.activity.findViewById(android.R.id.content))?.evaluateJavascript(
                    "document.body.innerText.includes('Theme preview') && !!document.querySelector('.katex') && getComputedStyle(document.documentElement).backgroundColor === '$background'",
                ) { ready.set(it == "true") }
            }
            ready.get()
        }
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            val folder = File(compose.activity.getExternalFilesDir(null), "verification").apply { mkdirs() }
            File(folder, name).outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { screenshot.recycle() }
    }
}
