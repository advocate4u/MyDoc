package com.advocate4u.mypdf

import android.content.Context
import android.graphics.Bitmap
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MyPdfApp(initialUri = intent?.data) }
    }
}

data class PdfNote(val page: Int, val x: Float, val y: Float, val text: String)

data class PdfState(val uri: Uri? = null, val pageCount: Int = 0, val page: Int = 0, val zoom: Float = 1f, val bitmap: Bitmap? = null, val removed: Set<Int> = emptySet(), val notes: List<PdfNote> = emptyList(), val loading: Boolean = false, val error: String? = null)

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
                val bitmap = withContext(Dispatchers.Default) { renderPageBitmap(r, 0, 1f) }
                state = PdfState(uri = uri, pageCount = r.pageCount, bitmap = bitmap)
            }.onFailure { e -> closePdf(); state = state.copy(loading = false, error = e.message ?: "Unable to open PDF") }
        }
    }

    fun renderCurrent(page: Int = state.page, zoom: Float = state.zoom) {
        val r = renderer ?: return
        scope.launch {
            val bmp = runCatching { withContext(Dispatchers.Default) { renderPageBitmap(r, page, zoom) } }.getOrNull()
            if (bmp != null) state = state.copy(page = page, zoom = zoom, bitmap = bmp, error = null)
        }
    }

    fun exportPdf(target: Uri) {
        val source = state.uri ?: return
        state = state.copy(loading = true, error = null)
        scope.launch {
            val result = runCatching { exportEditedPdf(context, source, target, state.removed, state.notes) }
            state = state.copy(loading = false, error = result.exceptionOrNull()?.message)
            if (result.isSuccess) mode = "view"
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(::openPdf) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { it?.let(::exportPdf) }
    LaunchedEffect(initialUri) { if (initialUri != null) openPdf(initialUri) }
    DisposableEffect(Unit) { onDispose { closePdf() } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("MyPDF") }, actions = { TextButton(onClick = { open.launch(arrayOf("application/pdf")) }) { Text("Open") }; if (state.uri != null) TextButton(onClick = { save.launch("edited.pdf") }) { Text("Export") } }) },
        bottomBar = { NavigationBar { NavigationBarItem(mode == "view", { mode = "view" }, icon = {}, label = { Text("View") }); NavigationBarItem(mode == "edit", { mode = "edit" }, icon = {}, label = { Text("Edit") }); NavigationBarItem(mode == "pages", { mode = "pages" }, icon = {}, label = { Text("Pages") }) } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
            if (mode == "pages") PageManager(state, { renderCurrent(it); mode = "view" }, { p -> state = state.copy(removed = state.removed + p) }, { p -> state = state.copy(removed = state.removed - p) })
            else {
                Toolbar(state, mode, { mode = it }, { renderCurrent(state.page, it) }, { if (state.page > 0) renderCurrent(state.page - 1) }, { if (state.page + 1 < state.pageCount) renderCurrent(state.page + 1) })
                PdfCanvas(state, mode == "edit") { point -> pendingNotePoint = point; noteDialog = true }
            }
        }
    }

    if (noteDialog) Dialog(onDismissRequest = { noteDialog = false }) {
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp)) {
                Text("Add note", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(12.dp))
                OutlinedTextField(noteText, { noteText = it }, label = { Text("Note") }, minLines = 3); Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { noteDialog = false }) { Text("Cancel") }
                    Button(onClick = { if (noteText.text.isNotBlank()) state = state.copy(notes = state.notes + PdfNote(state.page, pendingNotePoint.x, pendingNotePoint.y, noteText.text)); noteText = TextFieldValue(""); noteDialog = false }) { Text("Add") }
                }
            }
        }
    }
}

@Composable private fun Toolbar(state: PdfState, mode: String, onMode: (String) -> Unit, onZoom: (Float) -> Unit, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrev, enabled = state.page > 0) { Text("‹") }; Text("${if (state.pageCount == 0) 0 else state.page + 1}/${state.pageCount}")
        TextButton(onClick = onNext, enabled = state.page + 1 < state.pageCount) { Text("›") }; TextButton(onClick = { onZoom((state.zoom - .25f).coerceAtLeast(.5f)) }) { Text("−") }
        TextButton(onClick = { onZoom(1f) }) { Text("${(state.zoom * 100).toInt()}%") }; TextButton(onClick = { onZoom((state.zoom + .25f).coerceAtMost(3f)) }) { Text("+") }
        AssistChip(onClick = { onMode(if (mode == "edit") "view" else "edit") }, label = { Text(if (mode == "edit") "Notes" else "Edit") })
    }
}

