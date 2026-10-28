package com.jjteo.phototodocs.ui

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jjteo.phototodocs.R
import com.jjteo.phototodocs.ai.ModelStatus
import com.jjteo.phototodocs.data.NoteDocument
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun LibraryScreen(
    documents: List<NoteDocument>, loading: Boolean, model: ModelStatus,
    onNew: () -> Unit, onOpen: (String) -> Unit, onDelete: (String) -> Unit,
    onCamera: () -> Unit, onGallery: () -> Unit, onSettings: () -> Unit,
) {
    var deleting by remember { mutableStateOf<NoteDocument?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val matching = remember(documents, query) {
        if (query.isBlank()) documents else documents.filter {
            it.title.contains(query.trim(), ignoreCase = true) || it.source.contains(query.trim(), ignoreCase = true)
        }
    }
    ContentFrame(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("document-library"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(12.dp)) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { AppIcon(R.drawable.ic_scan) }
                    }
                    Text("Photo to Docs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    TextButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                        AppIcon(R.drawable.ic_settings)
                        Spacer(Modifier.width(8.dp))
                        Text("Settings")
                    }
                }
            }
            item {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Your documents", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.align(Alignment.CenterVertically).semantics { heading() })
                    TextButton(onClick = onNew, modifier = Modifier.heightIn(min = 48.dp).testTag("new-document")) { ActionLabel(R.drawable.ic_add, "New note") }
                }
            }
            item { CapturePanel(onCamera, onGallery) }
            item {
                Surface(onClick = onSettings, color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIcon(R.drawable.ic_lock)
                        Column(Modifier.weight(1f)) {
                            Text(if (model.installed) "Local AI is installed" else "Text recognition is ready", style = MaterialTheme.typography.titleSmall)
                            Text(if (model.installed) "Photos and notes stay on your phone." else "Add local AI for equations and editing.", style = MaterialTheme.typography.bodySmall)
                        }
                        AppIcon(R.drawable.ic_chevron_right)
                    }
                }
            }
            if (documents.isNotEmpty()) item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text("Search documents") }, singleLine = true,
                    leadingIcon = { AppIcon(R.drawable.ic_search) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { AppIcon(R.drawable.ic_close, description = "Clear search") } },
                    modifier = Modifier.fillMaxWidth().testTag("document-search"),
                    shape = MaterialTheme.shapes.medium,
                )
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (!loading && documents.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) { AppIcon(R.drawable.ic_document, modifier = Modifier.size(32.dp)) }
                    }
                    Text("Start with a page", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
                    Text("Take a photo above, or write your first note.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    OutlinedButton(onClick = onNew, modifier = Modifier.padding(top = 16.dp).heightIn(min = 48.dp)) { Text("Start writing") }
                }
            }
            if (documents.isNotEmpty() && matching.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Text("No matching documents", style = MaterialTheme.typography.titleMedium)
                    Text("Try another title or a word from your notes.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { query = "" }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear search") }
                }
            }
            items(matching, key = { it.id }) { doc ->
                DocumentRow(doc, { onOpen(doc.id) }, { deleting = doc })
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(R.drawable.ic_lock, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Stored on this device. No account needed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    deleting?.let { doc ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete document?") }, text = { Text("“${doc.title}” and its saved photos will be removed from this app.") },
            confirmButton = { TextButton(onClick = { onDelete(doc.id); deleting = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}

@Composable
private fun CapturePanel(onCamera: () -> Unit, onGallery: () -> Unit) {
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    val accent = LocalWorkspaceAccent.current
    Surface(color = Color.Transparent, contentColor = Color.White, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(accent.start, accent.end))).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Make a photo editable", style = MaterialTheme.typography.titleLarge)
                    Text("Text and equations for Word or LaTeX.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
                if (!largeText) Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { AppIcon(R.drawable.ic_scan, modifier = Modifier.size(24.dp)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCamera, modifier = Modifier.heightIn(min = 48.dp), shape = MaterialTheme.shapes.small, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = accent.start)) { ActionLabel(R.drawable.ic_camera, "Take photo") }
                OutlinedButton(onClick = onGallery, modifier = Modifier.heightIn(min = 48.dp), shape = MaterialTheme.shapes.small, border = androidx.compose.foundation.BorderStroke(1.dp, Color.White), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { ActionLabel(R.drawable.ic_image, "Gallery") }
            }
        }
    }
}

@Composable
private fun DocumentRow(doc: NoteDocument, onOpen: () -> Unit, onDelete: () -> Unit) {
    Surface(onClick = onOpen, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 40.dp, height = 52.dp).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                AppIcon(if (doc.pages.isEmpty()) R.drawable.ic_document else R.drawable.ic_image, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(doc.title.ifBlank { "Untitled document" }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text(doc.source.replace(Regex("\\s+"), " ").ifBlank { "Open to start editing" }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                val photoLabel = if (doc.pages.isEmpty()) "Text note" else "${doc.pages.size} photo${if (doc.pages.size == 1) "" else "s"}"
                Text("$photoLabel • ${SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(doc.updatedAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            IconButton(onClick = onDelete) { AppIcon(R.drawable.ic_delete, description = "Delete ${doc.title.ifBlank { "Untitled document" }}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
