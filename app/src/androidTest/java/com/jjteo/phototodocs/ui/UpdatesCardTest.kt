package com.jjteo.phototodocs.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jjteo.phototodocs.updates.UpdateStatus
import com.jjteo.phototodocs.updates.UpdateUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UpdatesCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun checkButtonCallsTheUpdateAction() {
        var checks = 0
        compose.setContent { MaterialTheme { UpdatesCard(UpdateUiState("1.0.1"), { checks++ }, {}) } }
        compose.onNodeWithText("Installed version 1.0.1").assertIsDisplayed()
        compose.onNodeWithTag("check-updates-button").performClick()
        compose.runOnIdle { assertEquals(1, checks) }
    }

    @Test fun pendingCheckCannotBeStartedTwice() {
        compose.setContent { MaterialTheme { UpdatesCard(UpdateUiState("1.0.1", status = UpdateStatus.CHECKING), {}, {}) } }
        compose.onNodeWithText("Checking GitHub…").assertIsDisplayed()
        compose.onNodeWithTag("check-updates-button").assertIsNotEnabled()
    }

    @Test fun newVersionOffersTheReleaseDownload() {
        var opens = 0
        compose.setContent { MaterialTheme { UpdatesCard(UpdateUiState("1.0.1", status = UpdateStatus.AVAILABLE,
            latestVersion = "v1.0.2", message = "Version v1.0.2 is available."), {}, { opens++ }) } }
        compose.onNodeWithText("Latest release: v1.0.2").assertIsDisplayed()
        compose.onNodeWithText("Download update").performClick()
        compose.runOnIdle { assertEquals(1, opens) }
    }

    @Test fun failedCheckKeepsRetryAndBrowserActions() {
        compose.setContent { MaterialTheme { UpdatesCard(UpdateUiState("1.0.1", status = UpdateStatus.ERROR,
            message = "Could not reach GitHub."), {}, {}) } }
        compose.onNodeWithTag("update-check-result").assertIsDisplayed()
        compose.onNodeWithTag("check-updates-button").assertIsDisplayed()
        compose.onNodeWithText("Open GitHub releases").assertIsDisplayed()
    }
}
