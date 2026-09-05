package com.advocate4u.mydoc

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.advocate4u.mydoc.core.FileFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

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
    var searchText by remember { mutableStateOf("") }
    var replacementText by remember { mutableStateOf("") }
    var showFind by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::open) }
    val docxSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FileFormat.DOCX.mime)) { uri -> uri?.let { vm.save(it, "docx") } }
    val xlsxSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FileFormat.XLSX.mime)) { uri -> uri?.let { vm.save(it, "xlsx") } }
    val pdfSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FileFormat.PDF.mime)) { uri -> uri?.let { vm.save(it, "pdf") } }
    val pptxSave = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FileFormat.PPTX.mime)) { uri -> uri?.let { vm.save(it, "pptx") } }
    var handledInitial by remember(initialUri) { mutableStateOf(false) }
    LaunchedEffect(initialUri) { if (!handledInitial) { handledInitial = true; initialUri?.let(vm::open) } }

    if (state.recoveryAvailable) {
        AlertDialog(
            onDismissRequest = vm::dismissRecovery,
            title = { Text("Recover unsaved work?") },
            text = { Text("MyDoc found an unsaved recovery copy${state.recoveryName?.let { " for $it" } ?: ""}.") },
            confirmButton = { Button(onClick = vm::restoreRecovery) { Text("Recover") } },
            dismissButton = { TextButton(onClick = vm::dismissRecovery) { Text("Discard") } }
        )
    }

    if (showFind) {
        AlertDialog(
            onDismissRequest = { showFind = false },
            title = { Text("Find / Replace") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(searchText, { searchText = it; vm.find(it) }, label = { Text("Find") }, singleLine = true)
                    OutlinedTextField(replacementText, { replacementText = it }, label = { Text("Replace with") }, singleLine = true)
                    Text(if (searchText.isEmpty()) "" else "${state.searchCount} match${if (state.searchCount == 1) "" else "es"}")
                }
            },
            confirmButton = { Button(onClick = { vm.replace(replacementText, false) }) { Text("Replace") } },
            dismissButton = { Row { TextButton(onClick = { vm.replace(replacementText, true) }) { Text("Replace all") }; TextButton(onClick = { showFind = false }) { Text("Close") } } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("MyDoc${if (state.dirty) " •" else ""}") }, actions = {
                if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                TextButton(onClick = { openLauncher.launch(arrayOf("*/*")) }) { Text("Open") }
                TextButton(onClick = { showFind = true }) { Text("Find") }
                state.sourceUri?.let { source ->
                    TextButton(onClick = {
                        val mime = FileFormat.mimeForExtension(state.name.substringAfterLast('.', "txt"))
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = mime
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(source))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Share document"))
                    }) { Text("Share") }
                }
                TextButton(onClick = {
                    val extension = when (state.tab) { 2 -> "xlsx"; 3 -> "pdf"; 4 -> "pptx"; else -> "docx" }
                    val name = "${state.name.substringBeforeLast('.', "Untitled")}.${extension}"
                    when (extension) {
                        "xlsx" -> xlsxSave.launch(name)
                        "pdf" -> pdfSave.launch(name)
                        "pptx" -> pptxSave.launch(name)
                        else -> docxSave.launch(name)
                    }
                }) { Text("Save") }
            })
        },
        bottomBar = {
            NavigationBar { tabs.forEachIndexed { i, label -> NavigationBarItem(selected = state.tab == i, onClick = { vm.setTab(i) }, icon = { Text(label.take(1)) }, label = { Text(label) }) } }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(12.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(state.status, style = MaterialTheme.typography.bodySmall)
            when (state.tab) {
                0 -> HomeContent(state.recent, vm::newDocument, { openLauncher.launch(arrayOf("*/*")) }, vm)
                1 -> WordEditor(state, vm)
                2 -> SpreadsheetEditor(state, vm)
                3 -> PdfEditor(state.sourceUri?.let(Uri::parse))
                else -> MoreFeatures(vm)
            }
        }
    }
}

