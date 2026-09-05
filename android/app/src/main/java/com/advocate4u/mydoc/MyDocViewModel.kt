package com.advocate4u.mydoc

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.advocate4u.mydoc.core.AppDispatchers
import com.advocate4u.mydoc.core.EditorUtils
import com.advocate4u.mydoc.core.RecentDocumentStore
import com.advocate4u.mydoc.core.RecoveryManager
import com.advocate4u.mydoc.data.DocumentRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyDocViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = DocumentRepository(app.contentResolver)
    private val recovery = RecoveryManager(app)
    private val prefs = app.getSharedPreferences("mydoc_recent", Application.MODE_PRIVATE)
    private val _ui = MutableStateFlow(MyDocUiState(recent = loadRecent()))
    val ui: StateFlow<MyDocUiState> = _ui.asStateFlow()
    private val undo = ArrayDeque<EditorSnapshot>()
    private val redo = ArrayDeque<EditorSnapshot>()
    private var autosaveJob: Job? = null

    init {
        recovery.read()?.let { snapshot ->
            _ui.value = _ui.value.copy(recoveryAvailable = true, recoveryName = snapshot.name, status = "Recovery available")
        }
    }

    fun open(uri: Uri) {
        val context = getApplication<Application>()
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: Throwable) { }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, status = "Opening…")
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Document"
            }.getOrDefault("Document")
            val ext = name.substringAfterLast('.', "txt").lowercase()
            repository.read(uri, ext, uri.toString()).fold(
                onSuccess = { text ->
                    val cells = if (ext in setOf("xlsx", "xls", "xlsm", "csv")) parseGrid(text) else List(20) { List(8) { "" } }
                    _ui.value = _ui.value.copy(loading = false, name = name, text = text, cells = cells, tab = ext.toTab(), status = "Opened $name", dirty = false, sourceUri = uri.toString(), recoveryAvailable = false, searchQuery = "", searchCount = 0, calculationResult = null)
                    pushRecent(name, uri.toString()); undo.clear(); redo.clear(); recovery.clear()
                },
                onFailure = { error -> _ui.value = _ui.value.copy(loading = false, status = error.message ?: "Could not open file") }
            )
        }
    }

    fun restoreRecovery() {
        val snapshot = recovery.read() ?: return
        undo.clear(); redo.clear()
        _ui.value = _ui.value.copy(name = snapshot.name, text = snapshot.text, tab = snapshot.extension.toTab(), dirty = true, status = "Recovered ${snapshot.name}", recoveryAvailable = false, sourceUri = null)
        recovery.clear()
    }

    fun dismissRecovery() { recovery.clear(); _ui.value = _ui.value.copy(recoveryAvailable = false, status = "Ready") }
    fun newDocument() { undo.clear(); redo.clear(); autosaveJob?.cancel(); recovery.clear(); _ui.value = MyDocUiState(tab = 1, name = "Untitled.docx", status = "New document", recent = loadRecent()) }

    fun renameRecent(document: RecentDocument, newName: String): Boolean {
        val trimmed = newName.trim().take(255)
        if (trimmed.isEmpty() || trimmed.contains('|')) { _ui.value = _ui.value.copy(status = "Enter a valid file name"); return false }
        val uri = Uri.parse(document.uri)
        val renamed = runCatching { DocumentsContract.renameDocument(getApplication<Application>().contentResolver, uri, trimmed) }.getOrNull()
        if (renamed == null) { _ui.value = _ui.value.copy(status = "Rename failed — the storage provider may not allow renaming"); return false }
        val newUri = renamed.toString()
        val updated = RecentDocumentStore.normalize(loadRecent().map { if (it.uri == document.uri) RecentDocument(trimmed, newUri) else it })
        persistRecent(updated)
        val current = _ui.value
        _ui.value = current.copy(name = if (current.sourceUri == document.uri) trimmed else current.name, sourceUri = if (current.sourceUri == document.uri) newUri else current.sourceUri, recent = updated, status = "Renamed to $trimmed")
        return true
    }

    fun removeRecent(document: RecentDocument) {
        val updated = loadRecent().filterNot { it.uri == document.uri }
        persistRecent(updated)
        _ui.value = _ui.value.copy(recent = updated, status = "Removed from recent documents")
    }

    fun clearRecent() {
        persistRecent(emptyList())
        _ui.value = _ui.value.copy(recent = emptyList(), status = "Recent documents cleared")
    }

    fun setText(value: String) {
        if (value.length <= 5_000_000) { snapshot(); _ui.value = _ui.value.copy(text = value, dirty = true, status = "Modified"); scheduleAutosave() }
    }

    fun setCell(row: Int, col: Int, value: String) {
        if (row !in 0 until 100 || col !in 0 until 26) return
        snapshot()
        val mutable = _ui.value.cells.map { it.toMutableList() }.toMutableList()
        while (mutable.size <= row) mutable.add(MutableList(26) { "" })
        while (mutable[row].size <= col) mutable[row].add("")
        mutable[row][col] = value.take(10_000)
        _ui.value = _ui.value.copy(cells = mutable.map { it.toList() }, dirty = true, status = "Modified", calculationResult = null)
        scheduleAutosave()
    }

    fun toggleBold() { _ui.value = _ui.value.copy(bold = !_ui.value.bold, dirty = true); scheduleAutosave() }
    fun toggleItalic() { _ui.value = _ui.value.copy(italic = !_ui.value.italic, dirty = true); scheduleAutosave() }
    fun toggleUnderline() { _ui.value = _ui.value.copy(underline = !_ui.value.underline, dirty = true); scheduleAutosave() }
    fun setTab(value: Int) { _ui.value = _ui.value.copy(tab = value) }

    fun find(query: String) {
        val q = query.take(200)
        val count = if (q.isEmpty()) 0 else Regex(Regex.escape(q)).findAll(_ui.value.text).count()
        _ui.value = _ui.value.copy(searchQuery = q, searchCount = count, status = if (q.isEmpty()) "Ready" else if (count == 0) "No matches" else "$count match${if (count == 1) "" else "es"}")
    }

    fun replace(replacement: String, replaceAll: Boolean) {
        val q = _ui.value.searchQuery
        if (q.isEmpty()) return
        val (updated, count) = EditorUtils.replace(_ui.value.text, q, replacement.take(10_000), replaceAll)
        if (count > 0) { snapshot(); _ui.value = _ui.value.copy(text = updated, dirty = true, searchCount = if (replaceAll) 0 else count, status = "Replaced $count match${if (count == 1) "" else "es"}"); scheduleAutosave() }
    }

    fun evaluateCell(row: Int, col: Int) {
        val value = _ui.value.cells.getOrNull(row)?.getOrNull(col) ?: return
        val result = EditorUtils.evaluateSimpleFormula(value, _ui.value.cells) ?: return
        _ui.value = _ui.value.copy(calculationResult = "${('A'.code + col).toChar()}${row + 1} = $result")
    }

    fun undo() { val previous = undo.removeLastOrNull() ?: return; redo.addLast(snapshotOf(_ui.value)); _ui.value = previous.toState(_ui.value).copy(status = "Undo", dirty = true); scheduleAutosave() }
    fun redo() { val next = redo.removeLastOrNull() ?: return; undo.addLast(snapshotOf(_ui.value)); _ui.value = next.toState(_ui.value).copy(status = "Redo", dirty = true); scheduleAutosave() }

    fun saveCurrent(): Boolean {
        val state = _ui.value
        val source = state.sourceUri ?: return false.also { _ui.value = state.copy(status = "Use Save As for a new document") }
        val extension = state.name.substringAfterLast('.', "").lowercase()
        if (extension !in WRITABLE_EXTENSIONS) {
            _ui.value = state.copy(status = "Use Save As — this format cannot be overwritten safely")
            return false
        }
        autosaveJob?.cancel()
        viewModelScope.launch { saveToUri(Uri.parse(source), extension, state) }
        return true
    }

    fun save(uri: Uri, extension: String) {
        val state = _ui.value
        autosaveJob?.cancel()
        viewModelScope.launch { saveToUri(uri, extension, state) }
    }

    private suspend fun saveToUri(uri: Uri, extension: String, state: MyDocUiState) {
        _ui.value = state.copy(loading = true, status = "Saving…")
        val result = if (extension == "pdf") repository.exportPdfToUri(uri, state.text) else repository.exportToUri(uri, extension, state.text, state.cells, state.bold, state.italic, state.underline)
        result.fold(
            { recovery.clear(); _ui.value = _ui.value.copy(loading = false, dirty = false, status = "Saved", sourceUri = uri.toString(), recoveryAvailable = false); pushRecent(_ui.value.name, uri.toString()) },
            { _ui.value = _ui.value.copy(loading = false, status = "Save failed: ${it.message ?: "unknown error"}") }
        )
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(1500)
            if (!isActive || !_ui.value.dirty) return@launch
            val snapshot = _ui.value
            val source = snapshot.sourceUri
            val extension = snapshot.name.substringAfterLast('.', "").lowercase()
            if (source != null && extension in WRITABLE_EXTENSIONS) {
                val result = withContext(AppDispatchers.io) {
                    repository.exportToUri(Uri.parse(source), extension, snapshot.text, snapshot.cells, snapshot.bold, snapshot.italic, snapshot.underline)
                }
                if (result.isSuccess) {
                    val current = _ui.value
                    if (current.sourceUri == source && current.text == snapshot.text && current.cells == snapshot.cells && current.bold == snapshot.bold && current.italic == snapshot.italic && current.underline == snapshot.underline) {
                        _ui.value = current.copy(loading = false, dirty = false, status = "Saved automatically")
                        recovery.clear()
                    }
                }
            } else {
                withContext(AppDispatchers.io) {
                    runCatching { recovery.write(snapshot.name, extension.ifEmpty { "docx" }, snapshot.text.take(5_000_000)) }
                }
            }
        }
    }

    private fun snapshot() { undo.addLast(snapshotOf(_ui.value)); while (undo.size > 50) undo.removeFirst(); redo.clear() }
    private fun snapshotOf(s: MyDocUiState) = EditorSnapshot(s.text, s.cells, s.bold, s.italic, s.underline)
    private fun EditorSnapshot.toState(base: MyDocUiState) = base.copy(text = text, cells = cells, bold = bold, italic = italic, underline = underline)
    private fun parseGrid(text: String): List<List<String>> = text.lines().take(100).map { it.split('\t', ',').take(26).let { row -> row + List(maxOf(0, 8 - row.size)) { "" } } }
    private fun String.toTab() = when (this) { "pdf" -> 3; "xlsx", "xls", "xlsm", "csv" -> 2; "ppt", "pptx" -> 4; else -> 1 }

    private fun loadRecent(): List<RecentDocument> {
        val stored = buildList {
            for (i in 0 until RecentDocumentStore.MAX_ITEMS) {
                val raw = prefs.getString("item_$i", null) ?: continue
                val parts = raw.split('|', limit = 2)
                if (parts.size == 2) add(RecentDocument(parts[0], parts[1]))
            }
        }
        val cleaned = RecentDocumentStore.removeInaccessible(getApplication<Application>().contentResolver, stored)
        if (cleaned.size != stored.size) persistRecent(cleaned)
        return cleaned
    }

    private fun persistRecent(items: List<RecentDocument>) {
        val normalized = RecentDocumentStore.normalize(items)
        prefs.edit().apply { for (i in 0 until RecentDocumentStore.MAX_ITEMS) remove("item_$i"); normalized.forEachIndexed { i, item -> putString("item_$i", "${item.name}|${item.uri}") } }.apply()
    }

    private fun pushRecent(name: String, uri: String) {
        val updated = RecentDocumentStore.push(loadRecent(), name, uri)
        persistRecent(updated); _ui.value = _ui.value.copy(recent = updated)
    }

    companion object { private val WRITABLE_EXTENSIONS = setOf("docx", "xlsx", "pptx", "txt", "csv") }
}

data class RecentDocument(val name: String, val uri: String)
data class MyDocUiState(
    val tab: Int = 0,
    val name: String = "Untitled",
    val text: String = "",
    val cells: List<List<String>> = List(20) { List(8) { "" } },
    val status: String = "Ready",
    val loading: Boolean = false,
    val dirty: Boolean = false,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val sourceUri: String? = null,
    val recent: List<RecentDocument> = emptyList(),
    val recoveryAvailable: Boolean = false,
    val recoveryName: String? = null,
    val searchQuery: String = "",
    val searchCount: Int = 0,
    val calculationResult: String? = null
)
data class EditorSnapshot(val text: String, val cells: List<List<String>>, val bold: Boolean, val italic: Boolean, val underline: Boolean)
