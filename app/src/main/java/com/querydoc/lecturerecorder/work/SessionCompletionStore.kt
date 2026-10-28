package com.querydoc.lecturerecorder.work

import android.content.Context

/** Marks sessions for which the recording service has flushed its final chunk. */
object SessionCompletionStore {
    private const val PREFS = "querydoc_sessions"

    fun markComplete(context: Context, sessionId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("complete_$sessionId", true)
            .apply()
    }

    fun isComplete(context: Context, sessionId: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("complete_$sessionId", false)
}
