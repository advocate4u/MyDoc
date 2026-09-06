package com.advocate4u.mypdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MyPdfApp(initialUri = intent?.data) }
    }
}

data class PdfNote(val page: Int, val x: Float, val y: Float, val text: String)

data class PdfState(
    val uri: Uri? = null,
    val pageCount: Int = 0,
    val page: Int = 0,
    val zoom: Float = 1f,
    val bitmap: Bitmap? = null,
    val removed: Set<Int> = emptySet(),
    val notes: List<PdfNote> = emptyList(),
    val drawing: List<Offset> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPdfApp(initialUri: Uri?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(PdfState()) }
    var descriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var mode by remember { mutableStateOf("view") }
    var noteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf(TextFieldValue("")) }
    var pendingNotePoint by remember { mutableStateOf(Offset.Zero) }

    fun closePdf() {
        runCatching { renderer?.close() }
        runCatching { descriptor?.close() }
        renderer = null
        descriptor = null
    }

    fun openPdf(uri: Uri) {
        closePdf()
        state = PdfState(uri = uri, loading = true)
        scope.launch {
            runCatching {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Unable to open PDF")
                val r = PdfRenderer(pfd)
                descriptor = pfd
                renderer = r
                state = state.copy(pageCount = r.pageCount, page = 0, loading = false, error = null)
                renderPage(r, 0, 1f) { bmp -> state = state.copy(bitmap = bmp) }
            }.onFailure { e ->
                closePdf()
                state = state.copy(loading = false, error = e.message ?: "Unable to open PDF")
            }
        }
    }

    fun renderCurrent(page: Int = state.page, zoom: Float = state.zoom) {
        val r = renderer ?: return
        scope.launch {
            val bmp = withContext(Dispatchers.Default) { renderPageBitmap(r, page, zoom) }
            state = state.copy(page = page, zoom = zoom, bitmap = bmp)
        }
    }

    fun exportPdf(target: Uri) {
        val source = state.uri ?: return
        state = state.copy(loading = true, error = null)
        scope.launch {
            val result = runCatching {
                exportEditedPdf(context, source, target, state.removed, state.notes)
            }
            state = state.copy(loading = false, error = result.exceptionOrNull()?.message)
            if (result.isSuccess) mode = "view"
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(::openPdf) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { it?.let(::exportPdf) }

    LaunchedEffect(initialUri) { if (initialUri != null) openPdf(initialUri) }
    DisposableEffect(Unit) { onDispose { closePdf() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MyPDF") },
                actions = {
                    TextButton(onClick = { open.launch(arrayOf("application/pdf")) }) { Text("Open") }
                    if (state.uri != null) TextButton(onClick = { save.launch("edited.pdf") }) { Text("Export") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = mode == "view", onClick = { mode = "view" }, icon = {}, label = { Text("View") })
                NavigationBarItem(selected = mode == "edit", onClick = { mode = "edit" }, icon = {}, label = { Text("Edit") })
                NavigationBarItem(selected = mode == "pages", onClick = { mode = "pages" }, icon = {}, label = { Text("Pages") })
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
            when (mode) {
                "pages" -> PageManager(state, onSelect = { renderCurrent(it); mode = "view" }, onDelete = { p -> state = state.copy(removed = state.removed + p) }, onRestore = { p -> state = state.copy(removed = state.removed - p) })
                else -> {
                    Toolbar(state, mode, onMode = { mode = it }, onZoom = { renderCurrent(state.page, it) }, onPrev = { if (state.page > 0) renderCurrent(state.page - 1) }, onNext = { if (state.page + 1 < state.pageCount) renderCurrent(state.page + 1) })
                    PdfCanvas(
                        state = state,
                        editable = mode == "edit",
                        onDraw = { points -> state = state.copy(drawing = points) },
                        onTap = { point -> pendingNotePoint = point; noteDialog = true }
                    )
                }
            }
        }
    }

    if (noteDialog) {
        Dialog(onDismissRequest = { noteDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp) {
                Column(Modifier.padding(20.dp)) {
                    Text("Add note", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(noteText, { noteText = it }, label = { Text("Note") }, minLines = 3)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { noteDialog = false }) { Text("Cancel") }
                        Button(onClick = {
                            if (noteText.text.isNotBlank()) state = state.copy(notes = state.notes + PdfNote(state.page, pendingNotePoint.x, pendingNotePoint.y, noteText.text))
                            noteText = TextFieldValue("")
                            noteDialog = false
                        }) { Text("Add") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Toolbar(state: PdfState, mode: String, onMode: (String) -> Unit, onZoom: (Float) -> Unit, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrev, enabled = state.page > 0) { Text("‹") }
        Text("${if (state.pageCount == 0) 0 else state.page + 1}/${state.pageCount}", modifier = Modifier.padding(horizontal = 4.dp))
        TextButton(onClick = onNext, enabled = state.page + 1 < state.pageCount) { Text("›") }
        TextButton(onClick = { onZoom(max(.5f, state.zoom - .25f)) }) { Text("−") }
        TextButton(onClick = { onZoom(1f) }) { Text("${(state.zoom * 100).toInt()}%") }
        TextButton(onClick = { onZoom(min(3f, state.zoom + .25f)) }) { Text("+") }
        if (mode == "edit") AssistChip(onClick = { onMode("view") }, label = { Text("Drawing / notes") })
        else AssistChip(onClick = { onMode("edit") }, label = { Text("Edit") })
    }
}

@Composable
private fun PdfCanvas(state: PdfState, editable: Boolean, onDraw: (List<Offset>) -> Unit, onTap: (Offset) -> Unit) {
    val bmp = state.bitmap
    var scale by remember(state.page) { mutableFloatStateOf(1f) }
    var offset by remember(state.page) { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (bmp == null) {
            Text("Open a PDF to begin", modifier = Modifier.align(Alignment.Center))
        } else {
            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(editable, bmp) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (!editable) {
                                scale = (scale * zoom).coerceIn(.5f, 4f)
                                offset += pan
                            }
                        }
                    }
                    .pointerInput(editable, bmp) {
                        if (editable) detectTapGestures(onTap = onTap)
                    }
            ) {
                val image = bmp.asImageBitmap()
                val w = image.width * state.zoom * scale
                val h = image.height * state.zoom * scale
                val left = (size.width - w) / 2f + offset.x
                val top = (size.height - h) / 2f + offset.y
                drawImage(image, dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()), dstSize = androidx.compose.ui.unit.IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)))
                state.notes.filter { it.page == state.page }.forEach { note ->
                    drawCircle(MaterialTheme.colorScheme.primary, 10f, Offset(left + note.x * state.zoom * scale, top + note.y * state.zoom * scale))
                }
            }
        }
    }
}

