package com.querydoc.lecturerecorder.data.repository

import com.querydoc.lecturerecorder.data.local.MediaType
import java.io.File

class StubUploadRepository : UploadRepository {
    override suspend fun upload(file: File, mediaType: MediaType, mimeType: String): UploadResult {
        if (!file.exists() || file.length() == 0L) {
            return UploadResult.PermanentFailure("Empty or missing file")
        }
        return UploadResult.Success
    }
}
