package com.advocate4u.mydoc.data

import android.content.ContentResolver
import android.net.Uri
import com.advocate4u.mydoc.DocumentEngine
import com.advocate4u.mydoc.core.AppDispatchers
import kotlinx.coroutines.withContext

class DocumentRepository(private val resolver: ContentResolver) {
    suspend fun read(uri: Uri, extension: String, cacheKey: String? = null): Result<String> = withContext(AppDispatchers.io) {
        runCatching {
            resolver.openInputStream(uri)?.use { input ->
                DocumentEngine.readText(input, extension, cacheKey)
            } ?: error("Unable to open document")
        }
    }

    suspend fun writePdf(text: String, output: java.io.File): Result<Unit> = withContext(AppDispatchers.io) {
        runCatching { DocumentEngine.writePdf(text, output) }
    }
}
