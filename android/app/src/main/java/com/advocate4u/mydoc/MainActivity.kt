package com.advocate4u.mydoc

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: MyDocViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MyDocApp(viewModel, intent?.data) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDocApp(vm: MyDocViewModel, initialUri: Uri?) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val tabs = listOf("Home", "Word", "Excel", "PDF", "More")
    var saveExtension by remember { mutableStateOf("docx") }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::open) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { vm.save(it, saveExtension) } }
    var handledInitial by remember(initialUri) { mutableStateOf(false) }
    LaunchedEffect(initialUri) { if (!handledInitial) { handledInitial = true; initialUri?.let(vm::open) } }

    Scaffold(topBar = {
        TopAppBar(title = { Text("MyDoc${if (state.dirty) " •" else ""}") }, actions = {
            if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            TextButton(onClick = { openLauncher.launch(arrayOf("*/*")) }) { Text("Open") }
            TextButton(onClick = {
                saveExtension = when (state.tab) { 2 -> "xlsx"; 3 -> "pdf"; 4 -> "pptx"; else -> "docx" }
                saveLauncher.launch("${state.name.substringBeforeLast('.', "Untitled")}.${saveExtension}")
            }) { Text("Save") }
        })
    }, bottomBar = { NavigationBar { tabs.forEachIndexed { i, label -> NavigationBarItem(selected = state.tab == i, onClick = { vm.setTab(i) }, icon = { Text(label.take(1)) }, label = { Text(label) }) } } }) { padding ->
        Column(Modifier.padding(padding).padding(12.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(state.status, style = MaterialTheme.typography.bodySmall)
            when (state.tab) {
                0 -> HomeContent(state.recent, vm::newDocument, { openLauncher.launch(arrayOf("*/*")) }, vm)
                1 -> WordEditor(state, vm)
                2 -> SpreadsheetEditor(state, vm)
                3 -> PdfEditor(state.text)
                else -> MoreFeatures(vm)
            }
        }
    }
}

@Composable private fun HomeContent(recent: List<RecentDocument>, onNew: () -> Unit, onOpen: () -> Unit, vm: MyDocViewModel) {
    Text("MyDoc", style = MaterialTheme.typography.headlineLarge)
    Text("Offline-first document workspace • no account required")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(onClick = onNew) { Text("New document") }; OutlinedButton(onClick = onOpen) { Text("Open file") } }
    Text("Recent documents", style = MaterialTheme.typography.titleLarge)
    if (recent.isEmpty()) Text("No recent documents")
    recent.forEach { item -> OutlinedButton(onClick = { vm.open(Uri.parse(item.uri)) }, modifier = Modifier.fillMaxWidth()) { Text(item.name) } }
}

@Composable private fun WordEditor(state: MyDocUiState, vm: MyDocViewModel) {
    Text("Word / DOCX", style = MaterialTheme.typography.headlineSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(state.bold, vm::toggleBold, label = { Text("B", fontWeight = FontWeight.Bold) })
        FilterChip(state.italic, vm::toggleItalic, label = { Text("I") })
        FilterChip(state.underline, vm::toggleUnderline, label = { Text("U", textDecoration = TextDecoration.Underline) })
        OutlinedButton(onClick = vm::undo) { Text("Undo") }
        OutlinedButton(onClick = vm::redo) { Text("Redo") }
    }
    OutlinedTextField(value = state.text, onValueChange = vm::setText, modifier = Modifier.fillMaxWidth().weight(1f), label = { Text(state.name) }, minLines = 20)
}

@Composable private fun SpreadsheetEditor(state: MyDocUiState, vm: MyDocViewModel) {
    Text("Excel / XLSX", style = MaterialTheme.typography.headlineSmall)
    Text("Formula cells start with = and are preserved in XLSX export.", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedButton(onClick = vm::undo) { Text("Undo") }; OutlinedButton(onClick = vm::redo) { Text("Redo") } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
        state.cells.forEachIndexed { r, row ->
            Row {
                row.forEachIndexed { c, value ->
                    OutlinedTextField(value = value, onValueChange = { vm.setCell(r, c, it) }, modifier = Modifier.width(110.dp).height(64.dp), singleLine = true, label = { Text("${('A'.code + c).toChar()}${r + 1}") })
                }
            }
        }
    }
}

@Composable private fun PdfEditor(text: String) {
    Text("PDF", style = MaterialTheme.typography.headlineSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedButton(onClick = {}) { Text("Zoom −") }; OutlinedButton(onClick = {}) { Text("Zoom +") }; OutlinedButton(onClick = {}) { Text("Highlight") }; OutlinedButton(onClick = {}) { Text("Search") } }
    Surface(Modifier.fillMaxWidth().weight(1f), tonalElevation = 2.dp) { Text(if (text.isBlank()) "Open a PDF to view it. PDF rendering/annotation engine is the next integration layer." else text, Modifier.padding(18.dp)) }
}

@Composable private fun MoreFeatures(vm: MyDocViewModel) {
    Text("PowerPoint / PPTX", style = MaterialTheme.typography.headlineSmall)
    Text("Create a basic slide package from the current document and export it as PPTX.")
    Text("Built-in foundations", style = MaterialTheme.typography.titleLarge)
    Text("• Offline storage and recent documents\n• Atomic exports\n• Bounded text cache\n• Background IO\n• Undo / redo\n• Crash-safe recovery snapshot\n• DOCX / XLSX / PPTX package generation\n• PDF export")
    Button(onClick = { vm.setTab(1) }) { Text("Open editor") }
}
