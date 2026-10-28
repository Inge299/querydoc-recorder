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

    fun markUnseen(context: Context, sessionId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("unseen_$sessionId", true)
            .putLong("unseen_at_$sessionId", System.currentTimeMillis())
            .apply()
    }

    /** Returns the newest unread conspect and clears the unread flags. */
    fun consumeLatestUnseen(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val candidates = prefs.all
            .filterKeys { it.startsWith("unseen_") && it != "unseen_at_" }
            .filterValues { it == true }
            .keys
            .map { key -> key.removePrefix("unseen_") }
        if (candidates.isEmpty()) return null
        val latest = candidates.maxByOrNull { prefs.getLong("unseen_at_$it", 0L) }
        val editor = prefs.edit()
        candidates.forEach { sessionId ->
            editor.remove("unseen_$sessionId")
            editor.remove("unseen_at_$sessionId")
        }
        editor.apply()
        return latest
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
