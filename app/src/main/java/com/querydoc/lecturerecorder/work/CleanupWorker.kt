package com.querydoc.lecturerecorder.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.querydoc.lecturerecorder.AppGraph
import com.querydoc.lecturerecorder.diagnostics.Diagnostics
import java.io.File

class CleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val dao = AppGraph.db(applicationContext).uploadQueueDao()
        val now = System.currentTimeMillis()
        val candidates = dao.findCleanupCandidates(nowMs = now, limit = CLEANUP_BATCH_SIZE)
        if (candidates.isEmpty()) {
            Diagnostics.info(applicationContext, "CleanupWorker: no expired files")
            return Result.success()
        }

        var deleted = 0
        for (item in candidates) {
            val file = File(item.filePath)
            if (!file.exists() || file.delete()) {
                dao.deleteById(item.id)
                deleted++
            } else {
                Diagnostics.warn(applicationContext, "CleanupWorker: failed to delete ${item.filePath}")
            }
        }
        Diagnostics.info(applicationContext, "CleanupWorker: cleaned $deleted file(s)")
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "cleanup-worker"
        private const val CLEANUP_BATCH_SIZE = 50
    }
}
