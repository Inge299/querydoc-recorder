package com.querydoc.lecturerecorder.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns

data class ImportRequest(
    val uri: Uri,
    val subjectHint: String = "",
)

object FolderImport {
    private val supported = setOf(
        "pdf", "docx", "txt", "md", "markdown",
        "mp3", "m4a", "wav", "ogg", "opus", "aac", "flac", "mp4", "mov",
    )

    fun collect(context: Context, treeUri: Uri): List<ImportRequest> {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootName = subjectFromFolder(queryName(context, treeUri, rootId)) ?: "Материалы"
        val result = mutableListOf<ImportRequest>()
        walk(context, treeUri, rootId, rootName, 0, result)
        return result
    }

    private fun walk(
        context: Context,
        treeUri: Uri,
        documentId: String,
        subjectHint: String,
        depth: Int,
        result: MutableList<ImportRequest>,
    ) {
        if (depth > 6 || result.size >= 100) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        runCatching {
            context.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1).orEmpty()
                    val mime = cursor.getString(2).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val nextSubject = subjectFromFolder(name) ?: subjectHint
                        walk(context, treeUri, id, nextSubject, depth + 1, result)
                    } else {
                        val extension = name.substringAfterLast('.', "").lowercase()
                        if (extension !in supported) continue
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                        result += ImportRequest(uri, subjectHint)
                    }
                }
            }
        }
    }

    private fun queryName(context: Context, treeUri: Uri, documentId: String): String {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("")
    }

    private fun subjectFromFolder(raw: String): String? {
        val value = raw
            .replace(Regex("^\\d+[\\s._-]*"), "")
            .replace(Regex("[_]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return value.take(80).takeIf { it.isNotBlank() }
    }
}
