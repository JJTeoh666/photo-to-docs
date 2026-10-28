package com.jjteo.phototodocs.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.jjteo.phototodocs.R
import com.jjteo.phototodocs.ai.ModelStatus
import com.jjteo.phototodocs.data.ThemeMode
import com.jjteo.phototodocs.updates.UpdateUiState
import java.util.Locale

@Composable
internal fun SettingsScreen(
    themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit,
    model: ModelStatus, updates: UpdateUiState, onCheckUpdates: () -> Unit, onOpenUpdates: () -> Unit,
    onBack: () -> Unit, onDownload: () -> Unit,
    onCancelDownload: () -> Unit, onDelete: () -> Unit, onImport: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    ContentFrame(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { AppIcon(R.drawable.ic_arrow_back, description = "Back") }
                Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 8.dp).semantics { heading() })
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                AppearanceCard(themeMode, onThemeModeChange)
                UpdatesCard(updates, onCheckUpdates, onOpenUpdates)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Local AI", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { AppIcon(R.drawable.ic_ai) }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("Gemma 4 E2B", style = MaterialTheme.typography.titleMedium)
                                    Text(if (model.installed) "Installed on this device" else "Optional download", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text("Read equations from photos and ask AI to improve your notes.", style = MaterialTheme.typography.bodyMedium)
                            Text("2.6 GB download. ARM64 phone, 8 GB+ RAM recommended.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(when {
                                model.downloading -> "Downloading model"
                                model.loading -> "Loading local AI"
                                model.ready -> "Ready on ${model.backend}"
                                model.installed -> "Ready to use"
                                model.downloadedBytes > 0 -> "Download paused"
                                else -> "Not installed"
                            }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            if (model.downloading || model.downloadedBytes > 0 && !model.installed) {
                                val progress = if (model.totalBytes > 0) (model.downloadedBytes.toFloat() / model.totalBytes).coerceIn(0f, 1f) else 0f
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                Text("${bytesLabel(model.downloadedBytes)} / ${if (model.totalBytes > 0) bytesLabel(model.totalBytes) else "2.6 GB"}", style = MaterialTheme.typography.labelSmall)
                            }
                            model.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            when {
                                model.downloading -> OutlinedButton(onClick = onCancelDownload, modifier = Modifier.heightIn(min = 48.dp)) { Text("Pause download") }
                                !model.installed -> Button(onClick = onDownload, enabled = !model.loading, modifier = Modifier.heightIn(min = 48.dp)) { ActionLabel(R.drawable.ic_download, if (model.downloadedBytes > 0) "Resume download" else "Download model") }
                                else -> OutlinedButton(onClick = { confirmDelete = true }, enabled = !model.loading, modifier = Modifier.heightIn(min = 48.dp)) { Text("Remove model") }
                            }
                            if (!model.downloading && !model.installed) TextButton(onClick = onImport, enabled = !model.loading, modifier = Modifier.heightIn(min = 48.dp)) { Text("Import a .litertlm model file") }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(R.drawable.ic_lock, tint = MaterialTheme.colorScheme.secondary)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Your work stays here", style = MaterialTheme.typography.titleMedium)
                        Text("Photos, notes, and AI edits are processed on your phone. After the model download, AI works offline.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column {
                    TextButton(onClick = { details = !details }, modifier = Modifier.heightIn(min = 48.dp).semantics { stateDescription = if (details) "Expanded" else "Collapsed" }) {
                        Text(if (details) "Hide app details" else "App details and tips")
                        Spacer(Modifier.width(8.dp))
                        AppIcon(if (details) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down)
                    }
                    if (details) {
                        Text("You can start without AI", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                        Text("Basic printed-text recognition, editing, previews, and exports work without the large model. Use local AI for mathematical equations. Check every transcription against the photo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        Text("About Photo to Docs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
                        Text("Gemma via Google LiteRT-LM. Equation previews by KaTeX; Word math by mathml2omml. Manrope typeface. Open-source licenses and library sources are included with the project.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Remove AI model?") }, text = { Text("This frees the model storage. Your documents and photos stay saved.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}

private fun bytesLabel(value: Long): String = if (value >= 1_000_000_000) String.format(Locale.US, "%.2f GB", value / 1_000_000_000.0) else String.format(Locale.US, "%.0f MB", value / 1_000_000.0)
