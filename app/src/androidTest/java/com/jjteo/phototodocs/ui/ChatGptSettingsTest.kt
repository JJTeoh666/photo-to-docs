package com.jjteo.phototodocs.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.jjteo.phototodocs.AppViewModel
import com.jjteo.phototodocs.MainActivity
import com.jjteo.phototodocs.ai.AiProvider
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue

class ChatGptSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun vm() = ViewModelProvider(compose.activity)[AppViewModel::class.java]
    private lateinit var original: AiProvider
    private var ownNote: String? = null
    @Before fun setup() {
        compose.waitUntil(15_000) { !vm().state.value.loading }
        original = vm().state.value.aiProvider
    }
    @After fun restore() {
        compose.runOnIdle { vm().cancelChatGpt(); vm().setAiProvider(original); ownNote?.let(vm()::deleteDocument) }
    }
    @Test fun providerChoiceIsExplicitAndPersistsAcrossActivityRecreation() {
        compose.runOnIdle { vm().setAiProvider(AiProvider.LOCAL); vm().closeDocument() }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithTag("provider-local").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("provider-chatgpt").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("chatgpt-sign-in").performScrollTo().assertIsEnabled()
        assertEquals(AiProvider.CHATGPT, vm().state.value.aiProvider)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("provider-chatgpt").performScrollTo().assertIsSelected()
    }
    @Test fun missingCloudConnectionDoesNotRequireLocalWeightsOrChangeTheNote() {
        assumeFalse(vm().state.value.chatGpt.ready)
        val source = "Keep this note and ${'$'}x^2${'$'} unchanged."
        compose.runOnIdle {
            vm().setAiProvider(AiProvider.CHATGPT); vm().createDocument()
            ownNote = vm().state.value.current!!.id
            vm().updateSource(source)
        }
        compose.onNodeWithTag("ai-edit").performClick()
        compose.onNodeWithText("ChatGPT · submitted photos and text are sent to OpenAI").assertIsDisplayed()
        compose.onNodeWithText("Set up AI").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { vm().requestEdit("Improve writing") }
        compose.onNodeWithText("Connect ChatGPT, enable plan use, and choose a model in Settings first.").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithTag("document-source").assertTextEquals(source)
        assertEquals(source, vm().state.value.current!!.source)
    }
    @Test fun browserSignInSurvivesActivityRecreationAndCanBeCancelled() {
        assumeTrue("Do not alter a saved user account during the sign-in lifecycle check", vm().state.value.chatGpt.accounts.isEmpty())
        compose.runOnIdle { vm().setAiProvider(AiProvider.LOCAL); vm().closeDocument() }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithTag("chatgpt-sign-in").performScrollTo().performClick()
        compose.waitUntil(10_000) { vm().state.value.chatGpt.signingIn }
        val originalVm = vm()
        // A shell launch represents the user returning to the app. A stopped app cannot launch itself
        // over Chrome under Android's background-activity restrictions.
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(
            "am start -W -n com.jjteo.phototodocs/.MainActivity -f 0x30020000")).use { it.readBytes() }
        compose.waitUntil(10_000) { compose.activity.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { vm().state.value.chatGpt.signingIn }
        assertSame(originalVm, vm())
        compose.onNodeWithText("Cancel sign-in").performScrollTo().performClick()
        compose.waitUntil(10_000) { !vm().state.value.chatGpt.working && !vm().state.value.chatGpt.signingIn && vm().state.value.chatGpt.status.contains("cancelled") }
        assertFalse(vm().state.value.chatGpt.signingIn)
        assertTrue(vm().state.value.chatGpt.status, vm().state.value.chatGpt.status.contains("cancelled"))
        assertEquals(AiProvider.LOCAL, vm().state.value.aiProvider)
    }
}
