package com.jjteo.phototodocs

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jjteo.phototodocs.ai.LocalAi
import com.jjteo.phototodocs.ai.AiProvider
import com.jjteo.phototodocs.ai.DocumentAi
import com.jjteo.phototodocs.ai.AiDocumentText
import com.jjteo.phototodocs.ai.ModelStatus
import com.jjteo.phototodocs.chatgpt.ChatGptAi
import com.jjteo.phototodocs.chatgpt.ChatGptConnection
import com.jjteo.phototodocs.chatgpt.ChatGptUiState
import com.jjteo.phototodocs.data.DocumentStore
import com.jjteo.phototodocs.data.AppearancePreferences
import com.jjteo.phototodocs.data.ThemeMode
import com.jjteo.phototodocs.data.NoteDocument
import com.jjteo.phototodocs.data.PhotoFiles
import com.jjteo.phototodocs.data.PhotoPage
import com.jjteo.phototodocs.documents.DocumentExporter
import com.jjteo.phototodocs.documents.convertEquations
import com.jjteo.phototodocs.documents.printPdf
import com.jjteo.phototodocs.updates.AppVersion
import com.jjteo.phototodocs.updates.GitHubUpdates
import com.jjteo.phototodocs.updates.UpdateStatus
import com.jjteo.phototodocs.updates.UpdateUiState
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

typealias EditProposal = com.jjteo.phototodocs.data.EditProposal

enum class ExportFormat(val extension: String, val mime: String) {
    LATEX("tex", "application/x-tex"),
    WORD("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
}

data class AppUiState(
    val documents: List<NoteDocument> = emptyList(),
    val current: NoteDocument? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val busyLabel: String = "",
    val partial: String = "",
    val proposal: EditProposal? = null,
    val error: String? = null,
    val model: ModelStatus = ModelStatus(),
    val updates: UpdateUiState = UpdateUiState(BuildConfig.VERSION_NAME),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val aiProvider: AiProvider = AiProvider.LOCAL,
    val chatGpt: ChatGptUiState = ChatGptUiState(),
) {
    val aiReady: Boolean get() = if (aiProvider == AiProvider.LOCAL) model.installed else chatGpt.ready
    val aiDisclosure: String get() = if (aiProvider == AiProvider.LOCAL) "Local AI · processed on this phone" else "ChatGPT · submitted photos and text are sent to OpenAI"
}

class AppViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application
    private val store = DocumentStore(File(app.filesDir, "documents"))
    private val ai = LocalAi(app)
    private val chatGpt = ChatGptConnection.get(app)
    private val cloudAi = ChatGptAi(chatGpt)
    private val aiPreferences = app.getSharedPreferences("ai-provider", android.content.Context.MODE_PRIVATE)
    private val appearance = AppearancePreferences(app)
    private val _state = MutableStateFlow(AppUiState(themeMode = appearance.read(),
        aiProvider = if (aiPreferences.getString("provider", "LOCAL") == "CHATGPT") AiProvider.CHATGPT else AiProvider.LOCAL))
    val state = _state.asStateFlow()
    private val saveQueue = Channel<NoteDocument>(Channel.UNLIMITED)
    // Outlives activity/ViewModel teardown long enough to drain accepted edits to disk.
    private val persistence = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val diskMutex = Mutex()
    private val photoMutex = Mutex()
    private val deleted = mutableSetOf<String>()
    private val undoHistory = mutableMapOf<String, ArrayDeque<String>>()
    private var task: Job? = null
    private var modelJob: Job? = null
    private var updateJob: Job? = null
    private var chatJob: Job? = null
    @Volatile private var taskSerial = 0L
    private var pendingImports = 0

