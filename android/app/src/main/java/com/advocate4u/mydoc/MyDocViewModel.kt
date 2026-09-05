package com.advocate4u.mydoc

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.advocate4u.mydoc.core.AppDispatchers
import com.advocate4u.mydoc.data.DocumentRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MyDocViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = DocumentRepository(app.contentResolver)
    private val prefs = app.getSharedPreferences("mydoc_recent", Application.MODE_PRIVATE)
    private val _ui = MutableStateFlow(MyDocUiState(recent = loadRecent()))
    val ui: StateFlow<MyDocUiState> = _ui.asStateFlow()
    private val undo = ArrayDeque<EditorSnapshot>()
    private val redo = ArrayDeque<EditorSnapshot>()
    private var autosaveJob: Job? = null

    fun open(uri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, status = "Opening…")
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Document"
            }.getOrDefault("Document")
            val ext = name.substringAfterLast('.', "txt").lowercase()
            repository.read(uri, ext, uri.toString()).fold(
                onSuccess = { text ->
                    val cells = if (ext == "xlsx" || ext == "csv") parseGrid(text) else _ui.value.cells
                    val next = _ui.value.copy(loading = false, name = name, text = text, cells = cells, tab = ext.toTab(), status = "Opened $name", dirty = false, sourceUri = uri.toString())
                    _ui.value = next
                    pushRecent(name, uri.toString())
                    undo.clear(); redo.clear()
                    scheduleAutosave()
                },
                onFailure = { error -> _ui.value = _ui.value.copy(loading = false, status = error.message ?: "Could not open file") }
            )
        }
    }

    fun newDocument() {
        undo.clear(); redo.clear(); autosaveJob?.cancel()
        _ui.value = MyDocUiState(tab = 1, name = "Untitled.docx", status = "New document", recent = loadRecent())
    }

    fun setText(value: String) {
        if (value.length <= 5_000_000) {
            snapshot()
            _ui.value = _ui.value.copy(text = value, dirty = true, status = "Modified")
            scheduleAutosave()
        }
    }

    fun setCell(row: Int, col: Int, value: String) {
        if (row !in 0 until 100 || col !in 0 until 26) return
        snapshot()
        val mutable = _ui.value.cells.map { it.toMutableList() }.toMutableList()
        while (mutable.size <= row) mutable.add(MutableList(26) { "" })
        while (mutable[row].size <= col) mutable[row].add("")
        mutable[row][col] = value.take(10_000)
        _ui.value = _ui.value.copy(cells = mutable.map { it.toList() }, dirty = true, status = "Modified")
        scheduleAutosave()
    }

    fun toggleBold() { _ui.value = _ui.value.copy(bold = !_ui.value.bold, dirty = true) }
    fun toggleItalic() { _ui.value = _ui.value.copy(italic = !_ui.value.italic, dirty = true) }
    fun toggleUnderline() { _ui.value = _ui.value.copy(underline = !_ui.value.underline, dirty = true) }
    fun setTab(value: Int) { _ui.value = _ui.value.copy(tab = value) }

    fun undo() {
        val previous = undo.removeLastOrNull() ?: return
        redo.addLast(snapshotOf(_ui.value))
        _ui.value = previous.toState(_ui.value).copy(status = "Undo", dirty = true)
    }

    fun redo() {
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(snapshotOf(_ui.value))
        _ui.value = next.toState(_ui.value).copy(status = "Redo", dirty = true)
    }

    fun save(uri: Uri, extension: String) {
        val state = _ui.value
        viewModelScope.launch {
            _ui.value = state.copy(loading = true, status = "Saving…")
            repository.exportToUri(uri, extension, state.text, state.cells, state.bold, state.italic, state.underline).fold(
                onSuccess = { _ui.value = _ui.value.copy(loading = false, dirty = false, status = "Saved") },
                onFailure = { _ui.value = _ui.value.copy(loading = false, status = "Save failed: ${it.message ?: "unknown error"}") }
            )
        }
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(1500)
            if (!isActive || !_ui.value.dirty) return@launch
            val snapshot = _ui.value
            withContext(AppDispatchers.io) {
                runCatching {
                    val file = File(getApplication<Application>().filesDir, "recovery.mydoc")
                    val temp = File(getApplication<Application>().filesDir, "recovery.mydoc.tmp")
                    temp.writeText(snapshot.text.take(5_000_000), Charsets.UTF_8)
                    if (!temp.renameTo(file)) { temp.copyTo(file, true); temp.delete() }
                }
            }
        }
    }

    private fun snapshot() {
        undo.addLast(snapshotOf(_ui.value))
        while (undo.size > 50) undo.removeFirst()
        redo.clear()
    }

    private fun snapshotOf(s: MyDocUiState) = EditorSnapshot(s.text, s.cells, s.bold, s.italic, s.underline)
    private fun EditorSnapshot.toState(base: MyDocUiState) = base.copy(text = text, cells = cells, bold = bold, italic = italic, underline = underline)

    private fun parseGrid(text: String): List<List<String>> = text.lines().take(100).map { it.split('\t', ',').take(26) }
    private fun String.toTab() = when (this) { "pdf" -> 3; "xlsx", "xls", "csv" -> 2; "ppt", "pptx" -> 4; else -> 1 }

    private fun loadRecent(): List<RecentDocument> = prefs.getStringSet("items", emptySet()).orEmpty().mapNotNull { raw ->
        val parts = raw.split('|', limit = 2)
        if (parts.size == 2) RecentDocument(parts[0], parts[1]) else null
    }.take(10)

    private fun pushRecent(name: String, uri: String) {
        val updated = (listOf(RecentDocument(name, uri)) + loadRecent().filterNot { it.uri == uri }).take(10)
        prefs.edit().putStringSet("items", updated.map { "${it.name}|${it.uri}" }.toSet()).apply()
        _ui.value = _ui.value.copy(recent = updated)
    }
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
    val recent: List<RecentDocument> = emptyList()
)

data class EditorSnapshot(val text: String, val cells: List<List<String>>, val bold: Boolean, val italic: Boolean, val underline: Boolean)
