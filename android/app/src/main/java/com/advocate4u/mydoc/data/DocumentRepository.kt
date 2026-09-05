package com.advocate4u.mydoc.data

import android.content.ContentResolver
import android.net.Uri
import com.advocate4u.mydoc.DocumentEngine
import com.advocate4u.mydoc.core.AppDispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DocumentRepository(private val resolver: ContentResolver) {
    suspend fun read(uri: Uri, extension: String, cacheKey: String? = null): Result<String> = withContext(AppDispatchers.io) {
        runCatching { resolver.openInputStream(uri)?.use { DocumentEngine.readText(it, extension, cacheKey) } ?: error("Unable to open document") }
    }

    suspend fun exportDocx(text: String, output: File, bold: Boolean = false, italic: Boolean = false, underline: Boolean = false): Result<Unit> = writeBytes(output) { DocumentEngine.writeDocx(text, bold, italic, underline) }
    suspend fun exportXlsx(cells: List<List<String>>, output: File): Result<Unit> = writeBytes(output) { DocumentEngine.writeXlsx(cells) }
    suspend fun exportPptx(text: String, output: File): Result<Unit> = writeBytes(output) { DocumentEngine.writePptx(text) }

    suspend fun exportToUri(uri: Uri, extension: String, text: String, cells: List<List<String>> = emptyList(), bold: Boolean = false, italic: Boolean = false, underline: Boolean = false): Result<Unit> =
        withContext(AppDispatchers.io) {
            runCatching {
                val bytes = when (extension.lowercase()) {
                    "docx" -> DocumentEngine.writeDocx(text, bold, italic, underline)
                    "xlsx" -> DocumentEngine.writeXlsx(cells)
                    "pptx" -> DocumentEngine.writePptx(text)
                    else -> text.toByteArray(Charsets.UTF_8)
                }
                resolver.openOutputStream(uri, "w")?.use { it.write(bytes) } ?: error("Unable to save document")
            }
        }

    suspend fun exportPdfToUri(uri: Uri, text: String): Result<Unit> = withContext(AppDispatchers.io) {
        runCatching {
            val temp = File.createTempFile("mydoc-", ".pdf")
            try {
                DocumentEngine.writePdf(text, temp)
                resolver.openOutputStream(uri, "w")?.use { output -> temp.inputStream().use { it.copyTo(output) } } ?: error("Unable to save PDF")
            } finally { temp.delete() }
        }
    }

    suspend fun writePdf(text: String, output: File): Result<Unit> = withContext(AppDispatchers.io) { runCatching { DocumentEngine.writePdf(text, output) } }

    private suspend fun writeBytes(output: File, producer: () -> ByteArray): Result<Unit> = withContext(AppDispatchers.io) {
        runCatching {
            output.parentFile?.mkdirs()
            val temp = File(output.parentFile, ".${output.name}.tmp")
            try {
                temp.outputStream().use { it.write(producer()) }
                if (!temp.renameTo(output)) { temp.copyTo(output, overwrite = true); temp.delete() }
            } finally { if (temp.exists()) temp.delete() }
        }
    }
}
