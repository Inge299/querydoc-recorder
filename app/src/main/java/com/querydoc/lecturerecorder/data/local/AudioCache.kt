package com.querydoc.lecturerecorder.data.local

import android.content.Context
import com.querydoc.lecturerecorder.data.remote.AudioSourceChunk
import java.io.File

data class CachedAudio(
    val paths: List<String>,
    val durationsMs: List<Long>,
    val bytes: Long,
)

/**
 * Persistent cache for audio streamed from S3. A downloaded lecture is kept for
 * seven days so opening the reader again does not download it a second time.
 */
object AudioCache {
    private const val ROOT = "audio_cache"
    private const val MANIFEST = "manifest.txt"
    private const val TTL_MS = 7L * 24L * 60L * 60L * 1_000L

    fun get(context: Context, sessionId: String): CachedAudio? {
        val dir = sessionDir(context, sessionId)
        if (!dir.isDirectory) return null
        val manifest = File(dir, MANIFEST)
        if (!manifest.isFile || System.currentTimeMillis() - manifest.lastModified() > TTL_MS) {
            clear(context, sessionId)
            return null
        }
        val paths = mutableListOf<String>()
        val durations = mutableListOf<Long>()
        runCatching {
            manifest.readLines().forEach { line ->
                val parts = line.split('\t')
                if (parts.size < 3) return@forEach
                val file = File(dir, parts[2])
                if (!file.isFile || file.length() == 0L) return null
                paths += file.absolutePath
                durations += parts[1].toLongOrNull()?.coerceAtLeast(1_000L) ?: 1_000L
            }
        }.onFailure { return null }
        if (paths.isEmpty()) return null
        return CachedAudio(paths, durations, paths.sumOf { File(it).length() })
    }

    suspend fun store(
        context: Context,
        sessionId: String,
        chunks: List<AudioSourceChunk>,
        download: suspend (url: String, destination: File) -> Boolean,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): CachedAudio? {
        if (chunks.isEmpty()) return null
        val dir = sessionDir(context, sessionId)
        if (!dir.mkdirs()) return null
        val lines = mutableListOf<String>()
        var alreadyCached = 0
        for ((index, chunk) in chunks.withIndex()) {
            val file = File(dir, String.format("chunk_%04d.m4a", index))
            if (file.isFile && file.length() > 0L) {
                lines += "${index}\t${chunk.durationMs.coerceAtLeast(1_000L)}\t${file.name}"
                alreadyCached++
            }
        }
        onProgress(alreadyCached, chunks.size)
        for ((index, chunk) in chunks.withIndex()) {
            val file = File(dir, String.format("chunk_%04d.m4a", index))
            if (file.isFile && file.length() > 0L) continue
            val part = File(dir, file.name + ".part")
            part.delete()
            if (!download(chunk.url, part)) {
                part.delete()
                return null
            }
            if (part.length() == 0L || !part.renameTo(file)) {
                part.delete()
                return null
            }
            lines += "${index}\t${chunk.durationMs.coerceAtLeast(1_000L)}\t${file.name}"
            onProgress(index + 1, chunks.size)
        }
        File(dir, MANIFEST).writeText(lines.joinToString("\n"))
        return get(context, sessionId)
    }
    fun clear(context: Context, sessionId: String) {
        sessionDir(context, sessionId).deleteRecursively()
    }

    fun cleanup(context: Context) {
        val root = File(context.filesDir, ROOT)
        val now = System.currentTimeMillis()
        root.listFiles()?.forEach { dir ->
            if (!dir.isDirectory) return@forEach
            val manifest = File(dir, MANIFEST)
            if (!manifest.isFile || now - manifest.lastModified() > TTL_MS) {
                dir.deleteRecursively()
            }
        }
    }

    private fun sessionDir(context: Context, sessionId: String): File {
        val safe = sessionId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(File(context.filesDir, ROOT), safe)
    }
}