@Composable
private fun HomeContent(recent: List<RecentDocument>, onNew: () -> Unit, onOpen: () -> Unit, vm: MyDocViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("MyDoc", style = MaterialTheme.typography.headlineLarge)
        Text("Offline-first document workspace • no account required")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(onClick = onNew) { Text("New document") }; OutlinedButton(onClick = onOpen) { Text("Open file") } }
        Text("Recent documents", style = MaterialTheme.typography.titleLarge)
        if (recent.isEmpty()) Text("No recent documents")
        recent.forEach { item -> OutlinedButton(onClick = { vm.open(Uri.parse(item.uri)) }, modifier = Modifier.fillMaxWidth()) { Text(item.name) } }
    }
}

@Composable
private fun WordEditor(state: MyDocUiState, vm: MyDocViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Word / DOCX", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = state.bold, onClick = vm::toggleBold, label = { Text("B", fontWeight = FontWeight.Bold) })
            FilterChip(selected = state.italic, onClick = vm::toggleItalic, label = { Text("I") })
            FilterChip(selected = state.underline, onClick = vm::toggleUnderline, label = { Text("U", textDecoration = TextDecoration.Underline) })
            OutlinedButton(onClick = vm::undo) { Text("Undo") }
            OutlinedButton(onClick = vm::redo) { Text("Redo") }
        }
        Text("${state.text.length} characters • ${state.text.lines().size} lines", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = state.text, onValueChange = vm::setText, modifier = Modifier.fillMaxSize(), label = { Text(state.name) }, minLines = 20)
    }
}

@Composable
private fun SpreadsheetEditor(state: MyDocUiState, vm: MyDocViewModel) {
    val rows = state.cells.take(100)
    val horizontal = rememberScrollState()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Excel / XLSX", style = MaterialTheme.typography.headlineSmall)
        Text("100 × 26 editing viewport • formula evaluation supports arithmetic and SUM/AVERAGE/MIN/MAX.", style = MaterialTheme.typography.bodySmall)
        state.calculationResult?.let { Text("Calculated: $it", style = MaterialTheme.typography.labelLarge) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedButton(onClick = vm::undo) { Text("Undo") }; OutlinedButton(onClick = vm::redo) { Text("Redo") } }
        Row(Modifier.horizontalScroll(horizontal)) {
            Text("#", Modifier.width(44.dp).padding(8.dp), style = MaterialTheme.typography.labelMedium)
            (0 until 26).forEach { c -> Text(('A'.code + c).toChar().toString(), Modifier.width(110.dp).padding(8.dp), style = MaterialTheme.typography.labelMedium) }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(rows, key = { index, _ -> index }) { r, row ->
                Row {
                    Text("${r + 1}", Modifier.width(44.dp).padding(8.dp), style = MaterialTheme.typography.labelSmall)
                    row.take(26).forEachIndexed { c, value ->
                        OutlinedTextField(value = value, onValueChange = { vm.setCell(r, c, it) }, modifier = Modifier.width(110.dp).height(64.dp), singleLine = true, label = { Text("${('A'.code + c).toChar()}${r + 1}") })
                    }
                }
            }
        }
    }
}

@Composable private fun PdfEditor(uri: Uri?) {
    Column(Modifier.fillMaxSize()) { Text("PDF", style = MaterialTheme.typography.headlineSmall); if (uri == null) Surface(Modifier.fillMaxSize(), tonalElevation = 2.dp) { Text("Open a PDF to view it.", Modifier.padding(18.dp)) } else PdfViewer(uri) }
}

private data class PdfSession(val renderer: PdfRenderer?, val descriptor: ParcelFileDescriptor?, val error: String?)

