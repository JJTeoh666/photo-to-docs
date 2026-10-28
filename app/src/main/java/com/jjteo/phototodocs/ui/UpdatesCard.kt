package com.jjteo.phototodocs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jjteo.phototodocs.updates.UpdateStatus
import com.jjteo.phototodocs.updates.UpdateUiState

@Composable
internal fun UpdatesCard(state: UpdateUiState, onCheck: () -> Unit, onOpen: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("App updates", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Installed version ${state.installedVersion}", style = MaterialTheme.typography.bodyMedium)
            Text("Check the latest GitHub release. Update checks need an internet connection.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.status == UpdateStatus.CHECKING) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Checking GitHub…", style = MaterialTheme.typography.bodySmall)
            }
            state.latestVersion?.let { Text("Latest release: $it", style = MaterialTheme.typography.bodySmall) }
            state.message?.let { Text(it, color = if (state.status == UpdateStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("update-check-result")) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onCheck, enabled = state.status != UpdateStatus.CHECKING, modifier = Modifier.testTag("check-updates-button")) {
                    Text("Check for updates")
                }
                OutlinedButton(onClick = onOpen, modifier = Modifier.testTag("open-update-release")) {
                    Text(if (state.status == UpdateStatus.AVAILABLE) "Download update" else "Open GitHub releases")
                }
            }
            Text("Downloads open in your browser. Android asks you to confirm installation.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
