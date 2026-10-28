package com.jjteo.phototodocs.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjteo.phototodocs.AppViewModel
import com.jjteo.phototodocs.ExportFormat
import com.jjteo.phototodocs.ai.ModelStatus
import com.jjteo.phototodocs.data.NoteDocument
import com.jjteo.phototodocs.data.PhotoFiles
import com.jjteo.phototodocs.data.PhotoPage
import com.jjteo.phototodocs.documents.DocumentPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

private val Ink = Color(0xFF203D34)
private val Paper = Color(0xFFF7F7F0)
private val Sage = Color(0xFFE4EBDD)
private val Muted = Color(0xFF58665D)

@Composable
fun PhotoToDocsApp(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    var settings by rememberSaveable { mutableStateOf(false) }
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    var launchError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val capture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCapture
        pendingCapture = null
        if (success && uri != null) vm.importPhoto(Uri.parse(uri))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach(vm::importPhoto)
    }
    val modelImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(it, actionLabel = "Dismiss", duration = SnackbarDuration.Long)
            vm.dismissNotice()
        }
    }
    val cameraAction: () -> Unit = {
        runCatching {
            val uri = PhotoFiles.newCaptureUri(context)
            pendingCapture = uri.toString()
            capture.launch(uri)
        }.onFailure { launchError = "A camera app could not be opened. You can choose a photo from your gallery." }
    }
    val galleryAction: () -> Unit = {
        runCatching { gallery.launch("image/*") }
            .onFailure { launchError = "The photo picker could not be opened." }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink, onPrimary = Color.White, primaryContainer = Sage,
            onPrimaryContainer = Ink, secondary = Color(0xFF6B7650),
            secondaryContainer = Color(0xFFEDF0DF), background = Paper,
            surface = Color(0xFFFEFEFA), surfaceVariant = Color(0xFFEBEEE5),
            onSurface = Color(0xFF1B2922), onSurfaceVariant = Muted,
            outline = Color(0xFFB6C1B5)
        ),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(30.dp)
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            Surface(Modifier.fillMaxSize(), color = Paper) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                if (state.busy) BusyBanner(state.busyLabel, state.partial, !state.busyLabel.startsWith("Importing"), vm::cancelTask)
                when {
                    settings -> SettingsScreen(
                        model = state.model,
                        onBack = { settings = false },
                        onDownload = vm::downloadModel,
                        onCancelDownload = vm::cancelModelDownload,
                        onDelete = vm::deleteModel,
                        onImport = { modelImport.launch(arrayOf("*/*")) }
                    )
                    state.current == null -> LibraryScreen(
                        documents = state.documents, loading = state.loading,
                        onNew = vm::createDocument, onOpen = vm::openDocument,
                        onDelete = vm::deleteDocument, onCamera = cameraAction,
                        onGallery = galleryAction, onSettings = { settings = true },
                        model = state.model
                    )
                    else -> DocumentScreen(
                        document = state.current!!, vm = vm, busy = state.busy,
                        model = state.model, onCamera = cameraAction,
                        onGallery = galleryAction, onSettings = { settings = true }
                    )
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(16.dp))
        }
        BackHandler(settings || state.current != null) {
            if (settings) settings = false else vm.closeDocument()
        }
        state.proposal?.let { proposal ->
            AlertDialog(
                onDismissRequest = vm::rejectProposal,
                title = { Text("Review AI changes") },
                text = {
                    Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
                        Text(proposal.label, style = MaterialTheme.typography.labelLarge, color = Ink)
                        Spacer(Modifier.height(14.dp))
                        Text("BEFORE", style = MaterialTheme.typography.labelSmall, color = Muted)
                        Text(proposal.oldText.ifBlank { "(empty)" }, modifier = Modifier.padding(vertical = 8.dp))
                        HorizontalDivider()
                        Text("PROPOSED", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelSmall, color = Ink)
                        SelectionContainerText(proposal.newText)
                        Text("Your saved document changes only when you accept.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                },
                confirmButton = { Button(onClick = vm::acceptProposal) { Text("Accept changes") } },
                dismissButton = { TextButton(onClick = vm::rejectProposal) { Text("Keep original") } }
            )
        }
        (launchError ?: state.error)?.let { error ->
            AlertDialog(
                onDismissRequest = { launchError = null; vm.dismissError() },
                title = { Text("Something needs attention") }, text = { Text(error) },
                confirmButton = { TextButton(onClick = { launchError = null; vm.dismissError() }) { Text("OK") } }
            )
        }
    }
}