@Composable
private fun PdfViewer(uri: Uri) {
    val context = LocalContext.current
    var page by remember(uri) { mutableIntStateOf(0) }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var session by remember(uri) { mutableStateOf(PdfSession(null, null, null)) }
    DisposableEffect(uri, context) {
        val result = openPdf(context, uri); session = result; page = 0
        onDispose {
            result.renderer?.let { synchronized(it) { it.close() } }
            result.descriptor?.close()
        }
    }
    val renderer = session.renderer
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(renderer, page, zoom) {
        bitmap?.recycle()
        bitmap = null
        if (renderer != null && page in 0 until renderer.pageCount) {
            bitmap = withContext(Dispatchers.Default) { renderPdfPage(renderer, page, zoom) }
        }
    }
    DisposableEffect(Unit) { onDispose { bitmap?.recycle() } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(enabled = renderer != null && page > 0, onClick = { page-- }) { Text("Previous") }
            Text(if (renderer == null) "Page —" else "Page ${page + 1} / ${renderer.pageCount}", modifier = Modifier.padding(top = 12.dp))
            OutlinedButton(enabled = renderer != null && page < renderer.pageCount - 1, onClick = { page++ }) { Text("Next") }
            OutlinedButton(onClick = { zoom = max(0.5f, zoom - 0.25f) }) { Text("−") }
            OutlinedButton(onClick = { zoom = min(3f, zoom + 0.25f) }) { Text("+") }
        }
        session.error?.let { Text("PDF error: $it") }
        if (renderer != null) AndroidView(modifier = Modifier.fillMaxSize(), factory = { PdfPageView(it) }, update = { it.bind(bitmap) })
    }
}

private fun openPdf(context: Context, uri: Uri): PdfSession = try {
    val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return PdfSession(null, null, "Unable to open PDF")
    try { PdfSession(PdfRenderer(fd), fd, null) } catch (t: Throwable) { fd.close(); PdfSession(null, null, t.message ?: "Unable to render PDF") }
} catch (t: Throwable) { PdfSession(null, null, t.message ?: "Unable to open PDF") }

private fun renderPdfPage(renderer: PdfRenderer, pageIndex: Int, zoom: Float): Bitmap? = try {
    synchronized(renderer) {
        if (pageIndex !in 0 until renderer.pageCount) return null
        renderer.openPage(pageIndex).use { page ->
            val scale = min(zoom, 3f)
            val width = max(1, (page.width * scale).toInt())
            val height = max(1, (page.height * scale).toInt())
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                it.eraseColor(Color.WHITE)
                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }
} catch (_: Throwable) { null }

private class PdfPageView(context: Context) : android.view.View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bitmap: Bitmap? = null
    fun bind(newBitmap: Bitmap?) {
        if (bitmap !== newBitmap) {
            bitmap = newBitmap
            requestLayout()
            invalidate()
        }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val b = bitmap
        setMeasuredDimension(if (b == null) MeasureSpec.getSize(widthMeasureSpec) else b.width, if (b == null) MeasureSpec.getSize(heightMeasureSpec) else b.height)
    }
    override fun onDraw(canvas: Canvas) { super.onDraw(canvas); bitmap?.let { canvas.drawBitmap(it, 0f, 0f, paint) } }
    override fun onDetachedFromWindow() { bitmap = null; super.onDetachedFromWindow() }
}

@Composable private fun MoreFeatures(vm: MyDocViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("PowerPoint / PPTX", style = MaterialTheme.typography.headlineSmall)
        Text("Basic single-slide PPTX creation and text extraction are available now; the architecture is ready for multi-slide editing.")
        Text("Implemented foundations", style = MaterialTheme.typography.titleLarge)
        Text("• Offline storage and recent documents\n• Persisted SAF read permission\n• Atomic exports\n• Bounded cache and input sizes\n• Background IO\n• Undo / redo\n• Crash recovery journal\n• Find / replace\n• Basic spreadsheet formulas\n• DOCX / XLSX / PPTX package generation\n• PDF export and background page rendering\n• Format-aware document sharing")
        Button(onClick = { vm.setTab(1) }) { Text("Open editor") }
    }
}
