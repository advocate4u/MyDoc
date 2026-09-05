package com.advocate4u.mydoc.data

import android.content.ContentResolver
import android.net.Uri
import com.advocate4u.mydoc.DocumentEngine
import com.advocate4u.mydoc.core.AppDispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DocumentRepository(private val resolver: ContentResolver) {
    suspend fun read(uri: Uri, extension: String, cacheKey: String? = null): Result<String> = withContext(AppDispatchers.io) {
        runCatching {
            resolver.openInputStream(uri)?.use { input -> DocumentEngine.readText(input, extension, cacheKey) }
                ?: error("Unable to open document")
        }
    }

    suspend fun exportDocx(text: String, output: File, bold: Boolean = false, italic: Boolean = false, underline: Boolean = false): Result<Unit> =
        writeBytes(output) { DocumentEngine.writeDocx(text, bold, italic, underline) }

    suspend fun exportXlsx(cells: List<List<String>>, output: File): Result<Unit> =
        writeBytes(output) { DocumentEngine.writeXlsx(cells) }

    suspend fun exportPptx(text: String, output: File): Result<Unit> =
        writeBytes(output) { DocumentEngine.writePptx(text) }

    suspend fun writePdf(text: String, output: File): Result<Unit> = withContext(AppDispatchers.io) {
        runCatching { DocumentEngine.writePdf(text, output) }
    }

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