@Composable
private fun LibraryScreen(
    documents: List<NoteDocument>, loading: Boolean, model: ModelStatus,
    onNew: () -> Unit, onOpen: (String) -> Unit, onDelete: (String) -> Unit,
    onCamera: () -> Unit, onGallery: () -> Unit, onSettings: () -> Unit
) {
    var deleting by remember { mutableStateOf<NoteDocument?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PHOTOTODOCS", letterSpacing = 2.sp, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = Ink)
                Text("Your study, editable.", fontFamily = FontFamily.Serif, fontSize = 28.sp, color = Ink)
            }
            TextButton(onClick = onSettings) { Text("AI setup") }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Ink), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp)) {
                        Text("FROM PAPER\nTO POSSIBILITY", fontSize = 13.sp, lineHeight = 20.sp, letterSpacing = 1.5.sp, color = Color(0xFFCFDEBD), fontWeight = FontWeight.Bold)
                        Text("Capture a thought.\nMake it your own.", fontFamily = FontFamily.Serif, fontSize = 30.sp, lineHeight = 35.sp, color = Color.White, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))
                        Text("Turn photos into text and equations you can edit, then export to Word or LaTeX.", color = Color(0xFFDEE6DC), style = MaterialTheme.typography.bodyMedium)
                        FlowRow(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = onCamera, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0E9C9), contentColor = Ink)) { Text("Take photo") }
                            OutlinedButton(onClick = onGallery, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("Gallery") }
                        }
                    }
                }
            }
            if (!model.installed) item {
                Surface(color = Sage, shape = RoundedCornerShape(16.dp), onClick = onSettings) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Private AI, on your phone", fontWeight = FontWeight.SemiBold, color = Ink)
                        Text("Set up the local model for equations and AI editing. Basic text recognition is ready now.", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Your documents", fontFamily = FontFamily.Serif, fontSize = 23.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onNew) { Text("+ New note") }
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (!loading && documents.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("A fresh page awaits.", fontFamily = FontFamily.Serif, fontSize = 22.sp, color = Ink)
                    Text("Take a photo, choose an image, or start a blank note.", style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.padding(top = 8.dp))
                    OutlinedButton(onClick = onNew, modifier = Modifier.padding(top = 18.dp)) { Text("Start writing") }
                }
            }
            items(documents, key = { it.id }) { doc ->
                Card(onClick = { onOpen(doc.id) }, colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(12.dp), color = Sage, modifier = Modifier.size(48.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(if (doc.pages.isEmpty()) "Aa" else "∑", fontFamily = FontFamily.Serif, fontSize = 25.sp, color = Ink) }
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(doc.title.ifBlank { "Untitled document" }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(doc.source.replace(Regex("\\s+"), " ").ifBlank { "Ready for your ideas" }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 5.dp))
                            Text("${doc.pages.size} photo${if (doc.pages.size == 1) "" else "s"} · ${dateLabel(doc.updatedAt)}", style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.padding(top = 8.dp))
                        }
                        TextButton(onClick = { deleting = doc }, contentPadding = PaddingValues(4.dp), modifier = Modifier.semantics { contentDescription = "Delete ${doc.title}" }) { Text("⋮", fontSize = 24.sp) }
                    }
                }
            }
            item { Text("Saved on this device · No account needed", style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
    deleting?.let { doc ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete document?") }, text = { Text("“${doc.title}” and its saved photos will be removed from this app.") },
            confirmButton = { TextButton(onClick = { onDelete(doc.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}

@Composable
private fun DocumentScreen(
    document: NoteDocument, vm: AppViewModel, busy: Boolean, model: ModelStatus,
    onCamera: () -> Unit, onGallery: () -> Unit, onSettings: () -> Unit
) {
    var tab by rememberSaveable(document.id) { mutableStateOf(if (document.pages.isNotEmpty() && document.source.isBlank()) 2 else 0) }
    var field by remember(document.id) { mutableStateOf(TextFieldValue(document.source)) }
    var showAi by remember { mutableStateOf(false) }
    var instruction by rememberSaveable { mutableStateOf("") }
    var exportMenu by remember { mutableStateOf(false) }
    val wordSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) { uri -> if (uri != null) vm.export(ExportFormat.WORD, uri) }
    val texSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-tex")) { uri -> if (uri != null) vm.export(ExportFormat.LATEX, uri) }
    LaunchedEffect(document.source) {
        if (field.text != document.source) field = TextFieldValue(document.source, TextRange(field.selection.end.coerceAtMost(document.source.length)))
    }
    LaunchedEffect(document.pages.size) {
        if (document.pages.isNotEmpty() && document.source.isBlank()) tab = 2
    }
    fun change(value: TextFieldValue) { field = value; vm.updateSource(value.text) }
    fun wrap(before: String, after: String = before) {
        val start = field.selection.min
        val end = field.selection.max
        val text = field.text.replaceRange(start, end, before + field.text.substring(start, end) + after)
        change(TextFieldValue(text, TextRange(start + before.length, end + before.length)))
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::closeDocument) { Text("‹ Library") }
            Spacer(Modifier.weight(1f))
            Text("AUTOSAVED", letterSpacing = 1.sp, style = MaterialTheme.typography.labelSmall, color = Muted)
            Box {
                TextButton(onClick = { exportMenu = true }) { Text("Export ↑") }
                DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                    DropdownMenuItem(text = { Text("Save Word (.docx)") }, onClick = { exportMenu = false; wordSave.launch(safeName(document.title) + ".docx") }, enabled = !busy)
                    DropdownMenuItem(text = { Text("Save LaTeX (.tex)") }, onClick = { exportMenu = false; texSave.launch(safeName(document.title) + ".tex") }, enabled = !busy)
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Share Word") }, onClick = { exportMenu = false; vm.shareExport(ExportFormat.WORD) }, enabled = !busy)
                    DropdownMenuItem(text = { Text("Share LaTeX") }, onClick = { exportMenu = false; vm.shareExport(ExportFormat.LATEX) }, enabled = !busy)
                }
            }
        }
        OutlinedTextField(
            value = document.title, onValueChange = vm::renameDocument, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).testTag("document-title"),
            singleLine = true, label = { Text("Document title") },
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Serif),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Edit", "Preview", "Photos ${document.pages.size}").forEachIndexed { index, label ->
                FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(label) })
            }
            TextButton(onClick = { showAi = true }, enabled = !busy) { Text("✦ AI") }
        }
        when (tab) {
            0 -> {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = { wrap("**") }, contentPadding = PaddingValues(horizontal = 9.dp)) { Text("B", fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { wrap("*") }, contentPadding = PaddingValues(horizontal = 9.dp)) { Text("I", fontFamily = FontFamily.Serif) }
                    TextButton(onClick = { wrap("\n## ", "\n") }, contentPadding = PaddingValues(horizontal = 9.dp)) { Text("H") }
                    TextButton(onClick = { wrap("\n- ", "\n") }, contentPadding = PaddingValues(horizontal = 9.dp)) { Text("• List") }
                    TextButton(onClick = { wrap("\n\$\$\n", "\n\$\$\n") }, contentPadding = PaddingValues(horizontal = 9.dp)) { Text("∑") }
                    TextButton(onClick = vm::undo, contentPadding = PaddingValues(horizontal = 9.dp)) { Text("Undo") }
                }
                OutlinedTextField(
                    value = field, onValueChange = { change(it) },
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp).testTag("document-source"),
                    placeholder = { Text("Write here, or add a photo to transcribe.\n\nUse **bold**, # headings, and LaTeX equations between \$…\$ or \$\$…\$\$.") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(16.dp)
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 19.dp, vertical = 10.dp)) {
                    Text(if (field.selection.collapsed) "Text + LaTeX equation source" else "${field.selection.max - field.selection.min} characters selected for AI", style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.weight(1f))
                    Text("${document.source.length} chars", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
            }
            1 -> DocumentPreview(document.source, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).testTag("document-preview"))
            2 -> PhotosPanel(document.pages, vm, busy, model.installed, onCamera, onGallery, onSettings, Modifier.weight(1f))
        }
    }
    if (showAi) {
        AlertDialog(
            onDismissRequest = { showAi = false }, title = { Text("A second pair of eyes") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(if (field.selection.collapsed) "Revise the whole document." else "Revise the selected text.", color = Muted)
                    Text("AI suggests changes for you to review.", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 4.dp))
                    OutlinedTextField(instruction, { instruction = it }, label = { Text("What should AI change?") }, placeholder = { Text("Clean up the formatting and keep the meaning.") }, minLines = 3, modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
                    TextButton(onClick = { instruction = "Fix spelling and improve clarity. Preserve every fact, number, and equation." }) { Text("Improve writing") }
                    TextButton(onClick = { instruction = "Format the equations as valid LaTeX. Preserve their mathematical meaning and all surrounding text." }) { Text("Format equations") }
                    if (!model.installed) Text("Download the local AI model in AI setup first.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                if (model.installed) Button(enabled = instruction.isNotBlank() && !busy, onClick = {
                    val start = if (field.selection.collapsed) 0 else field.selection.min
                    val end = if (field.selection.collapsed) -1 else field.selection.max
                    showAi = false
                    vm.requestEdit(instruction.trim(), start, end)
                }) { Text("Suggest changes") }
                else Button(onClick = { showAi = false; onSettings() }) { Text("AI setup") }
            },
            dismissButton = { TextButton(onClick = { showAi = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PhotosPanel(
    pages: List<PhotoPage>, vm: AppViewModel, busy: Boolean, installed: Boolean,
    onCamera: () -> Unit, onGallery: () -> Unit, onSettings: () -> Unit, modifier: Modifier
) {
    var cropping by remember { mutableStateOf<PhotoPage?>(null) }
    var removing by remember { mutableStateOf<PhotoPage?>(null) }
    var original by remember { mutableStateOf<PhotoPage?>(null) }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onCamera, enabled = !busy) { Text("Take photo") }
                OutlinedButton(onClick = onGallery, enabled = !busy) { Text("Add images") }
            }
        }
        if (pages.isEmpty()) item {
            Column(Modifier.padding(vertical = 24.dp)) {
                Text("Bring your notes in.", fontFamily = FontFamily.Serif, fontSize = 24.sp)
                Text("Photograph a clear, flat page. Crop around a single equation for the best results.", color = Muted, modifier = Modifier.padding(top = 10.dp))
            }
        }
        if (pages.isNotEmpty()) item {
            Column {
                Text("Read photos in this order", fontWeight = FontWeight.SemiBold)
                Text("Transcriptions are drafts. Check symbols and numbers against the photo.", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 4.dp))
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Button(onClick = { if (installed) vm.transcribeAll(false) else onSettings() }, enabled = !busy) { Text("AI: all pages") }
                    TextButton(onClick = { vm.transcribeAll(true) }, enabled = !busy) { Text("Basic text: all") }
                }
            }
        }
        items(pages, key = { it.id }) { page ->
            val index = pages.indexOfFirst { it.id == page.id }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("PAGE ${index + 1}", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.sp, color = Ink, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.movePhoto(page.id, -1) }, enabled = index > 0 && !busy, modifier = Modifier.semantics { contentDescription = "Move page earlier" }) { Text("↑") }
                        TextButton(onClick = { vm.movePhoto(page.id, 1) }, enabled = index < pages.lastIndex && !busy, modifier = Modifier.semantics { contentDescription = "Move page later" }) { Text("↓") }
                    }
                    FileImage(page.path, Modifier.fillMaxWidth().height(235.dp).clip(RoundedCornerShape(10.dp)).background(Paper).clickable { original = page })
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { cropping = page }, enabled = !busy) { Text("Crop") }
                        TextButton(onClick = { vm.rotatePhoto(page.id) }, enabled = !busy) { Text("Rotate") }
                        TextButton(onClick = { original = page }) { Text("Original") }
                        TextButton(onClick = { removing = page }, enabled = !busy) { Text("Remove") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(onClick = { if (installed) vm.transcribePage(page.id, false) else onSettings() }, enabled = !busy) { Text("AI text + math") }
                        TextButton(onClick = { vm.transcribePage(page.id, true) }, enabled = !busy) { Text("Basic text") }
                    }
                }
            }
        }
    }
    cropping?.let { page ->
        CropDialog(page, { cropping = null }) { left, top, right, bottom ->
            cropping = null; vm.cropPhoto(page.id, left, top, right, bottom)
        }
    }
    original?.let { page ->
        AlertDialog(onDismissRequest = { original = null }, title = { Text("Original photo") },
            text = { FileImage(page.originalPath, Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 450.dp)) },
            confirmButton = { TextButton(onClick = { original = null }) { Text("Close") } })
    }
    removing?.let { page ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remove this photo?") }, text = { Text("Text already transcribed into your document will stay.") },
            confirmButton = { TextButton(onClick = { vm.removePhoto(page.id); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } })
    }
}

