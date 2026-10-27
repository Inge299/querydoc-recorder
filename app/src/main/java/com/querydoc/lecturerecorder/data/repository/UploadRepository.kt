package com.querydoc.lecturerecorder.data.repository

import com.querydoc.lecturerecorder.data.local.UploadQueueEntity

interface UploadRepository {
    suspend fun upload(item: UploadQueueEntity): UploadResult
}

sealed interface UploadResult {
    data object Success : UploadResult
    data object RetryableFailure : UploadResult
    data object AuthRequired : UploadResult
    data class PermanentFailure(val reason: String) : UploadResult
}
