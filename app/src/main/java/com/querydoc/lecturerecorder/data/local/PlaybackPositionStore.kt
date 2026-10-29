package com.querydoc.lecturerecorder.data.local

import android.content.Context

object PlaybackPositionStore {
    private const val PREFS = "playback_positions"

    fun load(context: Context): Map<String, Long> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.mapNotNull { (key, value) ->
            val position = (value as? Long)?.takeIf { it > 0L } ?: return@mapNotNull null
            key to position
        }.toMap()
    }

    fun save(context: Context, sessionId: String, positionMs: Long) {
        if (positionMs <= 0L || sessionId.isBlank()) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(sessionId, positionMs)
            .apply()
    }

    fun clear(context: Context, sessionId: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(sessionId)
            .apply()
    }
}