@Composable
private fun SettingsScreen(
    model: ModelStatus, onBack: () -> Unit, onDownload: () -> Unit,
    onCancelDownload: () -> Unit, onDelete: () -> Unit, onImport: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) { Text("‹ Back") }
        Text("Your private\nstudy assistant.", fontFamily = FontFamily.Serif, fontSize = 34.sp, lineHeight = 39.sp, color = Ink, modifier = Modifier.padding(top = 18.dp))
        Text("LOCAL AI SETUP", style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp, color = Muted, modifier = Modifier.padding(top = 17.dp, bottom = 22.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Sage), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp)) {
                Text("Gemma 4 E2B", style = MaterialTheme.typography.headlineSmall, color = Ink)
                Text("Reads photos, formats equations, and suggests edits.", color = Muted, modifier = Modifier.padding(top = 6.dp))
                Text("About 2.6 GB · Android ARM64 · 8 GB+ RAM recommended", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 14.dp))
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text(when { model.downloading -> "Downloading model"; model.loading -> "Loading local AI"; model.ready -> "Ready · ${model.backend}"; model.installed -> "Model installed"; model.downloadedBytes > 0 -> "Download paused"; else -> "Ready to set up" }, fontWeight = FontWeight.SemiBold, color = Ink)
                if (model.downloading || model.downloadedBytes > 0 && !model.installed) {
                    val progress = if (model.totalBytes > 0) (model.downloadedBytes.toFloat() / model.totalBytes).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                    Text("${bytesLabel(model.downloadedBytes)} / ${if (model.totalBytes > 0) bytesLabel(model.totalBytes) else "2.6 GB"}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                }
                model.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 10.dp)) }
                Spacer(Modifier.height(17.dp))
                when {
                    model.downloading -> OutlinedButton(onClick = onCancelDownload) { Text("Pause download") }
                    !model.installed -> Button(onClick = onDownload, enabled = !model.loading) { Text(if (model.downloadedBytes > 0) "Resume download" else "Download model") }
                    else -> OutlinedButton(onClick = { confirmDelete = true }, enabled = !model.loading) { Text("Remove model") }
                }
                if (!model.downloading && !model.installed) TextButton(onClick = onImport, enabled = !model.loading) { Text("Import a .litertlm model file") }
            }
        }
        Text("Works offline after setup", style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.padding(top = 24.dp))
        Text("The download needs an internet connection. Your photos and notes are processed on this phone. AI speed depends on your device; use clear images and one page at a time.", color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        Text("You can start before downloading", style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.padding(top = 22.dp))
        Text("Basic printed-text recognition, manual editing, previews, and exports are available without the large model. Basic recognition does not reconstruct mathematical equations.", color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        Text("About this build", style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.padding(top = 22.dp))
        Text("Personal Android app · Gemma via Google LiteRT-LM\nEquation previews: KaTeX · Word math: mathml2omml\nOpen-source notices and bundled library sources are included with the project.", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp, bottom = 28.dp))
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Remove AI model?") }, text = { Text("This frees the model storage. Your documents and photos stay saved.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CropDialog(page: PhotoPage, onDismiss: () -> Unit, onCrop: (Float, Float, Float, Float) -> Unit) {
    var horizontal by remember(page.id) { mutableStateOf(0f..1f) }
    var vertical by remember(page.id) { mutableStateOf(0f..1f) }
    val bitmap by rememberFileBitmap(page.path)
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Crop photo") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(230.dp).background(Paper)) {
                    bitmap?.let { image ->
                        Image(image.asImageBitmap(), "Crop preview", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        Canvas(Modifier.matchParentSize()) {
                            val scale = min(size.width / image.width, size.height / image.height)
                            val width = image.width * scale
                            val height = image.height * scale
                            val x = (size.width - width) / 2
                            val y = (size.height - height) / 2
                            drawRect(Ink, Offset(x + horizontal.start * width, y + vertical.start * height), Size((horizontal.endInclusive - horizontal.start) * width, (vertical.endInclusive - vertical.start) * height), style = Stroke(3.dp.toPx()))
                        }
                    }
                }
                Text("Left / right edges", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                RangeSlider(value = horizontal, onValueChange = { if (it.endInclusive - it.start >= .05f) horizontal = it }, valueRange = 0f..1f)
                Text("Top / bottom edges", style = MaterialTheme.typography.labelLarge)
                RangeSlider(value = vertical, onValueChange = { if (it.endInclusive - it.start >= .05f) vertical = it }, valueRange = 0f..1f)
                Text("The original photo is kept for reference.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        },
        confirmButton = { Button(onClick = { onCrop(horizontal.start, vertical.start, horizontal.endInclusive, vertical.endInclusive) }, enabled = bitmap != null) { Text("Apply crop") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BusyBanner(label: String, partial: String, cancellable: Boolean, onCancel: () -> Unit) {
    Surface(color = Sage) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label.ifBlank { "Working on your document" }, style = MaterialTheme.typography.labelLarge, color = Ink, modifier = Modifier.weight(1f))
                if (cancellable) TextButton(onClick = onCancel) { Text("Cancel") }
            }
            LinearProgressIndicator(Modifier.fillMaxWidth())
            if (partial.isNotBlank()) Text(partial.takeLast(500), modifier = Modifier.padding(top = 8.dp).heightIn(max = 70.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SelectionContainerText(value: String) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        Text(value, modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FileImage(path: String, modifier: Modifier) {
    val bitmap by rememberFileBitmap(path)
    Box(modifier, contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), "Saved page photo", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            ?: Text("Loading photo…", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun rememberFileBitmap(path: String): State<Bitmap?> = produceState<Bitmap?>(null, path) {
    value = withContext(Dispatchers.IO) {
        runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            var sample = 1
            while (maxOf(options.outWidth, options.outHeight) / sample > 1400) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }
}

private fun safeName(title: String): String = title.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(80).ifEmpty { "Document" }
private fun dateLabel(time: Long): String = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(time))
private fun bytesLabel(value: Long): String = if (value >= 1_000_000_000) String.format(Locale.US, "%.2f GB", value / 1_000_000_000.0) else String.format(Locale.US, "%.0f MB", value / 1_000_000.0)
