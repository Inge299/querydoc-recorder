package com.querydoc.lecturerecorder.data.repository

import com.querydoc.lecturerecorder.data.local.UploadQueueEntity
import java.io.File

/** Kept for previews and tests; production wiring uses QueryDocUploadRepository. */
class StubUploadRepository : UploadRepository {
    override suspend fun upload(item: UploadQueueEntity): UploadResult {
        val file = File(item.filePath)
        if (!file.exists() || file.length() == 0L) {
            return UploadResult.PermanentFailure("Empty or missing file")
        }
        return UploadResult.Success
    }
}
