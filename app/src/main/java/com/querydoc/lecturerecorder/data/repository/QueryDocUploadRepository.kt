package com.querydoc.lecturerecorder.data.repository

import com.querydoc.lecturerecorder.data.local.UploadQueueEntity
import com.querydoc.lecturerecorder.data.remote.ApiClient
import com.querydoc.lecturerecorder.data.remote.ApiResult
import java.io.File

class QueryDocUploadRepository(private val api: ApiClient) : UploadRepository {
    override suspend fun upload(item: UploadQueueEntity): UploadResult {
        val file = File(item.filePath)
        if (!file.exists() || file.length() == 0L) return UploadResult.PermanentFailure("Empty or missing file")
        val metadata = UploadMetadata.parse(item.metadataJson)
            ?: return UploadResult.PermanentFailure("Missing session metadata")

        return when (
            val presign = api.presign(
                chunkId = item.id,
                sessionId = metadata.sessionId,
                chunkIndex = metadata.chunkIndex,
                chunkCount = metadata.chunkCount,
                sizeBytes = file.length(),
                mimeType = item.mimeType,
                fileName = item.displayName,
                durationMs = metadata.durationMs,
                capturedAtMs = metadata.capturedAtMs,
                language = metadata.language,
            )
        ) {
            is ApiResult.Failure -> mapFailure(presign)
            is ApiResult.Ok -> {
                val upload = presign.value
                if (upload.duplicate) return UploadResult.Success
                when (val put = api.putObject(upload, file)) {
                    is ApiResult.Failure -> mapFailure(put)
                    is ApiResult.Ok -> when (val complete = api.complete(upload.uploadSessionId, item.id)) {
                        is ApiResult.Failure -> mapFailure(complete)
                        is ApiResult.Ok -> UploadResult.Success
                    }
                }
            }
        }
    }

    private fun mapFailure(failure: ApiResult.Failure): UploadResult = when {
        failure.status == 401 || failure.status == 403 -> UploadResult.AuthRequired
        failure.retryable -> UploadResult.RetryableFailure
        else -> UploadResult.PermanentFailure("${failure.code}: ${failure.message}")
    }
}
