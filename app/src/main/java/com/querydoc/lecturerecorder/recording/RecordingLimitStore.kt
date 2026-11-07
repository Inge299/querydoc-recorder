package com.querydoc.lecturerecorder.recording

import android.content.Context

object RecordingLimitStore {
    const val DEFAULT_MINUTES = 90
    const val HARD_MAX_MINUTES = 120
    const val EXTEND_MINUTES = 15
    private const val PREFS = "recorder_preferences"
    private const val KEY = "max_recording_minutes"
    private val allowed = listOf(30, 60, 90, 120)

    fun current(context: Context): Int {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, DEFAULT_MINUTES)
        return raw.coerceIn(30, HARD_MAX_MINUTES)
    }

    fun set(context: Context, minutes: Int) {
        val normalized = allowed.minByOrNull { kotlin.math.abs(it - minutes) } ?: DEFAULT_MINUTES
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY, normalized).apply()
    }

    fun extend(context: Context): Int {
        val next = (current(context) + EXTEND_MINUTES).coerceAtMost(HARD_MAX_MINUTES)
        set(context, next)
        return next
    }
}
