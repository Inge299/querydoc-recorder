package com.querydoc.lecturerecorder.work

import android.content.Context
import com.querydoc.lecturerecorder.data.remote.ConspectSummary
import java.time.Instant

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

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun remove(context: Context, sessionId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("markdown_$sessionId")
            .remove("saved_at_$sessionId")
            .remove("transcript_$sessionId")
            .apply()
    }

    fun get(context: Context, sessionId: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("markdown_$sessionId", null)
            ?.takeIf { it.isNotBlank() }

    fun saveTranscript(context: Context, sessionId: String, transcript: String) {
        if (sessionId.isBlank() || transcript.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("transcript_$sessionId", transcript)
            .apply()
    }

    fun getTranscript(context: Context, sessionId: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("transcript_$sessionId", null)
            ?.takeIf { it.isNotBlank() }

    fun list(context: Context): List<ConspectSummary> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.keys
            .filter { it.startsWith("markdown_") }
            .mapNotNull { key ->
                val sessionId = key.removePrefix("markdown_")
                val markdown = prefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val lines = markdown.split('\n').map { it.trim() }
                val title = lines.firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim().orEmpty()
                    .ifBlank { "Конспект лекции" }
                val preview = lines.firstOrNull { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("- ") && !it.startsWith("* ") }
                    ?.replace("**", "")?.take(180).orEmpty()
                val savedAt = prefs.getLong("saved_at_$sessionId", 0L)
                ConspectSummary(
                    sessionId = sessionId,
                    title = title,
                    subject = "",
                    preview = preview,
                    completedAt = if (savedAt > 0L) Instant.ofEpochMilli(savedAt).toString() else "",
                    length = markdown.length,
                    markdown = markdown,
                )
            }
            .sortedByDescending { it.completedAt }
    }
}
