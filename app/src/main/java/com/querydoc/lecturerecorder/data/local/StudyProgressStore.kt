package com.querydoc.lecturerecorder.data.local

import android.content.Context

object StudyProgressStore {
    private const val PREFS = "study_progress"

    fun checked(context: Context, sessionId: String): Set<Int> {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(sessionId, null)
            .orEmpty()
        return raw.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()
    }

    fun clear(context: Context, sessionId: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(sessionId).apply()
    }

    fun save(context: Context, sessionId: String, checked: Set<Int>) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(sessionId, checked.sorted().joinToString(","))
            .apply()
    }
}
