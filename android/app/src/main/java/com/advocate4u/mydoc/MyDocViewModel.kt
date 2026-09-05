package com.advocate4u.mydoc

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.advocate4u.mydoc.data.DocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MyDocViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = DocumentRepository(app.contentResolver)
    private val _ui = MutableStateFlow(MyDocUiState())
    val ui: StateFlow<MyDocUiState> = _ui.asStateFlow()

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
            repository.read(uri, ext, cacheKey = uri.toString()).fold(
                onSuccess = { text -> _ui.value = _ui.value.copy(loading = false, name = name, text = text, tab = ext.toTab(), status = "Opened $name") },
                onFailure = { error -> _ui.value = _ui.value.copy(loading = false, status = error.message ?: "Could not open file") }
            )
        }
    }

    fun newDocument() { _ui.value = MyDocUiState(tab = 1, status = "New document") }
    fun setText(value: String) { if (value.length <= 5_000_000) _ui.value = _ui.value.copy(text = value, dirty = true) }
    fun setTab(value: Int) { _ui.value = _ui.value.copy(tab = value) }

    private fun String.toTab() = when (this) { "pdf" -> 3; "xlsx", "xls", "csv" -> 2; "ppt", "pptx" -> 4; else -> 1 }
}

data class MyDocUiState(
    val tab: Int = 0,
    val name: String = "Untitled",
    val text: String = "",
    val status: String = "Ready",
    val loading: Boolean = false,
    val dirty: Boolean = false
)
