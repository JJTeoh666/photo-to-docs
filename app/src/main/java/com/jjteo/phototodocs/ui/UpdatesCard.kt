package com.jjteo.phototodocs.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jjteo.phototodocs.updates.UpdateStatus
import com.jjteo.phototodocs.updates.UpdateUiState

@Composable
internal fun UpdatesCard(state: UpdateUiState, onCheck: () -> Unit, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("App updates", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Installed version ${state.installedVersion}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
            Text("Check GitHub for a newer version. Internet required.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            if (state.status == UpdateStatus.CHECKING || state.latestVersion != null || state.message != null) {
                val isError = state.status == UpdateStatus.ERROR
                Surface(
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    shape = MaterialTheme.shapes.medium,
                    color = if (isError) colors.errorContainer else colors.surfaceContainerLow,
                    contentColor = if (isError) colors.onErrorContainer else colors.onSurface,
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.status == UpdateStatus.CHECKING) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("Checking GitHub…", style = MaterialTheme.typography.bodyMedium)
                        }
                        state.latestVersion?.let { Text("Latest release: $it", style = MaterialTheme.typography.bodySmall) }
                        state.message?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("update-check-result"))
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCheck, enabled = state.status != UpdateStatus.CHECKING,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("check-updates-button")) {
                    Text("Check for updates")
                }
                OutlinedButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp).testTag("open-update-release")) {
                    Text(if (state.status == UpdateStatus.AVAILABLE) "Download update" else "Open GitHub releases")
                }
            }
            Text("Downloads open in your browser. Android asks you to confirm installation.",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}
