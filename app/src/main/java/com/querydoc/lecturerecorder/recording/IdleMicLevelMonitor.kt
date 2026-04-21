package com.querydoc.lecturerecorder.recording

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.querydoc.lecturerecorder.config.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class IdleMicLevelMonitor(
    private val app: Application
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var isRunning = false
    private var smoothedAverage = 0f
    private var smoothedAgcGain = 1f
    private var noiseFloor = 0.015f
    private var monitorStartedAtMs = 0L
    @Volatile
    private var farLecturerMode = false
    @Volatile
    private var environmentProfile = EnvironmentProfile.NORMAL

    fun setFarLecturerMode(enabled: Boolean) {
        farLecturerMode = enabled
    }

    fun setEnvironmentProfile(profile: EnvironmentProfile) {
        environmentProfile = profile
    }

    fun start() {
        if (isRunning) return
        if (!hasMicPermission()) return
        isRunning = true
        monitorStartedAtMs = System.currentTimeMillis()
        noiseFloor = 0.015f
        smoothedAverage = 0f
        smoothedAgcGain = 1f
        monitorJob = scope.launch {
            val sampleRate = AppConfig.Audio.sampleRateHz
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) {
                isRunning = false
                return@launch
            }
            val bufferSize = (minBuffer * 2).coerceAtLeast(2048)
            val audioRecord = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }.getOrNull()
            if (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                isRunning = false
                return@launch
            }

            val buffer = ShortArray(bufferSize / 2)
            try {
                audioRecord.startRecording()
                while (isActive && isRunning) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    var sumSq = 0f
                    for (i in 0 until read) {
                        val sample = buffer[i] / 32768f
                        sumSq += sample * sample
                    }
                    val rms = sqrt((sumSq / read).coerceAtLeast(0f)).coerceIn(0f, 1f)
                    val ageMs = System.currentTimeMillis() - monitorStartedAtMs
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
                    RecordingTelemetry.updateSoundLevel(softLimit(voiceOnly * smoothedAgcGain))
                    RecordingTelemetry.updateAverageSoundLevel(softLimit(smoothedAverage * smoothedAgcGain))
                    RecordingTelemetry.updateAgcGain(smoothedAgcGain)
                }
            } catch (_: Throwable) {
                // Ignore monitor errors; foreground recording path remains primary.
            } finally {
                runCatching { audioRecord.stop() }
                runCatching { audioRecord.release() }
                isRunning = false
            }
        }
    }

    fun stop() {
        isRunning = false
        monitorJob?.cancel()
        monitorJob = null
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun softLimit(value: Float): Float {
        val clamped = value.coerceIn(0f, 1f)
        return if (clamped <= 0.85f) clamped else (0.85f + (clamped - 0.85f) * 0.35f).coerceIn(0f, 1f)
    }
}