@Composable private fun PdfCanvas(state: PdfState, editable: Boolean, onTap: (Offset) -> Unit) {
    val bmp = state.bitmap
    var gestureScale by remember(state.page) { mutableFloatStateOf(1f) }
    var offset by remember(state.page) { mutableStateOf(Offset.Zero) }
    val noteColor = MaterialTheme.colorScheme.primary

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (bmp == null) {
            Text("Open a PDF to begin", modifier = Modifier.align(Alignment.Center))
        } else {
            Canvas(
                Modifier
                    .fillMaxSize()
                    // One- or two-finger pan, plus true two-finger pinch-to-zoom.
                    // Pan follows the fingers in every direction and is clamped so the
                    // page can be moved left/right/top/bottom without losing it.
                    .pointerInput(state.page, state.zoom, bmp) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val oldScale = gestureScale
                            val newScale = (oldScale * zoom).coerceIn(0.5f, 4f)
                            val imageWidth = bmp.width * state.zoom * newScale
                            val imageHeight = bmp.height * state.zoom * newScale
                            val oldWidth = bmp.width * state.zoom * oldScale
                            val oldHeight = bmp.height * state.zoom * oldScale

                            // Keep the point under the fingers stable while pinching.
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val oldLeft = (size.width - oldWidth) / 2f + offset.x
                            val oldTop = (size.height - oldHeight) / 2f + offset.y
                            val localX = (centroid.x - oldLeft) / oldScale
                            val localY = (centroid.y - oldTop) / oldScale
                            var newOffset = Offset(
                                centroid.x - (size.width - imageWidth) / 2f - localX * newScale + pan.x,
                                centroid.y - (size.height - imageHeight) / 2f - localY * newScale + pan.y
                            )

                            val maxX = if (imageWidth > size.width) (imageWidth - size.width) / 2f else 0f
                            val maxY = if (imageHeight > size.height) (imageHeight - size.height) / 2f else 0f
                            newOffset = Offset(
                                newOffset.x.coerceIn(-maxX, maxX),
                                newOffset.y.coerceIn(-maxY, maxY)
                            )
                            gestureScale = newScale
                            offset = newOffset
                        }
                    }
                    .pointerInput(editable, bmp) {
                        if (editable) detectTapGestures(onTap = onTap)
                    }
            ) {
                val image = bmp.asImageBitmap()
                val totalScale = state.zoom * gestureScale
                val w = image.width * totalScale
                val h = image.height * totalScale
                val left = (size.width - w) / 2f + offset.x
                val top = (size.height - h) / 2f + offset.y
                drawImage(
                    image,
                    dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
                )
                state.notes.filter { it.page == state.page }.forEach { note ->
                    drawCircle(noteColor, 10f, Offset(left + note.x * totalScale, top + note.y * totalScale))
                }
            }
        }
    }
}

@Composable private fun PageManager(state: PdfState, onSelect: (Int) -> Unit, onDelete: (Int) -> Unit, onRestore: (Int) -> Unit) {
    if (state.pageCount == 0) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No PDF loaded") }; return }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Pages", style = MaterialTheme.typography.titleLarge); Text("Deleted pages are excluded when exporting.", style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.lazy.LazyColumn { items((0 until state.pageCount).toList()) { page -> val deleted = page in state.removed; ListItem(
            headlineContent = { Text("Page ${page + 1}${if (deleted) " (deleted)" else ""}") }, supportingContent = { Text(if (deleted) "Will be removed on export" else "Included") }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), trailingContent = { if (deleted) TextButton({ onRestore(page) }) { Text("Restore") } else TextButton({ onDelete(page) }) { Text("Delete") } }
        ) } }
    }
}

private fun renderPageBitmap(renderer: PdfRenderer, page: Int, zoom: Float): Bitmap {
    require(page in 0 until renderer.pageCount) { "Invalid PDF page" }
    synchronized(renderer) { renderer.openPage(page).use { pdfPage ->
        val maxPixels = 12_000_000L; var width = (pdfPage.width * zoom).toInt().coerceAtLeast(1); var height = (pdfPage.height * zoom).toInt().coerceAtLeast(1); val pixels = width.toLong() * height
        if (pixels > maxPixels) { val factor = sqrt(maxPixels.toDouble() / pixels).toFloat(); width = (width * factor).toInt().coerceAtLeast(1); height = (height * factor).toInt().coerceAtLeast(1) }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.WHITE); pdfPage.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
    } }
}

private fun exportEditedPdf(context: Context, source: Uri, target: Uri, removed: Set<Int>, notes: List<PdfNote>) {
    val pfd = context.contentResolver.openFileDescriptor(source, "r") ?: error("Unable to read source PDF"); val renderer = PdfRenderer(pfd); val out = PdfDocument()
    try { var outputPageNumber = 1; for (pageIndex in 0 until renderer.pageCount) { if (pageIndex in removed) continue; renderer.openPage(pageIndex).use { page ->
        val width = min(page.width, 1800); val height = (page.height.toFloat() * width / page.width).toInt().coerceAtLeast(1); val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); bitmap.eraseColor(Color.WHITE); page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        val outPage = out.startPage(PdfDocument.PageInfo.Builder(width, height, outputPageNumber++).create()); outPage.canvas.drawBitmap(bitmap, 0f, 0f, null); val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 80, 180); textSize = 32f }
        notes.filter { it.page == pageIndex }.forEach { note -> val x = note.x.coerceIn(0f, page.width.toFloat()) / page.width * width; val y = note.y.coerceIn(0f, page.height.toFloat()) / page.height * height; outPage.canvas.drawCircle(x, y, 12f, paint); outPage.canvas.drawText(note.text.take(80), x + 16f, y, paint) }
        out.finishPage(outPage); bitmap.recycle()
    } }; context.contentResolver.openOutputStream(target, "w")?.use { out.writeTo(it) } ?: error("Unable to write target PDF")
    } finally { out.close(); renderer.close(); pfd.close() }
}
