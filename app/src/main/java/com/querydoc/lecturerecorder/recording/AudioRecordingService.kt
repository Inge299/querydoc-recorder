package com.querydoc.lecturerecorder.recording

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.IBinder
import android.os.PowerManager
import com.querydoc.lecturerecorder.AppGraph
import com.querydoc.lecturerecorder.R
import com.querydoc.lecturerecorder.config.AppConfig
import com.querydoc.lecturerecorder.data.local.MediaType
import com.querydoc.lecturerecorder.data.local.UploadQueueEntity
import com.querydoc.lecturerecorder.data.local.UploadStatus
import com.querydoc.lecturerecorder.diagnostics.Diagnostics
import com.querydoc.lecturerecorder.work.SessionCompletionStore
import com.querydoc.lecturerecorder.work.UploadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class AudioRecordingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recorder: ChunkedAacRecorder? = null
    private var isRecording = false
    private var isPaused = false
    private var farLecturerMode = true
    private var pauseOnInterruption = true
    private var environmentProfile = EnvironmentProfile.NORMAL
    private var recognitionLanguage = "ru-RU"
    private var sessionId: String = UUID.randomUUID().toString()
    private var chunkCounter: Int = 0
    private val pendingChunkInserts = AtomicInteger(0)
    private var wakeLock: PowerManager.WakeLock? = null
    private var resumeAfterFocusGain = false
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private var audioFocusRequest: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (pauseOnInterruption && isRecording && !isPaused) {
                    pauseRecordingInternal(byInterruption = true)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (pauseOnInterruption && resumeAfterFocusGain && isRecording && isPaused) {
                    resumeAfterFocusGain = false
                    resumeRecordingInternal(byInterruption = true)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                if (pauseOnInterruption && isRecording && !isPaused) {
                    pauseRecordingInternal(byInterruption = true)
                }
                resumeAfterFocusGain = false
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                farLecturerMode = intent.getBooleanExtra(EXTRA_FAR_LECTURER_MODE, true)
                pauseOnInterruption = intent.getBooleanExtra(EXTRA_PAUSE_ON_INTERRUPTION, true)
                recognitionLanguage = intent.getStringExtra(EXTRA_RECOGNITION_LANGUAGE) ?: "ru-RU"
                environmentProfile = EnvironmentProfile.fromName(
                    intent.getStringExtra(EXTRA_ENVIRONMENT_PROFILE)
                )
                startRecordingIfNeeded()
            }
            ACTION_PAUSE -> pauseRecordingIfNeeded()
            ACTION_RESUME -> resumeRecordingIfNeeded()
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopRecording()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startRecordingIfNeeded() {
        if (isRecording) return
        Diagnostics.info(applicationContext, "AudioRecordingService: start requested (AudioRecord + MediaCodec)")
        NotificationHelper.ensureChannel(this)
        startForeground(
            NotificationHelper.recordingNotificationId,
            NotificationHelper.buildRecordingNotification(this, getString(R.string.notification_recording_text))
        )
        acquireWakeLock()
        requestAudioFocus()

        val outputDir = File(filesDir, "audio_chunks").apply { mkdirs() }
        sessionId = UUID.randomUUID().toString()
        chunkCounter = 0
        val chunkRecorder = ChunkedAacRecorder(
            outputDir = outputDir,
            scope = serviceScope,
            onChunkReady = { file -> enqueueAudioChunk(file) },
            onSessionFinished = { metrics ->
                Diagnostics.info(
                    applicationContext,
                    "Audio session metrics: durationSec=${metrics.durationSec}, " +
                        "meanVoice=${"%.3f".format(metrics.meanVoiceLevel)}, " +
                        "peakVoice=${"%.3f".format(metrics.peakVoiceLevel)}, " +
                        "meanGain=${"%.3f".format(metrics.meanGain)}, " +
                        "clipRatio=${"%.4f".format(metrics.clipRatio)}, " +
                        "noiseFloor=${"%.4f".format(metrics.noiseFloor)}, " +
                        "profile=${metrics.profile.name}, farMode=${metrics.farLecturerMode}"
                )
            }
        )
        chunkRecorder.start(farMode = farLecturerMode, profile = environmentProfile)
        recorder = chunkRecorder
        isRecording = true
        isPaused = false
    }

    private fun pauseRecordingIfNeeded() {
        if (!isRecording || isPaused) return
        pauseRecordingInternal(byInterruption = false)
    }

    private fun resumeRecordingIfNeeded() {
        if (!isRecording || !isPaused) return
        resumeAfterFocusGain = false
        resumeRecordingInternal(byInterruption = false)
    }

    private fun stopRecording() {
        if (!isRecording) {
            abandonAudioFocus()
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        isRecording = false
        isPaused = false
        val active = recorder
        recorder = null
        val completedSessionId = sessionId
        serviceScope.launch {
            runCatching { active?.stop() }
            // The final chunk is inserted from a callback. Wait for that insert
            // before marking the session complete, otherwise UploadWorker would
            // see only the first chunk and generate a conspect too early.
            while (pendingChunkInserts.get() > 0) delay(25)
            SessionCompletionStore.markComplete(applicationContext, completedSessionId)
            UploadScheduler.enqueue(applicationContext)
            abandonAudioFocus()
            releaseWakeLock()
            Diagnostics.info(applicationContext, "AudioRecordingService: stopped")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun pauseRecordingInternal(byInterruption: Boolean) {
        if (!isRecording || isPaused) return
        recorder?.pause()
        isPaused = true
        resumeAfterFocusGain = byInterruption
        Diagnostics.info(
            applicationContext,
            if (byInterruption) "AudioRecordingService: auto-paused by interruption" else "AudioRecordingService: paused"
        )
    }

    private fun resumeRecordingInternal(byInterruption: Boolean) {
        if (!isRecording || !isPaused) return
        recorder?.resume()
        isPaused = false
        Diagnostics.info(
            applicationContext,
            if (byInterruption) "AudioRecordingService: auto-resumed after interruption" else "AudioRecordingService: resumed"
        )
    }

    private fun requestAudioFocus() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener)
            .setAcceptsDelayedFocusGain(false)
            .build()
        val result = audioManager.requestAudioFocus(audioFocusRequest!!)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Diagnostics.warn(applicationContext, "AudioRecordingService: audio focus not granted")
        }
    }

    private fun abandonAudioFocus() {
        val request = audioFocusRequest ?: return
        runCatching { audioManager.abandonAudioFocusRequest(request) }
        audioFocusRequest = null
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LectureRecorder:Recording").apply {
            setReferenceCounted(false)
            acquire(6L * 60L * 60L * 1000L)
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock
        wakeLock = null
        if (lock?.isHeld == true) {
            runCatching { lock.release() }
        }
    }

    private fun enqueueAudioChunk(file: File) {
        if (!file.exists() || file.length() == 0L) return
        val chunkIndex = chunkCounter++
        val chunkSessionId = sessionId
        pendingChunkInserts.incrementAndGet()
        serviceScope.launch {
            try {
                val now = System.currentTimeMillis()
                val deleteAfter = now + AppConfig.Storage.cleanupAfterDays * 24L * 60L * 60L * 1_000L
                AppGraph.db(applicationContext).uploadQueueDao().upsert(
                    UploadQueueEntity(
                        id = UUID.randomUUID().toString(),
                        displayName = file.name,
                        mediaType = MediaType.AUDIO_CHUNK,
                        filePath = file.absolutePath,
                        mimeType = "audio/mp4",
                        status = UploadStatus.PENDING,
                        retryCount = 0,
                        createdAtMs = now,
                        updatedAtMs = now,
                        uploadedAtMs = null,
                        deleteAfterMs = deleteAfter,
                        metadataJson = "sessionId=$chunkSessionId;chunkIndex=$chunkIndex;chunkCount=0;capturedAtMs=$now;durationMs=" + (AppConfig.Audio.chunkDurationSec * 1000L) + ";language=$recognitionLanguage"
                    )
                )
                Diagnostics.info(applicationContext, "AudioRecordingService: enqueued ${file.name}")
                UploadScheduler.enqueue(applicationContext)
            } finally {
                pendingChunkInserts.decrementAndGet()
            }
        }
    }

    companion object {
        const val ACTION_START = "com.querydoc.lecturerecorder.START_RECORDING"
        const val ACTION_PAUSE = "com.querydoc.lecturerecorder.PAUSE_RECORDING"
        const val ACTION_RESUME = "com.querydoc.lecturerecorder.RESUME_RECORDING"
        const val ACTION_STOP = "com.querydoc.lecturerecorder.STOP_RECORDING"
        const val EXTRA_FAR_LECTURER_MODE = "extra_far_lecturer_mode"
        const val EXTRA_ENVIRONMENT_PROFILE = "extra_environment_profile"
        const val EXTRA_PAUSE_ON_INTERRUPTION = "extra_pause_on_interruption"
        const val EXTRA_RECOGNITION_LANGUAGE = "extra_recognition_language"
    }
}
