package com.querydoc.lecturerecorder.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AudioBookmark(
    val id: String,
    val positionMs: Long,
    val label: String,
    val createdAtMs: Long,
)

object BookmarkStore {
    private const val PREFS = "audio_bookmarks"

    fun list(context: Context, sessionId: String): List<AudioBookmark> {
        val raw = prefs(context).getString(sessionId, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                AudioBookmark(
                    id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                    positionMs = item.optLong("positionMs").coerceAtLeast(0L),
                    label = item.optString("label"),
                    createdAtMs = item.optLong("createdAtMs"),
                )
            }.sortedBy { it.positionMs }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, sessionId: String, positionMs: Long, label: String): AudioBookmark {
        val normalized = positionMs.coerceAtLeast(0L)
        val bookmark = AudioBookmark(
            id = UUID.randomUUID().toString(),
            positionMs = normalized,
            label = label.trim().ifBlank { "Закладка ${formatTime(normalized)}" },
            createdAtMs = System.currentTimeMillis(),
        )
        val items = (list(context, sessionId) + bookmark).sortedBy { it.positionMs }
        save(context, sessionId, items)
        return bookmark
    }

    fun remove(context: Context, sessionId: String, id: String) {
        val items = list(context, sessionId).filterNot { it.id == id }
        save(context, sessionId, items)
    }

    private fun save(context: Context, sessionId: String, items: List<AudioBookmark>) {
        val array = JSONArray()
        items.forEach { bookmark ->
            array.put(
                JSONObject()
                    .put("id", bookmark.id)
                    .put("positionMs", bookmark.positionMs)
                    .put("label", bookmark.label)
                    .put("createdAtMs", bookmark.createdAtMs),
            )
        }
        prefs(context).edit().putString(sessionId, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1_000L).coerceAtLeast(0L)
        return "%02d:%02d".format(seconds / 60L, seconds % 60L)
    }
}
