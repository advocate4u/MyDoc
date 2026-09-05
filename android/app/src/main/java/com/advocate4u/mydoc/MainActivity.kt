package com.advocate4u.mydoc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MyDocApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDocApp() {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Home", "Word", "Excel", "PDF", "More")
    Scaffold(
        topBar = { TopAppBar(title = { Text("MyDoc") }, actions = { TextButton(onClick = {}) { Text("Open") } }) },
        bottomBar = { NavigationBar { tabs.forEachIndexed { i, label -> NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(if (i == 0) "⌂" else label.take(1)) }, label = { Text(label) }) } } }
    ) { padding ->
        Column(Modifier.padding(padding).padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("No account required", style = MaterialTheme.typography.titleMedium)
            Text("Create, open, edit and manage your documents locally. MyDoc is designed to work offline.")
            when (tab) {
                0 -> HomeContent()
                1 -> EditorPlaceholder("Word / DOCX", "Rich formatting, paragraphs, tables, images, headers, footers and page layout")
                2 -> EditorPlaceholder("Excel / XLSX", "Sheets, cells, formulas, formatting, charts, sorting and filtering")
                3 -> EditorPlaceholder("PDF", "View, search, zoom, annotate, fill and export")
                else -> EditorPlaceholder("More Office features", "PowerPoint, printing, sharing, conversion, OCR and future AI tools")
            }
        }
    }
}

@Composable private fun HomeContent() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = {}) { Text("New document") }
        OutlinedButton(onClick = {}) { Text("Open file") }
    }
    Text("Recent documents", style = MaterialTheme.typography.titleLarge)
    Text("Your recent files will appear here.")
}

@Composable private fun EditorPlaceholder(title: String, description: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Text(description)
    Button(onClick = {}) { Text("Open $title") }
}