@Composable
private fun PageManager(state: PdfState, onSelect: (Int) -> Unit, onDelete: (Int) -> Unit, onRestore: (Int) -> Unit) {
    if (state.pageCount == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No PDF loaded") }
        return
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Pages", style = MaterialTheme.typography.titleLarge)
        Text("Tap a page to open it. Deleted pages are excluded when exporting.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.lazy.LazyColumn {
            items(state.pageCount) { page ->
                val deleted = page in state.removed
                ListItem(
                    headlineContent = { Text("Page ${page + 1}${if (deleted) " (deleted)" else ""}") },
                    supportingContent = { Text(if (deleted) "Will be removed on export" else "Included") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    trailingContent = {
                        if (deleted) TextButton(onClick = { onRestore(page) }) { Text("Restore") }
                        else TextButton(onClick = { onDelete(page) }) { Text("Delete") }
                    },
                    tonalElevation = 1.dp
                )
            }
        }
    }
}

private suspend fun renderPage(renderer: PdfRenderer, page: Int, zoom: Float, publish: (Bitmap) -> Unit) {
    val bitmap = withContext(Dispatchers.Default) { renderPageBitmap(renderer, page, zoom) }
    publish(bitmap)
}

private fun renderPageBitmap(renderer: PdfRenderer, page: Int, zoom: Float): Bitmap {
    require(page in 0 until renderer.pageCount) { "Invalid PDF page" }
    synchronized(renderer) {
        renderer.openPage(page).use { pdfPage ->
            val maxPixels = 12_000_000
            var width = (pdfPage.width * zoom).toInt().coerceAtLeast(1)
            var height = (pdfPage.height * zoom).toInt().coerceAtLeast(1)
            if (width.toLong() * height > maxPixels) {
                val factor = kotlin.math.sqrt(maxPixels.toDouble() / (width.toLong() * height)).toFloat()
                width = (width * factor).toInt().coerceAtLeast(1)
                height = (height * factor).toInt().coerceAtLeast(1)
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        }
    }
}

private fun exportEditedPdf(context: Context, source: Uri, target: Uri, removed: Set<Int>, notes: List<PdfNote>) {
    val pfd = context.contentResolver.openFileDescriptor(source, "r") ?: error("Unable to read source PDF")
    val renderer = PdfRenderer(pfd)
    val out = PdfDocument()
    try {
        for (pageIndex in 0 until renderer.pageCount) {
            if (pageIndex in removed) continue
            renderer.openPage(pageIndex).use { page ->
                val width = min(page.width, 1800)
                val height = (page.height.toFloat() * width / page.width).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                val info = PdfDocument.PageInfo.Builder(width, height, pageIndex + 1).create()
                val outPage = out.startPage(info)
                outPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 80, 180); textSize = 32f }
                notes.filter { it.page == pageIndex }.forEach { note ->
                    val x = (itSafe(note.x, page.width) / page.width) * width
                    val y = (itSafe(note.y, page.height) / page.height) * height
                    outPage.canvas.drawCircle(x, y, 12f, paint)
                    outPage.canvas.drawText(note.text.take(80), x + 16f, y, paint)
                }
                out.finishPage(outPage)
                bitmap.recycle()
            }
        }
        context.contentResolver.openOutputStream(target, "w")?.use { out.writeTo(it) } ?: error("Unable to write target PDF")
    } finally {
        out.close()
        renderer.close()
        pfd.close()
    }
}

private fun itSafe(value: Float, max: Int): Float = value.coerceIn(0f, max.toFloat())
