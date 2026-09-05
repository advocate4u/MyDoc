package com.advocate4u.mydoc

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    var saveExtension by remember { mutableStateOf("docx") }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::open) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { vm.save(it, saveExtension) } }
    var handledInitial by remember(initialUri) { mutableStateOf(false) }
    LaunchedEffect(initialUri) {
        if (!handledInitial) {
            handledInitial = true
            initialUri?.let(vm::open)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("MyDoc${if (state.dirty) " •" else ""}") }, actions = {
                if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                TextButton(onClick = { openLauncher.launch(arrayOf("*/*")) }) { Text("Open") }
                TextButton(onClick = {
                    saveExtension = when (state.tab) { 2 -> "xlsx"; 3 -> "pdf"; 4 -> "pptx"; else -> "docx" }
                    saveLauncher.launch("${state.name.substringBeforeLast('.', "Untitled")}.${saveExtension}")
                }) { Text("Save") }
            })
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, label ->
                    NavigationBarItem(selected = state.tab == i, onClick = { vm.setTab(i) }, icon = { Text(label.take(1)) }, label = { Text(label) })
                }
            }
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
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("MyDoc", style = MaterialTheme.typography.headlineLarge)
        Text("Offline-first document workspace • no account required")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onNew) { Text("New document") }
            OutlinedButton(onClick = onOpen) { Text("Open file") }
        }
        Text("Recent documents", style = MaterialTheme.typography.titleLarge)
        if (recent.isEmpty()) Text("No recent documents")
        recent.forEach { item ->
            OutlinedButton(onClick = { vm.open(Uri.parse(item.uri)) }, modifier = Modifier.fillMaxWidth()) { Text(item.name) }
        }
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
        OutlinedTextField(value = state.text, onValueChange = vm::setText, modifier = Modifier.fillMaxSize(), label = { Text(state.name) }, minLines = 20)
    }
}

@Composable
private fun SpreadsheetEditor(state: MyDocUiState, vm: MyDocViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Excel / XLSX", style = MaterialTheme.typography.headlineSmall)
        Text("Formula cells start with = and are preserved in XLSX export.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = vm::undo) { Text("Undo") }
            OutlinedButton(onClick = vm::redo) { Text("Redo") }
        }
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
}

@Composable private fun PdfEditor(uri: Uri?) {
    Column(Modifier.fillMaxSize()) {
        Text("PDF", style = MaterialTheme.typography.headlineSmall)
        if (uri == null) {
            Surface(Modifier.fillMaxSize(), tonalElevation = 2.dp) { Text("Open a PDF to view it.", Modifier.padding(18.dp)) }
        } else PdfViewer(uri)
    }
}

private data class PdfSession(val renderer: PdfRenderer?, val descriptor: ParcelFileDescriptor?, val error: String?)

@Composable
private fun PdfViewer(uri: Uri) {
    val context = LocalContext.current
    var page by remember(uri) { mutableIntStateOf(0) }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var session by remember(uri) { mutableStateOf(PdfSession(null, null, null)) }

    DisposableEffect(uri, context) {
        val result = openPdf(context, uri)
        session = result
        page = 0
        onDispose {
            result.renderer?.close()
            result.descriptor?.close()
        }
    }

    val renderer = session.renderer
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(enabled = renderer != null && page > 0, onClick = { page-- }) { Text("Previous") }
            Text(if (renderer == null) "Page —" else "Page ${page + 1} / ${renderer.pageCount}", modifier = Modifier.padding(top = 12.dp))
            OutlinedButton(enabled = renderer != null && page < renderer.pageCount - 1, onClick = { page++ }) { Text("Next") }
            OutlinedButton(onClick = { zoom = max(0.5f, zoom - 0.25f) }) { Text("−") }
            OutlinedButton(onClick = { zoom = min(3f, zoom + 0.25f) }) { Text("+") }
        }
        session.error?.let { Text("PDF error: $it") }
        if (renderer != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { PdfPageView(it) },
                update = { it.bind(renderer, page, zoom) }
            )
        }
    }
}

private fun openPdf(context: android.content.Context, uri: Uri): PdfSession {
    return try {
        val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return PdfSession(null, null, "Unable to open PDF")
        try {
            PdfSession(PdfRenderer(fd), fd, null)
        } catch (t: Throwable) {
            fd.close()
            PdfSession(null, null, t.message ?: "Unable to render PDF")
        }
    } catch (t: Throwable) {
        PdfSession(null, null, t.message ?: "Unable to open PDF")
    }
}

private class PdfPageView(context: android.content.Context) : android.view.View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var renderer: PdfRenderer? = null
    private var pageIndex = -1
    private var zoom = 1f
    private var bitmap: Bitmap? = null

    fun bind(renderer: PdfRenderer, pageIndex: Int, zoom: Float) {
        if (this.renderer !== renderer || this.pageIndex != pageIndex || this.zoom != zoom) {
            this.renderer = renderer
            this.pageIndex = pageIndex
            this.zoom = zoom
            renderPage()
        }
    }

    private fun renderPage() {
        bitmap?.recycle()
        bitmap = null
        val r = renderer ?: return
        if (pageIndex !in 0 until r.pageCount) return
        r.openPage(pageIndex).use { page ->
            val scale = min(zoom, 3f)
            val width = max(1, (page.width * scale).toInt())
            val height = max(1, (page.height * scale).toInt())
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                it.eraseColor(Color.WHITE)
                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val b = bitmap
        val width = if (b == null) MeasureSpec.getSize(widthMeasureSpec) else b.width
        val height = if (b == null) MeasureSpec.getSize(heightMeasureSpec) else b.height
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, paint) }
    }

    override fun onDetachedFromWindow() {
        bitmap?.recycle()
        bitmap = null
        super.onDetachedFromWindow()
    }
}

@Composable private fun MoreFeatures(vm: MyDocViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("PowerPoint / PPTX", style = MaterialTheme.typography.headlineSmall)
        Text("Create a basic slide package from the current document and export it as PPTX.")
        Text("Built-in foundations", style = MaterialTheme.typography.titleLarge)
        Text("• Offline storage and recent documents\n• Atomic exports\n• Bounded text cache\n• Background IO\n• Undo / redo\n• Crash-safe recovery snapshot\n• DOCX / XLSX / PPTX package generation\n• PDF export\n• Offline PDF page rendering")
        Button(onClick = { vm.setTab(1) }) { Text("Open editor") }
    }
}