    init {
        viewModelScope.launch { chatGpt.state.collect { value -> _state.update { it.copy(chatGpt = value) } } }
        viewModelScope.launch {
            try { chatGpt.initialize() }
            catch (_: CancellationException) { }
            catch (e: Exception) { chatGpt.reportFailure(e) }
        }
        viewModelScope.launch {
            ai.state.collect { model -> _state.update { it.copy(model = model) } }
        }
        viewModelScope.launch {
            try {
                val notes = withContext(Dispatchers.IO) { store.load() }
                val currentId: String? = saved["documentId"]
                _state.update { it.copy(documents = notes, current = notes.find { note -> note.id == currentId }, loading = false,
                    error = if (store.loadErrors.isNotEmpty()) "Some saved notes could not be read. Their files have been kept for recovery." else null) }
            } catch (e: Exception) { notifyError("Could not read the library: ${e.message}"); _state.update { it.copy(loading = false) } }
        }
        persistence.launch {
            try {
            for (first in saveQueue) {
                val latest = linkedMapOf(first.id to first)
                while (true) {
                    val next = saveQueue.tryReceive().getOrNull() ?: break
                    latest[next.id] = next
                }
                for (note in latest.values) {
                    try { diskMutex.withLock { if (note.id !in deleted) store.save(note) } }
                    catch (e: Exception) { notifyError("Autosave failed: ${e.message}. Keep this note open and retry after freeing storage.") }
                }
            }
            } finally { persistence.cancel() }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        if (_state.value.themeMode == mode) return
        appearance.write(mode)
        _state.update { it.copy(themeMode = mode) }
    }

    fun setAiProvider(provider: AiProvider) {
        if (_state.value.aiProvider == provider) return
        cancelTask()
        aiPreferences.edit().putString("provider", provider.name).apply()
        _state.update { it.copy(aiProvider = provider, proposal = null) }
        if (provider == AiProvider.CHATGPT) viewModelScope.launch {
            try { ai.unloadEngine() } catch (_: CancellationException) { } catch (_: Exception) { notifyError("Local AI could not release its engine. Restart the app to free its memory.") }
        }
    }
    private fun activeAi(): DocumentAi = if (_state.value.aiProvider == AiProvider.LOCAL) ai else cloudAi
    private fun requireAi(): Boolean {
        if (_state.value.aiReady) return true
        notifyError(if (_state.value.aiProvider == AiProvider.LOCAL) "Download or import the local model in Local AI setup first. Basic printed-text extraction is available immediately."
            else "Connect ChatGPT, enable plan use, and choose a model in Settings first.")
        return false
    }
    private fun chatAction(block: suspend () -> Unit) {
        if (chatJob?.isCompleted == false) return
        chatGpt.setWorking(true)
        chatJob = viewModelScope.launch {
            try { chatGpt.initialize(); block() }
            catch (_: CancellationException) { chatGpt.cancelled() }
            catch (e: Exception) { if (currentCoroutineContext().isActive) chatGpt.reportFailure(e) else chatGpt.cancelled() }
            finally { chatGpt.setWorking(false) }
        }
    }
    fun signInChatGpt(addAccount: Boolean = false) {
        cancelTask()
        _state.update { it.copy(proposal = null) }
        chatAction {
            val id = if (addAccount) null else _state.value.chatGpt.selectedId
            chatGpt.signIn(id) { url ->
                app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
    fun cancelChatGpt() { chatJob?.cancel(); chatGpt.cancelSignIn() }
    fun loadChatGptModels() = chatAction { chatGpt.loadModels() }
    fun selectChatGptAccount(id: String) { cancelTask(); _state.update { it.copy(proposal = null) }; chatAction { chatGpt.selectAccount(id) } }
    fun selectChatGptModel(slug: String) { cancelTask(); _state.update { it.copy(proposal = null) }; chatAction { chatGpt.selectModel(slug) } }
    fun testChatGpt() = chatAction { chatGpt.testConnection() }
    fun disconnectChatGpt() { cancelTask(); _state.update { it.copy(proposal = null) }; chatAction { chatGpt.disconnect() } }
    fun manageChatGptUsage() {
        try { app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/settings/usage")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { notifyError("Open ChatGPT Settings → Usage in your browser to manage app access and usage.") }
    }

    fun createDocument() {
        if (_state.value.loading) return
        if (task?.isActive == true) cancelTask()
        val note = NoteDocument()
        saved["documentId"] = note.id
        _state.update { it.copy(documents = listOf(note) + it.documents, current = note, proposal = null) }
        saveQueue.trySend(note)
    }

    fun openDocument(id: String) {
        if (task?.isActive == true) cancelTask()
        val note = _state.value.documents.find { it.id == id } ?: return
        saved["documentId"] = id
        _state.update { it.copy(current = note, proposal = null) }
    }

    fun closeDocument() {
        if (task?.isActive == true) cancelTask()
        saved["documentId"] = null
        _state.update { it.copy(current = null, proposal = null) }
    }

    private fun change(id: String, transform: (NoteDocument) -> NoteDocument): NoteDocument? {
        val before = _state.value.documents.find { it.id == id } ?: return null
        val after = transform(before).copy(revision = before.revision + 1, updatedAt = System.currentTimeMillis())
        _state.update { state -> state.copy(documents = state.documents.map { if (it.id == id) after else it }.sortedByDescending { it.updatedAt },
            current = if (state.current?.id == id) after else state.current) }
        saveQueue.trySend(after)
        return after
    }

    fun renameDocument(title: String) { _state.value.current?.let { note -> change(note.id) { it.copy(title = title.take(200)) } } }

    fun updateSource(source: String) {
        val before = _state.value.current ?: return
        if (before.source == source) return
        val history = undoHistory.getOrPut(before.id) { ArrayDeque() }
        history.addLast(before.source)
        while (history.size > 80) history.removeFirst()
        change(before.id) { it.copy(source = source) }
    }

    fun undo() {
        val note = _state.value.current ?: return
        val source = undoHistory[note.id]?.removeLastOrNull() ?: return
        change(note.id) { it.copy(source = source) }
    }

    fun deleteDocument(id: String) {
        if (_state.value.busy) { notifyError("Wait for the current task to finish before deleting a note."); return }
        val note = _state.value.documents.find { it.id == id } ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { diskMutex.withLock { store.delete(id); deleted += id } }
                if (_state.value.current?.id == id) saved["documentId"] = null
                _state.update { it.copy(documents = it.documents.filterNot { note -> note.id == id }, current = it.current?.takeUnless { note -> note.id == id }, proposal = null) }
                undoHistory.remove(id)
                withContext(Dispatchers.IO) {
                    // Every imported page has its own UUID. Only remove files in our photo directory.
                    val ids = note.pages.map { it.id }
                    File(app.filesDir, "photos").listFiles().orEmpty().filter { file ->
                        ids.any { pageId -> file.name.startsWith("$pageId-") || file.name == "$pageId.jpg" }
                    }.forEach { it.delete() }
                }
            } catch (e: Exception) { notifyError("Could not delete note: ${e.message}") }
        }
    }

    fun importPhoto(uri: Uri) {
        if (_state.value.loading) { notifyError("The library is still loading. Please choose the photo again in a moment."); return }
        if (_state.value.busy && pendingImports == 0) { notifyError("Finish or cancel the current task before adding photos."); return }
        if (_state.value.current == null) createDocument()
        val id = _state.value.current!!.id
        pendingImports++
        _state.update { it.copy(busy = true, busyLabel = "Importing photos…") }
        viewModelScope.launch {
            try {
                photoMutex.withLock {
                    val page = withContext(Dispatchers.IO) { PhotoFiles.import(app, uri) }
                    change(id) { it.copy(pages = it.pages + page) }
                }
            } catch (e: Exception) { notifyError("Could not import photo: ${e.message}") }
            finally {
                pendingImports--
                if (pendingImports == 0) _state.update { it.copy(busy = false, busyLabel = "") }
            }
        }
    }

    fun rotatePhoto(pageId: String) = modifyPhoto(pageId) { PhotoFiles.rotate(it) }
    fun cropPhoto(pageId: String, left: Float, top: Float, right: Float, bottom: Float) = modifyPhoto(pageId) { PhotoFiles.crop(it, left, top, right, bottom) }

    private fun modifyPhoto(pageId: String, transform: (PhotoPage) -> PhotoPage) {
        val note = _state.value.current ?: return
        val page = note.pages.find { it.id == pageId } ?: return
        runTask("Preparing photo…") {
            val edited = withContext(Dispatchers.IO) { transform(page) }
            val changed = change(note.id) { it.copy(pages = it.pages.map { old -> if (old.id == pageId) edited else old }) }
            if (changed != null) saveAndCleanPhotos(changed, pageId, setOf(edited.path, edited.originalPath), setOf(page.path))
        }
    }

    fun removePhoto(pageId: String) {
        if (_state.value.busy) return
        _state.value.current?.let { note ->
            if (note.pages.none { it.id == pageId }) return
            val changed = change(note.id) { it.copy(pages = it.pages.filterNot { page -> page.id == pageId }) }
            if (changed != null) saveAndCleanPhotos(changed, pageId, emptySet())
        }
    }

    private fun saveAndCleanPhotos(note: NoteDocument, pageId: String, preserve: Set<String>, obsolete: Set<String>? = null) {
        persistence.launch {
            try {
                diskMutex.withLock {
                    if (note.id !in deleted) store.save(note)
                    val folder = File(app.filesDir, "photos").canonicalFile
                    folder.listFiles().orEmpty().filter { file ->
                        file.canonicalFile.parentFile == folder && file.absolutePath !in preserve &&
                            (obsolete == null || file.absolutePath in obsolete) &&
                            (file.name.startsWith("$pageId-") || file.name == "$pageId.jpg")
                    }.forEach { it.delete() }
                }
            } catch (e: Exception) { notifyError("Photo cleanup could not finish: ${e.message}") }
        }
    }

    fun movePhoto(pageId: String, offset: Int) {
        if (_state.value.busy) return
        _state.value.current?.let { note ->
            val pages = note.pages.toMutableList()
            val index = pages.indexOfFirst { it.id == pageId }
            val target = index + offset
            if (index >= 0 && target in pages.indices) {
                val page = pages.removeAt(index); pages.add(target, page)
                change(note.id) { it.copy(pages = pages) }
            }
        }
    }

    fun transcribePage(pageId: String, basicOnly: Boolean = false) {
        val note = _state.value.current ?: return
        val page = note.pages.find { it.id == pageId } ?: return
        transcribe(note, listOf(page), basicOnly)
    }

    fun transcribeAll(basicOnly: Boolean = false) {
        val note = _state.value.current ?: return
        if (note.pages.isEmpty()) { notifyError("Add a photo first."); return }
        transcribe(note, note.pages, basicOnly)
    }

    private fun transcribe(note: NoteDocument, pages: List<PhotoPage>, basicOnly: Boolean) {
        if (!basicOnly && !requireAi()) return
        val provider = _state.value.aiProvider
        val engine = activeAi()
        runTask(if (basicOnly) "Extracting printed text…" else "${provider.label}: reading text and equations…", if (basicOnly) null else provider) {
            val generation = taskSerial
            val texts = mutableListOf<String>()
            pages.forEachIndexed { index, page ->
                _state.update { it.copy(busyLabel = "Reading page ${index + 1} of ${pages.size}…") }
                val ocr = if (basicOnly) recognize(page) else try { recognize(page) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { "" }
                val text = if (basicOnly) ocr else {
                    // The original stays untouched; vision input is capped to keep memory bounded.
                    val visionFile = withContext(Dispatchers.IO) {
                        val temp = File.createTempFile("vision-", ".jpg", app.cacheDir)
                        val bitmap = PhotoFiles.readBitmap(File(page.path), 1600)
                        try { temp.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 94, it) } }
                        finally { bitmap.recycle() }
                        temp
                    }
                    try { engine.transcribe(visionFile, ocr) { partial -> _state.update { if (generation == taskSerial) it.copy(partial = partial) else it } } }
                    finally { visionFile.delete() }
                }
                require(text.isNotBlank()) { "No readable text found on page ${index + 1}. Try a clearer photo or a tighter crop." }
                texts += text.trim()
            }
            val separator = if (note.source.isBlank()) "" else "\n\n"
            val appended = separator + texts.joinToString("\n\n")
            val label = if (basicOnly) "Basic text extraction • equations need manual review" else "Photo transcription • check against the original"
            _state.update { it.copy(proposal = EditProposal(note.id, note.revision, "", appended, note.source.length, note.source.length, label)) }
        }
    }

    private suspend fun recognize(page: PhotoPage): String = withContext(Dispatchers.IO) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try { recognizer.process(InputImage.fromFilePath(app, Uri.fromFile(File(page.path)))).await().text }
        finally { recognizer.close() }
    }

    fun requestEdit(instruction: String, start: Int = 0, end: Int = -1) {
        val note = _state.value.current ?: return
        if (instruction.isBlank()) { notifyError("Describe the change you want."); return }
        if (!requireAi()) return
        val actualStart = start.coerceIn(0, note.source.length)
        val actualEnd = (if (end < 0) note.source.length else end).coerceIn(actualStart, note.source.length)
        val oldText = note.source.substring(actualStart, actualEnd)
        if (oldText.isBlank()) { notifyError("Write or transcribe some text first."); return }
        try { AiDocumentText.checkRevisionInput(oldText, instruction.trim()) }
        catch (invalid: IllegalArgumentException) { notifyError(invalid.message ?: "Use a smaller selection or shorter instruction."); return }
        val provider = _state.value.aiProvider
        val engine = activeAi()
        runTask("${provider.label}: preparing your suggestion…", provider) {
            val generation = taskSerial
            val result = engine.revise(oldText, instruction.trim()) { partial -> _state.update { if (generation == taskSerial) it.copy(partial = partial) else it } }
            require(result.isNotBlank()) { "The model returned an empty suggestion. Your note has been preserved." }
            _state.update { it.copy(proposal = EditProposal(note.id, note.revision, oldText, result, actualStart, actualEnd, instruction.trim())) }
        }
    }

    fun acceptProposal() {
        val proposal = _state.value.proposal ?: return
        val note = _state.value.current
        val result = note?.let { proposal.applyTo(it) }
        if (result == null) {
            notifyError("The note changed while the suggestion was being prepared. Your edits are safe. Reject this suggestion and run it again on the current version.")
            return
        }
        updateSource(result)
        _state.update { it.copy(proposal = null) }
    }
    fun rejectProposal() { _state.update { it.copy(proposal = null) } }

    fun downloadModel() {
        if (modelJob?.isActive == true || _state.value.busy) return
        modelJob = viewModelScope.launch {
            try { ai.downloadModel() }
            catch (_: CancellationException) { }
            catch (e: Exception) { notifyError("Model download failed: ${e.message}. You can resume it from Local AI setup.") }
        }
    }

    fun checkForUpdates() {
        if (updateJob?.isActive == true) return
        _state.update { it.copy(updates = it.updates.copy(status = UpdateStatus.CHECKING, message = null)) }
        updateJob = viewModelScope.launch {
            try {
                val (release, newer) = withContext(Dispatchers.IO) { GitHubUpdates().check(BuildConfig.VERSION_NAME) }
                val message = when {
                    newer -> "Version ${release.tag} is available. Open the release to download the APK."
                    release.version == AppVersion.parse(BuildConfig.VERSION_NAME) -> "You're up to date."
                    else -> "Your installed build is newer than the latest published release."
                }
                _state.update { it.copy(updates = it.updates.copy(
                    status = if (newer) UpdateStatus.AVAILABLE else UpdateStatus.CURRENT,
                    message = message, latestVersion = release.tag, releaseUrl = release.url,
                )) }
            } catch (_: CancellationException) {
                _state.update { it.copy(updates = it.updates.copy(status = UpdateStatus.IDLE, message = null)) }
            } catch (e: Exception) {
                _state.update { it.copy(updates = it.updates.copy(status = UpdateStatus.ERROR,
                    message = e.message ?: "Could not check for updates. Check your connection and try again.")) }
            }
        }
    }

    fun openUpdatePage() {
        try {
            app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(_state.value.updates.releaseUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { notifyError("A browser could not be opened. Visit github.com/JJTeoh666/photo-to-docs/releases to download updates.") }
    }
    fun cancelModelDownload() { ai.cancelDownload(); modelJob?.cancel() }
    fun deleteModel() {
        if (_state.value.busy || _state.value.model.downloading) return
        modelJob = viewModelScope.launch { try { ai.deleteModel() } catch (e: Exception) { notifyError(e.message ?: "Could not remove model.") } }
    }
    fun importModel(uri: Uri) {
        if (_state.value.busy || modelJob?.isActive == true) return
        modelJob = viewModelScope.launch {
            try { ai.importModel(uri) }
            catch (_: CancellationException) { }
            catch (e: Exception) { notifyError("Model import failed: ${e.message}") }
        }
    }

    fun exportPdf(activity: Activity) {
        val note = _state.value.current ?: return
        runTask("Preparing PDF…") { printPdf(activity, note.title, note.source) }
    }

    fun export(format: ExportFormat, uri: Uri) {
        val note = _state.value.current ?: return
        runTask("Creating ${format.extension.uppercase()} file…") {
            val bytes = exportBytes(format, note)
            withContext(Dispatchers.IO) {
                app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() } ?: kotlin.error("The chosen location could not be opened.")
            }
        }
    }

    fun shareExport(format: ExportFormat) {
        val note = _state.value.current ?: return
        runTask("Preparing file to share…") {
            val bytes = exportBytes(format, note)
            val folder = File(app.cacheDir, "exports").apply { mkdirs() }
            val filename = note.title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").trim().take(80).ifBlank { "note" }
            val file = File(folder, "$filename.${format.extension}")
            withContext(Dispatchers.IO) { file.writeBytes(bytes) }
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = format.mime
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newRawUri("Document", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            app.startActivity(Intent.createChooser(send, "Share ${format.extension.uppercase()}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private suspend fun exportBytes(format: ExportFormat, note: NoteDocument): ByteArray {
        val equations = if (format == ExportFormat.WORD) convertEquations(app, note.source) else emptyMap()
        return withContext(Dispatchers.Default) {
            if (format == ExportFormat.LATEX) DocumentExporter.latex(note.title.ifBlank { "Untitled note" }, note.source).toByteArray(Charsets.UTF_8)
            else DocumentExporter.docx(note.title.ifBlank { "Untitled note" }, note.source, equations)
        }
    }

    private fun runTask(label: String, provider: AiProvider? = null, block: suspend () -> Unit) {
        if (_state.value.busy) return
        val generation = ++taskSerial
        _state.update { it.copy(busy = true, busyLabel = label, partial = "", error = null) }
        task = viewModelScope.launch {
            try { block() }
            catch (_: CancellationException) { }
            catch (e: OutOfMemoryError) { if (generation == taskSerial) notifyError("The phone ran out of memory. Close other apps, use a tighter crop, and try again. Manual editing is available.") }
            catch (e: Exception) {
                if (generation == taskSerial) {
                    if (provider == AiProvider.CHATGPT) { chatGpt.reportFailure(e); notifyError(chatGpt.state.value.status) }
                    else notifyError(e.message ?: "This task could not be completed.")
                }
            }
            finally { if (generation == taskSerial) _state.update { it.copy(busy = false, busyLabel = "", partial = "") } }
        }
    }

    fun cancelTask() {
        ++taskSerial
        ai.cancelGeneration(); task?.cancel(); task = null
        _state.update { it.copy(busy = false, busyLabel = "", partial = "") }
    }
    fun dismissError() { _state.update { it.copy(error = null) } }
    private fun notifyError(message: String) { _state.update { it.copy(error = message) } }

    override fun onCleared() {
        chatJob?.cancel(); chatGpt.cancelSignIn()
        ai.cancelDownload(); ai.cancelGeneration(); ai.close()
        saveQueue.close()
        super.onCleared()
    }
}
