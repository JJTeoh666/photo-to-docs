package com.jjteo.phototodocs.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.jjteo.phototodocs.R
import com.jjteo.phototodocs.AppViewModel
import com.jjteo.phototodocs.ExportFormat
import com.jjteo.phototodocs.ai.ModelStatus
import com.jjteo.phototodocs.ai.AiProvider
import com.jjteo.phototodocs.data.NoteDocument
import com.jjteo.phototodocs.data.PhotoFiles
import com.jjteo.phototodocs.data.PhotoPage
import com.jjteo.phototodocs.documents.DocumentPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

private val Ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val Paper: Color @Composable get() = MaterialTheme.colorScheme.background
private val Sage: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
private val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

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

    val darkTheme = state.themeMode.isDark(isSystemInDarkTheme())
    PhotoToDocsTheme(darkTheme = darkTheme) {
        ThemeSystemBars(darkTheme)
        Box(Modifier.fillMaxSize().testTag("app-root")) {
            Surface(Modifier.fillMaxSize(), color = Paper) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                if (state.busy) BusyBanner(state.busyLabel, state.partial, !state.busyLabel.startsWith("Importing"), vm::cancelTask)
                when {
                    settings -> SettingsScreen(
                        themeMode = state.themeMode,
                        onThemeModeChange = vm::setThemeMode,
                        model = state.model,
                        updates = state.updates,
                        onCheckUpdates = vm::checkForUpdates,
                        onOpenUpdates = vm::openUpdatePage,
                        onBack = { settings = false },
                        onDownload = vm::downloadModel,
                        onCancelDownload = vm::cancelModelDownload,
                        onDelete = vm::deleteModel,
                        onImport = { modelImport.launch(arrayOf("*/*")) },
                        provider = state.aiProvider,
                        aiSettings = { ChatGptSettings(vm, state.aiProvider, state.chatGpt) },
                    )
                    state.current == null -> LibraryScreen(
                        documents = state.documents, loading = state.loading,
                        onNew = vm::createDocument, onOpen = vm::openDocument,
                        onDelete = vm::deleteDocument, onCamera = cameraAction,
                        onGallery = galleryAction, onSettings = { settings = true },
                        model = state.model,
                        aiLabel = if (state.aiProvider == AiProvider.CHATGPT) "ChatGPT is selected" else null,
                        aiDetail = if (state.aiProvider == AiProvider.CHATGPT) if (state.aiReady) "AI requests use your ChatGPT plan and internet." else "Connect your account and choose a model in Settings." else null,
                    )
                    else -> DocumentScreen(
                        document = state.current!!, vm = vm, busy = state.busy,
                        model = state.model, onCamera = cameraAction,
                        onGallery = galleryAction, onSettings = { settings = true },
                        aiReady = state.aiReady, aiDisclosure = state.aiDisclosure,
                    )
                    }
                }
            }
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
                        Text("Original", style = MaterialTheme.typography.labelSmall, color = Muted)
                        Text(proposal.oldText.ifBlank { "(empty)" }, modifier = Modifier.padding(vertical = 8.dp))
                        HorizontalDivider()
                        Text("Suggested changes", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelSmall, color = Ink)
                        SelectionContainerText(proposal.newText)
                        Text("Your saved document changes only when you accept.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                },
                confirmButton = { Button(onClick = vm::acceptProposal) { ActionLabel(R.drawable.ic_check, "Accept changes") } },
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
@OptIn(ExperimentalLayoutApi::class)
private fun DocumentScreen(
    document: NoteDocument, vm: AppViewModel, busy: Boolean, model: ModelStatus,
    onCamera: () -> Unit, onGallery: () -> Unit, onSettings: () -> Unit,
    aiReady: Boolean, aiDisclosure: String,
) {
    var tab by rememberSaveable(document.id) { mutableStateOf(if (document.pages.isNotEmpty() && document.source.isBlank()) 2 else 0) }
    var field by remember(document.id) { mutableStateOf(TextFieldValue(document.source)) }
    var editorRevision by remember(document.id) { mutableLongStateOf(document.revision) }
    var showAi by remember { mutableStateOf(false) }
    var instruction by rememberSaveable { mutableStateOf("") }
    var exportMenu by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    val wordSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) { uri -> if (uri != null) vm.export(ExportFormat.WORD, uri) }
    val texSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-tex")) { uri -> if (uri != null) vm.export(ExportFormat.LATEX, uri) }
    LaunchedEffect(document.revision) {
        // Own keystrokes already updated the field. Only sync newer external edits
        // (undo/AI); an older composition must never replace more recent input.
        if (document.revision > editorRevision) {
            editorRevision = document.revision
            if (field.text != document.source) field = TextFieldValue(document.source, TextRange(field.selection.end.coerceAtMost(document.source.length)))
        }
    }
    LaunchedEffect(document.pages.size) {
        if (document.pages.isNotEmpty() && document.source.isBlank()) tab = 2
    }
    fun change(value: TextFieldValue) {
        field = value
        vm.updateSource(value.text)
        vm.state.value.current?.takeIf { it.id == document.id }?.let { editorRevision = it.revision }
    }
    fun wrap(before: String, after: String = before) {
        val start = field.selection.min
        val end = field.selection.max
        val text = field.text.replaceRange(start, end, before + field.text.substring(start, end) + after)
        change(TextFieldValue(text, TextRange(start + before.length, end + before.length)))
    }
    var showFormatting by rememberSaveable(document.id) { mutableStateOf(false) }
    var compactMenu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var titleFocused by remember { mutableStateOf(false) }
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    ContentFrame(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < if (largeText) 680.dp else 480.dp
            val veryCompact = maxHeight < 280.dp
            val typingOnly = maxHeight < 160.dp && WindowInsets.isImeVisible && tab == 0 && !titleFocused
            Column(Modifier.fillMaxSize()) {
                if (!typingOnly) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::closeDocument) { AppIcon(R.drawable.ic_arrow_back, description = "Library") }
                    if (compact && !titleFocused) IconButton(onClick = { rename = true }) { AppIcon(R.drawable.ic_document, description = "Rename document") }
                    Spacer(Modifier.weight(1f))
                    if (veryCompact) Box {
                        IconButton(onClick = { compactMenu = true }) { AppIcon(R.drawable.ic_list, description = "Document views and formatting") }
                        DropdownMenu(expanded = compactMenu, onDismissRequest = { compactMenu = false }) {
                            listOf("Edit", "Preview", "Photos").forEachIndexed { index, name ->
                                DropdownMenuItem(text = { Text(name) }, onClick = { tab = index; compactMenu = false })
                            }
                            if (tab == 0) {
                                HorizontalDivider()
                                listOf("Bold" to ("**" to "**"), "Italic" to ("*" to "*"), "Heading" to ("\n## " to "\n"), "Bulleted list" to ("\n- " to "\n"), "Insert equation" to ("\n\$\$\n" to "\n\$\$\n")).forEach { (label, syntax) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = { wrap(syntax.first, syntax.second); compactMenu = false })
                                }
                                DropdownMenuItem(text = { Text("Undo") }, onClick = { vm.undo(); compactMenu = false })
                            }
                        }
                    }
                    if (tab != 1) TextButton(onClick = { showAi = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("ai-edit")) {
                        if (!largeText) { AppIcon(R.drawable.ic_ai); Spacer(Modifier.width(8.dp)) }
                        Text("AI edit")
                    }
                    Box {
                        FilledTonalButton(onClick = { exportMenu = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("export-document")) {
                            if (!largeText) { AppIcon(R.drawable.ic_export); Spacer(Modifier.width(8.dp)) }
                            Text("Export")
                        }
                        DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                            DropdownMenuItem(text = { Text("Save PDF (.pdf)") }, onClick = { exportMenu = false; activity?.let(vm::exportPdf) }, enabled = !busy && activity != null)
                            DropdownMenuItem(text = { Text("Save Word (.docx)") }, onClick = { exportMenu = false; wordSave.launch(safeName(document.title) + ".docx") }, enabled = !busy)
                            DropdownMenuItem(text = { Text("Save LaTeX (.tex)") }, onClick = { exportMenu = false; texSave.launch(safeName(document.title) + ".tex") }, enabled = !busy)
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Share Word") }, onClick = { exportMenu = false; vm.shareExport(ExportFormat.WORD) }, enabled = !busy)
                            DropdownMenuItem(text = { Text("Share LaTeX") }, onClick = { exportMenu = false; vm.shareExport(ExportFormat.LATEX) }, enabled = !busy)
                        }
                    }
                }
                if (!compact || titleFocused) {
                    OutlinedTextField(
                        value = document.title, onValueChange = vm::renameDocument,
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp).onFocusChanged { titleFocused = it.isFocused }.testTag("document-title"),
                        singleLine = true, label = { Text("Document title") },
                        textStyle = MaterialTheme.typography.headlineSmall,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = MaterialTheme.shapes.medium,
                    )
                }
                if (!veryCompact) SecondaryTabRow(selectedTabIndex = tab, containerColor = Paper, contentColor = MaterialTheme.colorScheme.primary) {
                    listOf("Edit", "Preview", "Photos").forEachIndexed { index, name ->
                        Tab(selected = tab == index, onClick = { tab = index }, modifier = Modifier.heightIn(min = 48.dp),
                            selectedContentColor = MaterialTheme.colorScheme.primary, unselectedContentColor = Muted) {
                            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp))
                        }
                    }
                }
                when (tab) {
                    0 -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp, vertical = if (typingOnly) 4.dp else 12.dp),
                            color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            TextField(
                                value = field, onValueChange = { change(it) },
                                modifier = Modifier.fillMaxSize().semantics { contentDescription = "Document text" }.testTag("document-source"),
                                placeholder = { Text("Write a note, or add a photo in Photos.\n\nUse **bold**, # headings, and LaTeX between \$…\$ or \$\$…\$\$.") },
                                textStyle = MaterialTheme.typography.bodyLarge,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                ),
                            )
                        }
                        if ((!compact || showFormatting) && !veryCompact) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { wrap("**") }) { AppIcon(R.drawable.ic_bold, description = "Bold") }
                                IconButton(onClick = { wrap("*") }) { AppIcon(R.drawable.ic_italic, description = "Italic") }
                                IconButton(onClick = { wrap("\n## ", "\n") }) { AppIcon(R.drawable.ic_heading, description = "Heading") }
                                IconButton(onClick = { wrap("\n- ", "\n") }) { AppIcon(R.drawable.ic_list, description = "Bulleted list") }
                                IconButton(onClick = { wrap("\n\$\$\n", "\n\$\$\n") }) { AppIcon(R.drawable.ic_math, description = "Insert equation") }
                                if (!compact) TextButton(onClick = vm::undo, modifier = Modifier.heightIn(min = 48.dp)) { ActionLabel(R.drawable.ic_undo, "Undo") }
                            }
                        }
                        if (compact && !veryCompact) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = { showFormatting = !showFormatting }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (showFormatting) "Hide formatting" else "Formatting") }
                                TextButton(onClick = vm::undo, modifier = Modifier.heightIn(min = 48.dp)) { Text("Undo") }
                            }
                        } else if (!compact) {
                            FlowRow(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (field.selection.collapsed) "Saves automatically" else "${field.selection.max - field.selection.min} characters selected", style = MaterialTheme.typography.labelSmall, color = Muted)
                                Text("${document.source.length} characters", style = MaterialTheme.typography.labelSmall, color = Muted)
                            }
                        }
                    }
                    1 -> {
                        DocumentPreview(document.source, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).clip(MaterialTheme.shapes.medium).testTag("document-preview"))
                        PreviewAiAction(onClick = { showAi = true }, enabled = !busy,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp).testTag("ai-edit-preview"))
                    }
                    2 -> PhotosPanel(document.pages, vm, busy, aiReady, onCamera, onGallery, onSettings, Modifier.weight(1f), aiDisclosure)
                }
            }
        }
    }
    if (rename) AlertDialog(onDismissRequest = { rename = false }, title = { Text("Document title") },
        text = { OutlinedTextField(document.title, vm::renameDocument, singleLine = true, modifier = Modifier.testTag("document-title"), label = { Text("Title") }) },
        confirmButton = { TextButton(onClick = { rename = false }) { Text("Done") } })
    if (showAi) {
        AlertDialog(
            onDismissRequest = { showAi = false }, title = { Text("Edit with AI") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(if (field.selection.collapsed) "Revise the whole document." else "Revise the selected text.", color = Muted)
                    Text("AI suggests changes for you to review.", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 4.dp))
                    Text(aiDisclosure, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 8.dp))
                    OutlinedTextField(instruction, { instruction = it }, label = { Text("What should AI change?") }, placeholder = { Text("Clean up the formatting and keep the meaning.") }, minLines = 3, modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
                    TextButton(onClick = { instruction = "Fix spelling and improve clarity. Preserve every fact, number, and equation." }) { Text("Improve writing") }
                    TextButton(onClick = { instruction = "Format the equations as valid LaTeX. Preserve their mathematical meaning and all surrounding text." }) { Text("Format equations") }
                    if (!aiReady) Text("Set up local AI or connect ChatGPT in Settings to suggest changes.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                if (aiReady) Button(enabled = instruction.isNotBlank() && !busy, onClick = {
                    val start = if (field.selection.collapsed) 0 else field.selection.min
                    val end = if (field.selection.collapsed) -1 else field.selection.max
                    showAi = false
                    vm.requestEdit(instruction.trim(), start, end)
                }) { Text("Suggest changes") }
                else Button(onClick = { showAi = false; onSettings() }) { Text("Set up AI") }
            },
            dismissButton = { TextButton(onClick = { showAi = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PhotosPanel(
    pages: List<PhotoPage>, vm: AppViewModel, busy: Boolean, installed: Boolean,
    onCamera: () -> Unit, onGallery: () -> Unit, onSettings: () -> Unit, modifier: Modifier,
    aiDisclosure: String,
) {
    var cropping by remember { mutableStateOf<PhotoPage?>(null) }
    var removing by remember { mutableStateOf<PhotoPage?>(null) }
    var original by remember { mutableStateOf<PhotoPage?>(null) }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCamera, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { ActionLabel(R.drawable.ic_camera, "Take photo") }
                OutlinedButton(onClick = onGallery, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { ActionLabel(R.drawable.ic_image, "Add images") }
            }
        }
        if (pages.isEmpty()) item {
            Column(Modifier.padding(vertical = 24.dp)) {
                Text("Add your first photo", style = MaterialTheme.typography.titleLarge)
                Text("Photograph a clear, flat page. Crop around a single equation for the best results.", color = Muted, modifier = Modifier.padding(top = 10.dp))
            }
        }
        if (pages.isNotEmpty()) item {
            Column {
                Text("Transcribe your photos", style = MaterialTheme.typography.titleMedium)
                Text("Transcriptions are drafts. Check symbols and numbers against the photo.", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 4.dp))
                Text(aiDisclosure, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 4.dp))
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Button(onClick = { if (installed) vm.transcribeAll(false) else onSettings() }, enabled = !busy) { Text("AI: all pages") }
                    TextButton(onClick = { vm.transcribeAll(true) }, enabled = !busy) { Text("Basic text: all") }
                }
            }
        }
        items(pages, key = { it.id }) { page ->
            val index = pages.indexOfFirst { it.id == page.id }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Page ${index + 1}", style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.movePhoto(page.id, -1) }, enabled = index > 0 && !busy, modifier = Modifier.semantics { contentDescription = "Move page earlier" }) { AppIcon(R.drawable.ic_arrow_up) }
                        TextButton(onClick = { vm.movePhoto(page.id, 1) }, enabled = index < pages.lastIndex && !busy, modifier = Modifier.semantics { contentDescription = "Move page later" }) { AppIcon(R.drawable.ic_arrow_down) }
                    }
                    FileImage(page.path, Modifier.fillMaxWidth().height(235.dp).clip(RoundedCornerShape(10.dp)).background(Paper).clickable { original = page })
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { cropping = page }, enabled = !busy) { ActionLabel(R.drawable.ic_crop, "Crop") }
                        TextButton(onClick = { vm.rotatePhoto(page.id) }, enabled = !busy) { ActionLabel(R.drawable.ic_rotate, "Rotate") }
                        TextButton(onClick = { original = page }) { Text("Original") }
                        TextButton(onClick = { removing = page }, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Remove") }
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
@OptIn(ExperimentalMaterial3Api::class)
private fun CropDialog(page: PhotoPage, onDismiss: () -> Unit, onCrop: (Float, Float, Float, Float) -> Unit) {
    var horizontal by remember(page.id) { mutableStateOf(0f..1f) }
    var vertical by remember(page.id) { mutableStateOf(0f..1f) }
    val bitmap by rememberFileBitmap(page.path)
    val cropColor = MaterialTheme.colorScheme.primary
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
                            drawRect(cropColor, Offset(x + horizontal.start * width, y + vertical.start * height), Size((horizontal.endInclusive - horizontal.start) * width, (vertical.endInclusive - vertical.start) * height), style = Stroke(3.dp.toPx()))
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
