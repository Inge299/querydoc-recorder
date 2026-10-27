package com.querydoc.lecturerecorder.recording

import android.annotation.SuppressLint

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.SystemClock
import com.querydoc.lecturerecorder.config.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.sqrt

class ChunkedAacRecorder(
    private val outputDir: File,
    private val scope: CoroutineScope,
    private val onChunkReady: (File) -> Unit,
    private val onSessionFinished: (SessionMetrics) -> Unit
) {
    @Volatile
    private var running = false
    @Volatile
    private var paused = false

    private var worker: Job? = null

    private var noiseFloor = 0.015f
    private var smoothedAverage = 0f
    private var smoothedAgcGain = 1f

    private var farLecturerMode = false
    private var environmentProfile = EnvironmentProfile.NORMAL
    private var startedAtMs = 0L
    private var samplesProcessed = 0L
    private var clippedSamples = 0L
    private var sumVoiceLevel = 0.0
    private var sumGain = 0.0
    private var peakVoiceLevel = 0f

    fun start(farMode: Boolean, profile: EnvironmentProfile) {
        if (running) return
        running = true
        paused = false
        farLecturerMode = farMode
        environmentProfile = profile
        startedAtMs = SystemClock.elapsedRealtime()
        noiseFloor = 0.015f
        smoothedAverage = 0f
        smoothedAgcGain = 1f
        samplesProcessed = 0L
        clippedSamples = 0L
        sumVoiceLevel = 0.0
        sumGain = 0.0
        peakVoiceLevel = 0f
        worker = scope.launch {
            runRecorderLoop()
        }
    }

    fun pause() {
        paused = true
        RecordingTelemetry.updateSoundLevel(0f)
        RecordingTelemetry.updateAverageSoundLevel(0f)
    }

    fun resume() {
        paused = false
    }

    suspend fun stop() {
        if (!running) return
        running = false
        worker?.cancelAndJoin()
        worker = null
        RecordingTelemetry.reset()
    }

    @SuppressLint("MissingPermission") // RECORD_AUDIO is requested by MainActivity and checked before starting the service.
    private suspend fun runRecorderLoop() {
        outputDir.mkdirs()
        val sampleRate = AppConfig.Audio.sampleRateHz
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return
        val bufferSize = (minBuffer * 2).coerceAtLeast(4096)

        val source = if (farLecturerMode) MediaRecorder.AudioSource.CAMCORDER else MediaRecorder.AudioSource.MIC
        val audioRecord = AudioRecord(
            source,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { audioRecord.release() }
            return
        }

        val codec = MediaCodec.createEncoderByType(MIME_TYPE_AUDIO)
        val format = MediaFormat.createAudioFormat(MIME_TYPE_AUDIO, sampleRate, AppConfig.Audio.channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AppConfig.Audio.bitRateBps)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        var codecFormat: MediaFormat? = null
        var chunk = newChunkContext(outputDir)
        var chunkStartedAt = SystemClock.elapsedRealtime()
        var totalSamples = 0L
        val pcm = ShortArray(bufferSize / 2)
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            audioRecord.startRecording()
            while (scope.isActive && running) {
                val read = audioRecord.read(pcm, 0, pcm.size)
                if (read <= 0) {
                    delay(8)
                    continue
                }

                if (paused) {
                    RecordingTelemetry.updateSoundLevel(0f)
                    RecordingTelemetry.updateAverageSoundLevel(0f)
                    delay(24)
                    continue
                }

                val gain = updateTelemetryAndGain(pcm, read)
                val processed = applyGainAndLimiter(pcm, read, gain)
                feedInput(codec, processed, read, totalSamples, sampleRate)
                totalSamples += read.toLong()
                drainOutput(codec, bufferInfo) { encoded, info ->
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) return@drainOutput
                    if (codecFormat == null) {
                        codecFormat = codec.outputFormat
                    }
                    if (!chunk.started) {
                        codecFormat?.let { fmt ->
                            chunk.trackIndex = chunk.muxer.addTrack(fmt)
                            chunk.muxer.start()
                            chunk.started = true
                        }
                    }
                    if (chunk.started && info.size > 0) {
                        chunk.muxer.writeSampleData(chunk.trackIndex, encoded, info)
                    }
                }

                val elapsed = SystemClock.elapsedRealtime() - chunkStartedAt
                if (elapsed >= AppConfig.Audio.chunkDurationSec * 1_000L) {
                    closeChunk(chunk)
                    if (chunk.file.exists() && chunk.file.length() > 0L) onChunkReady(chunk.file)
                    chunk = newChunkContext(outputDir)
                    chunkStartedAt = SystemClock.elapsedRealtime()
                    codecFormat?.let { fmt ->
                        chunk.trackIndex = chunk.muxer.addTrack(fmt)
                        chunk.muxer.start()
                        chunk.started = true
                    }
                }
            }

            // Flush encoder tail.
            queueEos(codec, totalSamples, sampleRate)
            drainOutput(codec, bufferInfo) { encoded, info ->
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) return@drainOutput
                if (codecFormat == null) {
                    codecFormat = codec.outputFormat
                }
                if (!chunk.started) {
                    codecFormat?.let { fmt ->
                        chunk.trackIndex = chunk.muxer.addTrack(fmt)
                        chunk.muxer.start()
                        chunk.started = true
                    }
                }
                if (chunk.started && info.size > 0) {
                    chunk.muxer.writeSampleData(chunk.trackIndex, encoded, info)
                }
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return@drainOutput
            }
        } finally {
            runCatching { audioRecord.stop() }
            runCatching { audioRecord.release() }
            runCatching { closeChunk(chunk) }
            if (chunk.file.exists() && chunk.file.length() > 0L) {
                onChunkReady(chunk.file)
            }
            runCatching { codec.stop() }
            runCatching { codec.release() }
            val durationSec = ((SystemClock.elapsedRealtime() - startedAtMs) / 1000L).coerceAtLeast(1L)
            val meanVoice = if (samplesProcessed > 0L) (sumVoiceLevel / samplesProcessed).toFloat() else 0f
            val meanGain = if (samplesProcessed > 0L) (sumGain / samplesProcessed).toFloat() else 1f
            val clipRatio = if (samplesProcessed > 0L) clippedSamples.toFloat() / samplesProcessed.toFloat() else 0f
            onSessionFinished(
                SessionMetrics(
                    durationSec = durationSec,
                    meanVoiceLevel = meanVoice,
                    peakVoiceLevel = peakVoiceLevel,
                    meanGain = meanGain,
                    clipRatio = clipRatio,
                    noiseFloor = noiseFloor,
                    profile = environmentProfile,
                    farLecturerMode = farLecturerMode
                )
            )
        }
    }

    private fun updateTelemetryAndGain(pcm: ShortArray, read: Int): Float {
        var sumSq = 0f
        for (i in 0 until read) {
            val s = pcm[i] / 32768f
            sumSq += s * s
        }
        val rms = sqrt((sumSq / read).coerceAtLeast(0f)).coerceIn(0f, 1f)
        val ageMs = SystemClock.elapsedRealtime() - startedAtMs
        val floorAlphaBase = if (ageMs < 12_000L) 0.05f else 0.01f
        val floorAlpha = when (environmentProfile) {
            EnvironmentProfile.QUIET -> floorAlphaBase * 0.8f
            EnvironmentProfile.NORMAL -> floorAlphaBase
            EnvironmentProfile.NOISY -> floorAlphaBase * 1.4f
        }
        val floorMax = when (environmentProfile) {
            EnvironmentProfile.QUIET -> 0.05f
            EnvironmentProfile.NORMAL -> 0.08f
            EnvironmentProfile.NOISY -> 0.12f
        }
        noiseFloor = (noiseFloor * (1f - floorAlpha) + rms * floorAlpha).coerceIn(0.005f, floorMax)
        val voiceOnly = ((rms - noiseFloor) / (1f - noiseFloor)).coerceIn(0f, 1f)
        smoothedAverage = (smoothedAverage * 0.9f + voiceOnly * 0.1f).coerceIn(0f, 1f)
        val baseTarget = if (farLecturerMode) 0.58f else 0.45f
        val target = when (environmentProfile) {
            EnvironmentProfile.QUIET -> baseTarget + 0.04f
            EnvironmentProfile.NORMAL -> baseTarget
            EnvironmentProfile.NOISY -> baseTarget - 0.03f
        }
        val baseMaxGain = if (farLecturerMode) 3.2f else 2.5f
        val maxGain = when (environmentProfile) {
            EnvironmentProfile.QUIET -> baseMaxGain + 0.3f
            EnvironmentProfile.NORMAL -> baseMaxGain
            EnvironmentProfile.NOISY -> baseMaxGain - 0.4f
        }.coerceAtLeast(1.8f)
        val desiredGain = if (voiceOnly < 0.02f) 1f else (target / voiceOnly).coerceIn(0.8f, maxGain)
        smoothedAgcGain = (smoothedAgcGain * 0.92f + desiredGain * 0.08f).coerceIn(0.8f, maxGain)
        samplesProcessed += read.toLong()
        sumVoiceLevel += voiceOnly * read
        sumGain += smoothedAgcGain * read
        if (voiceOnly > peakVoiceLevel) peakVoiceLevel = voiceOnly
        RecordingTelemetry.updateSoundLevel(softLimit(voiceOnly * smoothedAgcGain))
        RecordingTelemetry.updateAverageSoundLevel(softLimit(smoothedAverage * smoothedAgcGain))
        RecordingTelemetry.updateAgcGain(smoothedAgcGain)
        return smoothedAgcGain
    }

    private fun applyGainAndLimiter(pcm: ShortArray, read: Int, gain: Float): ShortArray {
        val out = ShortArray(read)
        for (i in 0 until read) {
            val raw = (pcm[i] / 32768f) * gain
            if (abs(raw) > 0.98f) clippedSamples++
            var x = raw
            // Soft limiter to preserve intelligibility and avoid clipping.
            x /= (1f + abs(x) * 0.6f)
            x = x.coerceIn(-1f, 1f)
            out[i] = (x * 32767f).toInt().toShort()
        }
        return out
    }

    private fun feedInput(
        codec: MediaCodec,
        pcm: ShortArray,
        read: Int,
        totalSamples: Long,
        sampleRate: Int
    ) {
        val inIndex = codec.dequeueInputBuffer(10_000)
        if (inIndex < 0) return
        val input = codec.getInputBuffer(inIndex) ?: return
        input.clear()
        for (i in 0 until read) {
            input.putShort(pcm[i])
        }
        val ptsUs = totalSamples * 1_000_000L / sampleRate
        codec.queueInputBuffer(inIndex, 0, read * 2, ptsUs, 0)
    }

    private fun queueEos(codec: MediaCodec, totalSamples: Long, sampleRate: Int) {
        val inIndex = codec.dequeueInputBuffer(10_000)
        if (inIndex < 0) return
        val ptsUs = totalSamples * 1_000_000L / sampleRate
        codec.queueInputBuffer(inIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
    }

    private inline fun drainOutput(
        codec: MediaCodec,
        info: MediaCodec.BufferInfo,
        writer: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
    ) {
        while (true) {
            val outIndex = codec.dequeueOutputBuffer(info, 0)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                outIndex >= 0 -> {
                    val out = codec.getOutputBuffer(outIndex)
                    if (out != null) {
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        writer(out, info)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                }
            }
        }
    }

    private fun newChunkContext(dir: File): ChunkContext {
        val file = AudioChunkFileNamer.createChunkFile(dir)
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        return ChunkContext(file = file, muxer = muxer)
    }

    private fun closeChunk(chunk: ChunkContext) {
        runCatching {
            if (chunk.started) chunk.muxer.stop()
        }
        runCatching { chunk.muxer.release() }
    }

    private fun softLimit(value: Float): Float {
        val clamped = value.coerceIn(0f, 1f)
        return if (clamped <= 0.85f) clamped else (0.85f + (clamped - 0.85f) * 0.35f).coerceIn(0f, 1f)
    }

    private data class ChunkContext(
        val file: File,
        val muxer: MediaMuxer,
        var trackIndex: Int = -1,
        var started: Boolean = false
    )

    companion object {
        private const val MIME_TYPE_AUDIO = "audio/mp4a-latm"
    }

    data class SessionMetrics(
        val durationSec: Long,
        val meanVoiceLevel: Float,
        val peakVoiceLevel: Float,
        val meanGain: Float,
        val clipRatio: Float,
        val noiseFloor: Float,
        val profile: EnvironmentProfile,
        val farLecturerMode: Boolean
    )
}
