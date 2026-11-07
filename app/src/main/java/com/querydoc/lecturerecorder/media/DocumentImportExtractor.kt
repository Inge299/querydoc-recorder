package com.querydoc.lecturerecorder.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class ImportedDocument(
    val file: File,
    val fileName: String,
    val mimeType: String,
)

object DocumentImportExtractor {
    private val allowed = setOf("pdf", "docx", "txt", "md", "markdown")

    suspend fun prepare(context: Context, uri: Uri): Result<ImportedDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "document-${System.currentTimeMillis()}"
            val extension = displayName.substringAfterLast('.', "").lowercase()
            require(extension in allowed) { "Поддерживаются TXT, MD, DOCX и PDF" }
            val mime = resolver.getType(uri).orEmpty().ifBlank {
                when (extension) {
                    "pdf" -> "application/pdf"
                    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    "md", "markdown" -> "text/markdown"
                    else -> "text/plain"
                }
            }
            val output = File(context.cacheDir, "document-${UUID.randomUUID()}.$extension")
            resolver.openInputStream(uri)?.use { input ->
                output.outputStream().use { input.copyTo(it) }
            } ?: error("Не удалось открыть документ")
            require(output.length() > 0L) { "Документ пустой" }
            require(output.length() <= 100L * 1024L * 1024L) { "Документ больше 100 МБ" }
            ImportedDocument(output, displayName, mime)
        }
    }
}
