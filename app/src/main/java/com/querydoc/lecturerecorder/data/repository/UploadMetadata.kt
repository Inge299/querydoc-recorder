package com.querydoc.lecturerecorder.data.repository

data class UploadMetadata(
    val sessionId: String,
    val chunkIndex: Int,
    val chunkCount: Int?,
    val capturedAtMs: Long?,
    val durationMs: Long?,
    val language: String? = null,
) {
    companion object {
        fun parse(raw: String?): UploadMetadata? {
            val values = raw
                ?.split(';')
                ?.mapNotNull { part ->
                    val separator = part.indexOf('=')
                    if (separator <= 0) null else part.substring(0, separator) to part.substring(separator + 1)
                }
                ?.toMap()
                .orEmpty()
            val sessionId = values["sessionId"]?.takeIf { it.isNotBlank() } ?: return null
            val chunkIndex = values["chunkIndex"]?.toIntOrNull() ?: return null
            return UploadMetadata(
                sessionId = sessionId,
                chunkIndex = chunkIndex,
                chunkCount = values["chunkCount"]?.toIntOrNull()?.takeIf { it > 0 },
                capturedAtMs = values["capturedAtMs"]?.toLongOrNull(),
                durationMs = values["durationMs"]?.toLongOrNull(),
                language = values["language"]?.takeIf { it.isNotBlank() },
            )
        }
    }
}
