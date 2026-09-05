package com.advocate4u.mydoc

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MyDocApp(intent?.data) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDocApp(initialUri: Uri?) {
    var tab by remember { mutableIntStateOf(0) }
    var editorText by remember { mutableStateOf("") }
    var currentName by remember { mutableStateOf("Untitled") }
    var status by remember { mutableStateOf("Ready") }
    val tabs = listOf("Home", "Word", "Excel", "PDF", "More")

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val name = uriName(uri) ?: "Document"
                currentName = name
                val ext = name.substringAfterLast('.', "txt")
                val bytes = LocalContext.current.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                editorText = DocumentEngine.readText(bytes, ext)
                status = "Opened $name"
                tab = when (ext.lowercase()) { "pdf" -> 3; "xlsx", "xls", "csv" -> 2; else -> 1 }
            }.onFailure { status = "Could not open file" }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("MyDoc") }, actions = {
            TextButton(onClick = { openLauncher.launch(arrayOf("*/*")) }) { Text("Open") }
        }) },
        bottomBar = { NavigationBar { tabs.forEachIndexed { i, label ->
            NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(label.take(1)) }, label = { Text(label) })
        } } }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("No account required • Offline first", style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.bodySmall)
            when (tab) {
                0 -> HomeContent(onNew = { currentName = "Untitled"; editorText = ""; tab = 1 }, onOpen = { openLauncher.launch(arrayOf("*/*")) })
                1 -> WordEditor(currentName, editorText, { editorText = it }, { status = "Changes saved locally" })
                2 -> SpreadsheetEditor()
                3 -> PdfEditor(editorText)
                else -> MoreFeatures()
            }
        }
    }
}

@Composable
private fun HomeContent(onNew: () -> Unit, onOpen: () -> Unit) {
    Text("Office documents", style = MaterialTheme.typography.headlineSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onNew) { Text("New document") }
        OutlinedButton(onClick = onOpen) { Text("Open file") }
    }
    Text("Create and edit documents without signing in.")
    Text("Word • Excel • PDF • PowerPoint")
}

@Composable
private fun WordEditor(name: String, text: String, onTextChange: (String) -> Unit, onSave: () -> Unit) {
    var bold by remember { mutableStateOf(false) }
    var italic by remember { mutableStateOf(false) }
    var underline by remember { mutableStateOf(false) }
    Text("Word / DOCX", style = MaterialTheme.typography.headlineSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = bold, onClick = { bold = !bold }, label = { Text("B") })
        FilterChip(selected = italic, onClick = { italic = !italic }, label = { Text("I") })
        FilterChip(selected = underline, onClick = { underline = !underline }, label = { Text("U") })
        OutlinedButton(onClick = onSave) { Text("Save") }
        OutlinedButton(onClick = {}) { Text("Export PDF") }
    }
    OutlinedTextField(value = text, onValueChange = onTextChange, modifier = Modifier.fillMaxWidth().weight(1f), label = { Text(name) }, minLines = 20)
    Text("Formatting toolbar foundation: font, size, styles, alignment, lists, tables, images, headers/footers and page layout will use the same editor model.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SpreadsheetEditor() {
    Text("Excel / XLSX", style = MaterialTheme.typography.headlineSmall)
    Text("Spreadsheet workspace")
    val cells = remember { mutableStateListOf(*Array(25) { "" }) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        for (r in 0 until 5) {
            Row {
                for (c in 0 until 5) {
                    val index = r * 5 + c
                    OutlinedTextField(value = cells[index], onValueChange = { cells[index] = it }, modifier = Modifier.width(100.dp), singleLine = true, label = { Text("${('A'.code + c).toChar()}${r + 1}") })
                }
            }
        }
    }
    Text("Planned engine features: formulas, multiple sheets, cell formatting, merge, sort/filter, charts, freeze panes and XLSX import/export.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun PdfEditor(text: String) {
    Text("PDF", style = MaterialTheme.typography.headlineSmall)
    Text("PDF workspace")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = {}) { Text("Zoom −") }
        OutlinedButton(onClick = {}) { Text("Zoom +") }
        OutlinedButton(onClick = {}) { Text("Annotate") }
        OutlinedButton(onClick = {}) { Text("Search") }
    }
    Text(if (text.isBlank()) "Open a PDF to view it here." else text, modifier = Modifier.fillMaxWidth().weight(1f))
    Text("PDF roadmap: page rendering, text selection, highlights, drawing, forms, signatures, page operations and export.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun MoreFeatures() {
    Text("More Office features", style = MaterialTheme.typography.headlineSmall)
    Text("PowerPoint / PPTX")
    Text("Slides, text, images, shapes, formatting and presentation mode")
    Text("Common tools")
    Text("Print • Share • Convert • OCR • Search • Dark mode • AI assistance")
}

private fun uriName(uri: Uri): String? = null
