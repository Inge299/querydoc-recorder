package com.querydoc.lecturerecorder.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.querydoc.lecturerecorder.AppGraph
import com.querydoc.lecturerecorder.config.AppConfig
import com.querydoc.lecturerecorder.data.local.UploadStatus
import com.querydoc.lecturerecorder.data.repository.UploadMetadata
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
        val repository = AppGraph.uploadRepository(applicationContext)

        while (true) {
            val item = dao.nextPending() ?: break
            val now = System.currentTimeMillis()
            dao.updateStatus(item.id, UploadStatus.UPLOADING, item.retryCount, now)

            val file = File(item.filePath)
            val result = repository.upload(item)
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

                UploadResult.AuthRequired -> {
                    dao.updateStatus(item.id, UploadStatus.FAILED, item.retryCount + 1, System.currentTimeMillis())
                    Diagnostics.warn(applicationContext, "UploadWorker: authorization required")
                    return Result.failure()
                }

                is UploadResult.PermanentFailure -> {
                    dao.updateStatus(item.id, UploadStatus.FAILED, item.retryCount + 1, System.currentTimeMillis())
                    Diagnostics.warn(applicationContext, "UploadWorker: permanent failure ${item.displayName}")
                }
            }
        }

        scheduleCompletedSessions(dao)
        return Result.success()
    }

    private suspend fun scheduleCompletedSessions(dao: com.querydoc.lecturerecorder.data.local.UploadQueueDao) {
        runCatching {
            val grouped = dao.allAudioItems()
                .mapNotNull { item -> UploadMetadata.parse(item.metadataJson)?.let { meta -> meta to item } }
                .groupBy { (meta, _) -> meta.sessionId }
            grouped.forEach { (sessionId, entries) ->
                val expectedChunks = entries.mapNotNull { (meta, _) -> meta.chunkCount }.maxOrNull()
                val allUploaded = entries.all { (_, item) -> item.status == UploadStatus.UPLOADED }
                val enoughChunks = expectedChunks == null || entries.size >= expectedChunks
                if (allUploaded && enoughChunks) {
                    Diagnostics.info(applicationContext, "UploadWorker: session complete, scheduling conspect $sessionId")
                    ConspectScheduler.enqueue(applicationContext, sessionId)
                }
            }
        }.onFailure { error ->
            Diagnostics.warn(applicationContext, "UploadWorker: conspect scheduling failed ${error.message}")
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "upload-queue-worker"
    }
}
