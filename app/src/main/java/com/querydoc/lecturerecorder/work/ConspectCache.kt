package com.querydoc.lecturerecorder.work

import android.content.Context

/** Local copy of a completed conspect, so it opens even without a network. */
object ConspectCache {
    private const val PREFS = "querydoc_conspect_cache"

    fun save(context: Context, sessionId: String, markdown: String) {
        if (sessionId.isBlank() || markdown.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("markdown_$sessionId", markdown)
            .putLong("saved_at_$sessionId", System.currentTimeMillis())
            .apply()
    }

    fun get(context: Context, sessionId: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("markdown_$sessionId", null)
            ?.takeIf { it.isNotBlank() }
}
