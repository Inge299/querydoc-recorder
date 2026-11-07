package com.querydoc.lecturerecorder.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.querydoc.lecturerecorder.AppGraph
import com.querydoc.lecturerecorder.data.local.AudioCache
import com.querydoc.lecturerecorder.data.local.UploadStatus
import com.querydoc.lecturerecorder.diagnostics.Diagnostics
import com.querydoc.lecturerecorder.recording.AudioStorageStore
import java.io.File

class CleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val dao = AppGraph.db(applicationContext).uploadQueueDao()
        val now = System.currentTimeMillis()
        val retentionMs = AudioStorageStore.retentionDays(applicationContext) * 24L * 60L * 60L * 1_000L
        val limitBytes = AudioStorageStore.limitBytes(applicationContext)

        val uploaded = dao.allAudioItems().filter { item ->
            item.status == UploadStatus.UPLOADED && File(item.filePath).exists()
        }
        var deleted = 0

        suspend fun deleteItem(item: com.querydoc.lecturerecorder.data.local.UploadQueueEntity): Boolean {
            val file = File(item.filePath)
            if (!file.exists() || file.delete()) {
                dao.deleteById(item.id)
                deleted++
                return true
            }
            Diagnostics.warn(applicationContext, "CleanupWorker: failed to delete ${item.filePath}")
            return false
        }

        // Expired by retention policy.
        uploaded.filter { item ->
            val savedAt = item.uploadedAtMs ?: item.createdAtMs
            savedAt + retentionMs < now
        }.forEach { deleteItem(it) }

        // Hard size cap: remove the oldest audio first.
        val remaining = dao.allAudioItems().filter { item ->
            item.status == UploadStatus.UPLOADED && File(item.filePath).exists()
        }
        var totalBytes = remaining.sumOf { File(it.filePath).length().coerceAtLeast(0L) }
        if (totalBytes > limitBytes) {
            remaining.sortedBy { it.uploadedAtMs ?: it.createdAtMs }.forEach { item ->
                if (totalBytes <= limitBytes) return@forEach
                totalBytes -= File(item.filePath).length().coerceAtLeast(0L)
                deleteItem(item)
            }
        }

        AudioCache.cleanup(applicationContext)
        Diagnostics.info(applicationContext, "CleanupWorker: cleaned $deleted file(s)")
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "cleanup-worker"
    }
}
