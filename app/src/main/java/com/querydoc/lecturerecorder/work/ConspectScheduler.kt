package com.querydoc.lecturerecorder.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object ConspectScheduler {
    private const val PREFIX = "conspect-poll-"
    private const val PREFS = "querydoc_conspects"
    const val KEY_SESSION_ID = "sessionId"

    fun isCompleted(context: Context, sessionId: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("done_$sessionId", false)

    fun markCompleted(context: Context, sessionId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("done_$sessionId", true).apply()
    }

    fun enqueue(context: Context, sessionId: String) {
        if (isCompleted(context, sessionId)) return
        if (sessionId.isBlank()) return
        val request = OneTimeWorkRequestBuilder<ConspectWorker>()
            .setInputData(workDataOf(KEY_SESSION_ID to sessionId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 20, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(PREFIX + sessionId, ExistingWorkPolicy.KEEP, request)
    }
}
