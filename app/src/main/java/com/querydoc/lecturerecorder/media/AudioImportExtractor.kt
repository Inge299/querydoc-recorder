package com.querydoc.lecturerecorder.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class ImportedAudio(
    val file: File,
    val fileName: String,
    val mimeType: String,
    val durationMs: Long,
)

object AudioImportExtractor {
    suspend fun prepare(context: Context, uri: Uri): Result<ImportedAudio> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri).orEmpty()
            val displayName = queryDisplayName(context, uri) ?: "import-${System.currentTimeMillis()}"
            val durationMs = readDurationMs(context, uri)
            val output = File(context.cacheDir, "import-${UUID.randomUUID()}")
            when {
                mime.startsWith("audio/") -> {
                    resolver.openInputStream(uri)?.use { input ->
                        output.outputStream().use { input.copyTo(it) }
                    } ?: error("Не удалось открыть аудиофайл")
                    ImportedAudio(output, displayName, mime, durationMs)
                }
                mime.startsWith("video/") -> {
                    val extracted = File(context.cacheDir, "import-${UUID.randomUUID()}.m4a")
                    extractAudioTrack(context, uri, extracted)
                    ImportedAudio(extracted, displayName, "audio/mp4", durationMs)
                }
                else -> error("Выберите аудио- или видеофайл")
            }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun readDurationMs(context: Context, uri: Uri): Long {
        return runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            }
        }.getOrDefault(0L)
    }

    private fun extractAudioTrack(context: Context, uri: Uri, output: File) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrack = -1
            var format: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(index)
                val mime = candidate.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrack = index
                    format = candidate
                    break
                }
            }
            require(audioTrack >= 0 && format != null) { "В видео нет аудиодорожки" }
            extractor.selectTrack(audioTrack)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackIndex = muxer.addTrack(format)
            muxer.start()
            val maxInput = runCatching { format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }.getOrDefault(256 * 1024)
            val buffer = java.nio.ByteBuffer.allocateDirect(maxInput.coerceAtLeast(64 * 1024))
            val info = MediaCodec.BufferInfo()
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(0, size, extractor.sampleTime, extractor.sampleFlags)
                muxer.writeSampleData(trackIndex, buffer, info)
                extractor.advance()
            }
            muxer.stop()
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }
}
