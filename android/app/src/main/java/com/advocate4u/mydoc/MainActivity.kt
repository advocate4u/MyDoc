package com.advocate4u.mydoc

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: MyDocViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MyDocApp(viewModel, intent?.data) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDocApp(vm: MyDocViewModel, initialUri: Uri?) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val tabs = listOf("Home", "Word", "Excel", "PDF", "More")
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::open) }
    var handledInitial by remember(initialUri) { mutableStateOf(false) }
    LaunchedEffect(initialUri) { if (!handledInitial) { handledInitial = true; initialUri?.let(vm::open) } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("MyDoc") }, actions = {
            if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            TextButton(onClick = { openLauncher.launch(arrayOf("*/*")) }) { Text("Open") }
        }) },
        bottomBar = { NavigationBar { tabs.forEachIndexed { i, label ->
            NavigationBarItem(selected = state.tab == i, onClick = { vm.setTab(i) }, icon = { Text(label.take(1)) }, label = { Text(label) })
        } } }
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("No account required • Offline first", style = MaterialTheme.typography.titleMedium)
            Text(state.status, style = MaterialTheme.typography.bodySmall)
            when (state.tab) {
                0 -> HomeContent(onNew = vm::newDocument, onOpen = { openLauncher.launch(arrayOf("*/*")) })
                1 -> WordEditor(state.name, state.text, vm::setText)
                2 -> SpreadsheetEditor()
                3 -> PdfEditor(state.text)
                else -> MoreFeatures()
            }
        }
    }
}

@Composable private fun HomeContent(onNew: () -> Unit, onOpen: () -> Unit) {
    Text("Office documents", style = MaterialTheme.typography.headlineSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onNew) { Text("New document") }
        OutlinedButton(onClick = onOpen) { Text("Open file") }
    }
    Text("Create and edit documents without signing in.")
    Text("Word • Excel • PDF • PowerPoint")
}

@Composable private fun WordEditor(name: String, text: String, onTextChange: (String) -> Unit) {
    var bold by remember { mutableStateOf(false) }
    var italic by remember { mutableStateOf(false) }
    var underline by remember { mutableStateOf(false) }
    Text("Word / DOCX", style = MaterialTheme.typography.headlineSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = bold, onClick = { bold = !bold }, label = { Text("B") })
        FilterChip(selected = italic, onClick = { italic = !italic }, label = { Text("I") })
        FilterChip(selected = underline, onClick = { underline = !underline }, label = { Text("U") })
    }
    OutlinedTextField(value = text, onValueChange = onTextChange, modifier = Modifier.fillMaxWidth().weight(1f), label = { Text(name) }, minLines = 20)
}

@Composable private fun SpreadsheetEditor() {
    Text("Excel / XLSX", style = MaterialTheme.typography.headlineSmall)
    val cells = remember { mutableStateListOf(*Array(25) { "" }) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        for (r in 0 until 5) Row {
            for (c in 0 until 5) {
                val index = r * 5 + c
                OutlinedTextField(value = cells[index], onValueChange = { cells[index] = it }, modifier = Modifier.width(100.dp), singleLine = true, label = { Text("${('A'.code + c).toChar()}${r + 1}") })
            }
        }
    }
}

@Composable private fun PdfEditor(text: String) {
    Text("PDF", style = MaterialTheme.typography.headlineSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = {}) { Text("Zoom −") }
        OutlinedButton(onClick = {}) { Text("Zoom +") }
        OutlinedButton(onClick = {}) { Text("Annotate") }
        OutlinedButton(onClick = {}) { Text("Search") }
    }
    Text(if (text.isBlank()) "Open a PDF to view it here." else text, Modifier.fillMaxWidth().weight(1f))
}

@Composable private fun MoreFeatures() {
    Text("More Office features", style = MaterialTheme.typography.headlineSmall)
    Text("PowerPoint / PPTX")
    Text("Slides, text, images, shapes, formatting and presentation mode")
    Text("Common tools")
    Text("Print • Share • Convert • OCR • Search • Dark mode • AI assistance")
}
