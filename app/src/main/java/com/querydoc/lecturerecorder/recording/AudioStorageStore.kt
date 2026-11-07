package com.querydoc.lecturerecorder.recording

import android.content.Context

object AudioStorageStore {
    const val DEFAULT_RETENTION_DAYS = 7
    const val DEFAULT_LIMIT_BYTES = 2L * 1024L * 1024L * 1024L
    private const val PREFS = "recorder_preferences"
    private const val KEY_DAYS = "audio_retention_days"
    private const val KEY_LIMIT = "audio_storage_limit_bytes"
    val retentionOptions = listOf(3, 7, 14)
    val limitOptionsGb = listOf(1, 2, 4, 8)

    fun retentionDays(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_DAYS, DEFAULT_RETENTION_DAYS)
            .coerceIn(1, 30)

    fun setRetentionDays(context: Context, days: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_DAYS, days.coerceIn(1, 30)).apply()
    }

    fun limitBytes(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LIMIT, DEFAULT_LIMIT_BYTES)
            .coerceAtLeast(256L * 1024L * 1024L)

    fun setLimitBytes(context: Context, bytes: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LIMIT, bytes.coerceAtLeast(256L * 1024L * 1024L)).apply()
    }
}
