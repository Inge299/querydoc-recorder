package com.querydoc.lecturerecorder.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.querydoc.lecturerecorder.AppGraph
import com.querydoc.lecturerecorder.config.AppConfig
import com.querydoc.lecturerecorder.data.local.UploadStatus
import com.querydoc.lecturerecorder.data.repository.UploadResult
import com.querydoc.lecturerecorder.diagnostics.Diagnostics
import java.io.File

class UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Diagnostics.info(applicationContext, "UploadWorker: started")
        val dao = AppGraph.db(applicationContext).uploadQueueDao()
        val repository = AppGraph.uploadRepository()

        while (true) {
            val item = dao.nextPending() ?: return Result.success()
            val now = System.currentTimeMillis()
            dao.updateStatus(item.id, UploadStatus.UPLOADING, item.retryCount, now)

            val file = File(item.filePath)
            val result = repository.upload(file, item.mediaType, item.mimeType)
            when (result) {
                UploadResult.Success -> {
                    val updateTs = System.currentTimeMillis()
                    dao.markUploaded(item.id, updateTs, updateTs)
                    Diagnostics.info(applicationContext, "UploadWorker: uploaded ${item.displayName}")
                    if (!AppConfig.Storage.keepLocalAudioAfterUpload && file.exists()) {
                        file.delete()
                    }
                }

                UploadResult.RetryableFailure -> {
                    dao.updateStatus(item.id, UploadStatus.FAILED, item.retryCount + 1, System.currentTimeMillis())
                    Diagnostics.warn(applicationContext, "UploadWorker: retryable failure ${item.displayName}")
                    return Result.retry()
                }

                is UploadResult.PermanentFailure -> {
                    dao.updateStatus(item.id, UploadStatus.FAILED, item.retryCount + 1, System.currentTimeMillis())
                    Diagnostics.warn(applicationContext, "UploadWorker: permanent failure ${item.displayName}")
                }
            }
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "upload-queue-worker"
    }
}
