package com.querydoc.lecturerecorder.data.repository

import com.querydoc.lecturerecorder.data.local.MediaType
import java.io.File

interface UploadRepository {
    suspend fun upload(file: File, mediaType: MediaType, mimeType: String): UploadResult
}

sealed interface UploadResult {
    data object Success : UploadResult
    data object RetryableFailure : UploadResult
    data class PermanentFailure(val reason: String) : UploadResult
}
