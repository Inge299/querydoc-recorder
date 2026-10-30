package com.querydoc.lecturerecorder.diagnostics

import android.content.Context

object CrashReporter {
    private const val PREFS = "crash_reporter"
    private const val KEY_PENDING = "pending_crash"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val stack = error.stackTraceToString().take(8_000)
                appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_PENDING, "${thread.name}: ${error.message}\n$stack")
                    .apply()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Отправляет отчёт о прошлом падении при следующем запуске. */
    fun flush(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = prefs.getString(KEY_PENDING, null) ?: return
        prefs.edit().remove(KEY_PENDING).apply()
        Analytics.track(context, "crash", mapOf("stack" to pending.take(8_000)))
    }
}
