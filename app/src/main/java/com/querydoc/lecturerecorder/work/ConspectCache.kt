package com.querydoc.lecturerecorder.work

import android.content.Context
import com.querydoc.lecturerecorder.data.remote.ConspectSummary
import java.time.Instant

/** Local copy of a completed conspect, so it opens even without a network. */
object ConspectCache {
    private const val PREFS = "querydoc_conspect_cache"

    fun save(context: Context, sessionId: String, markdown: String, owner: String) {
        if (sessionId.isBlank() || markdown.isBlank() || owner.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("markdown_$sessionId", markdown)
            .putString("owner_$sessionId", owner)
            .putLong("saved_at_$sessionId", System.currentTimeMillis())
            .apply()
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun clearOwner(context: Context, owner: String) {
        if (owner.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        prefs.all.keys.forEach { key ->
            if (key.startsWith("markdown_") || key.startsWith("transcript_")) {
                val sessionId = key.substringAfter('_')
                if (prefs.getString("owner_$sessionId", "") == owner) {
                    editor.remove(key)
                    if (key.startsWith("markdown_")) editor.remove("saved_at_$sessionId")
                }
            }
        }
        editor.apply()
    }

    fun remove(context: Context, sessionId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("markdown_$sessionId")
            .remove("owner_$sessionId")
            .remove("saved_at_$sessionId")
            .remove("transcript_$sessionId")
            .apply()
    }

    fun get(context: Context, sessionId: String, owner: String): String? {
        if (owner.isBlank()) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString("owner_$sessionId", "") != owner) return null
        return prefs.getString("markdown_$sessionId", null)?.takeIf { it.isNotBlank() }
    }

    fun saveTranscript(context: Context, sessionId: String, transcript: String, owner: String) {
        if (sessionId.isBlank() || transcript.isBlank() || owner.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("transcript_$sessionId", transcript)
            .putString("owner_$sessionId", owner)
            .apply()
    }

    fun getTranscript(context: Context, sessionId: String, owner: String): String? {
        if (owner.isBlank()) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString("owner_$sessionId", "") != owner) return null
        return prefs.getString("transcript_$sessionId", null)?.takeIf { it.isNotBlank() }
    }

    fun list(context: Context, owner: String): List<ConspectSummary> {
        if (owner.isBlank()) return emptyList()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.keys
            .filter { it.startsWith("markdown_") }
            .filter { prefs.getString("owner_${it.removePrefix("markdown_")}", "") == owner }
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
