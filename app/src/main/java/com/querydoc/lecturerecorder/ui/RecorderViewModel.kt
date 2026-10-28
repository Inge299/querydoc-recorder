package com.querydoc.lecturerecorder.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.querydoc.lecturerecorder.AppGraph
import com.querydoc.lecturerecorder.data.remote.ApiResult
import com.querydoc.lecturerecorder.data.remote.ConspectState
import com.querydoc.lecturerecorder.config.AppConfig
import com.querydoc.lecturerecorder.data.local.UploadQueueEntity
import com.querydoc.lecturerecorder.data.local.UploadStatus
import com.querydoc.lecturerecorder.recording.AudioRecordingService
import com.querydoc.lecturerecorder.recording.EnvironmentProfile
import com.querydoc.lecturerecorder.recording.IdleMicLevelMonitor
import com.querydoc.lecturerecorder.recording.RecordingTelemetry
import com.querydoc.lecturerecorder.work.ConspectCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecorderViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val app = getApplication<Application>()
    private val dao = AppGraph.db(app).uploadQueueDao()
    private val api = AppGraph.apiClient(app)
    private val conspectJobs = mutableSetOf<String>()
    private val idleMicMonitor = IdleMicLevelMonitor(app)
    private val dateFormatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru"))
    private val durationCacheMs = mutableMapOf<String, Long>()
    private val prefs = app.getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)
    private val initialFarLecturerMode = prefs.getBoolean(KEY_FAR_MODE, true)
    private val initialPauseOnInterruptions = prefs.getBoolean(KEY_PAUSE_ON_INTERRUPTION, true)
    private val initialEnvironmentProfile = EnvironmentProfile.fromName(
        prefs.getString(KEY_ENVIRONMENT_PROFILE, EnvironmentProfile.NORMAL.name)
    )

    private val _state = MutableStateFlow(
        RecorderUiState(
            hasMicPermission = false,
            farLecturerMode = initialFarLecturerMode,
            pauseOnInterruptions = initialPauseOnInterruptions,
            environmentProfile = initialEnvironmentProfile,
            energySaverEnabled = AppConfig.Power.enableEnergySaverByDefault,
            statusText = "Готово"
        )
    )
    val state: StateFlow<RecorderUiState> = _state.asStateFlow()

    private var recordingsById: Map<String, RecordingListItem> = emptyMap()
    private var elapsedJob: Job? = null
    private var storageJob: Job? = null
    private var recordingStartedAtMs = 0L
    private var pausedAtMs = 0L
    private var pausedDurationMs = 0L

    private var mediaPlayer: MediaPlayer? = null
    private var playbackMonitorJob: Job? = null
    private var playbackSessionId: String? = null
    private var playbackChunkPaths: List<String> = emptyList()
    private var playbackChunkDurationsMs: List<Long> = emptyList()
    private var playbackChunkIndex: Int = 0
    private var playbackBaseMs: Long = 0L

    init {
        idleMicMonitor.setFarLecturerMode(initialFarLecturerMode)
        idleMicMonitor.setEnvironmentProfile(initialEnvironmentProfile)
        idleMicMonitor.start()
        observeQueue()
        observePendingUploads()
        observeTelemetry()
        startStorageTicker()
    }

    fun onPermissionResult(result: Map<String, Boolean>) {
        val hasMic = result[Manifest.permission.RECORD_AUDIO] == true
        _state.update {
            it.copy(
                hasMicPermission = hasMic,
                statusText = if (hasMic) "Готово" else "Нет доступа к микрофону"
            )
        }
        if (!hasMic) {
            idleMicMonitor.stop()
        } else if (!state.value.isRecording) {
            idleMicMonitor.start()
        }
    }

    fun startRecording() {
        if (!state.value.hasMicPermission) {
            _state.update { it.copy(statusText = "Разрешите доступ к микрофону") }
            return
        }
        if (state.value.isRecording) return
        stopPlaybackInternal(clearItem = true)
        val startIntent = Intent(app, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.ACTION_START
            putExtra(AudioRecordingService.EXTRA_FAR_LECTURER_MODE, state.value.farLecturerMode)
            putExtra(AudioRecordingService.EXTRA_ENVIRONMENT_PROFILE, state.value.environmentProfile.name)
            putExtra(AudioRecordingService.EXTRA_PAUSE_ON_INTERRUPTION, state.value.pauseOnInterruptions)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(startIntent)
        } else {
            app.startService(startIntent)
        }
        recordingStartedAtMs = System.currentTimeMillis()
        pausedDurationMs = 0L
        pausedAtMs = 0L
        idleMicMonitor.stop()
        _state.update {
            it.copy(
                isRecording = true,
                isRecordingPaused = false,
                elapsedSeconds = 0L,
                statusText = "Идет запись"
            )
        }
        startElapsedTicker()
    }

    fun pauseOrResumeRecording() {
        if (!state.value.isRecording) return
        val isPaused = state.value.isRecordingPaused
        if (isPaused) {
            if (pausedAtMs > 0L) {
                pausedDurationMs += (System.currentTimeMillis() - pausedAtMs)
                pausedAtMs = 0L
            }
            app.startService(Intent(app, AudioRecordingService::class.java).apply {
                action = AudioRecordingService.ACTION_RESUME
            })
            idleMicMonitor.stop()
            _state.update { it.copy(isRecordingPaused = false, statusText = "Идет запись") }
        } else {
            pausedAtMs = System.currentTimeMillis()
            app.startService(Intent(app, AudioRecordingService::class.java).apply {
                action = AudioRecordingService.ACTION_PAUSE
            })
            idleMicMonitor.start()
            _state.update { it.copy(isRecordingPaused = true, statusText = "Пауза записи") }
        }
    }

    fun stopRecording() {
        if (!state.value.isRecording) return
        app.startService(Intent(app, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.ACTION_STOP
        })
        if (pausedAtMs > 0L) {
            pausedDurationMs += (System.currentTimeMillis() - pausedAtMs)
            pausedAtMs = 0L
        }
        elapsedJob?.cancel()
        elapsedJob = null
        idleMicMonitor.start()
        _state.update {
            it.copy(
                isRecording = false,
                isRecordingPaused = false,
                statusText = "Запись остановлена"
            )
        }
    }

    fun toggleFarLecturerMode() {
        val next = !state.value.farLecturerMode
        idleMicMonitor.setFarLecturerMode(next)
        prefs.edit().putBoolean(KEY_FAR_MODE, next).apply()
        _state.update { it.copy(farLecturerMode = next) }
    }

    fun setPauseOnInterruptions(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PAUSE_ON_INTERRUPTION, enabled).apply()
        _state.update { it.copy(pauseOnInterruptions = enabled) }
    }

    fun setEnvironmentProfile(profile: EnvironmentProfile) {
        idleMicMonitor.setEnvironmentProfile(profile)
        prefs.edit().putString(KEY_ENVIRONMENT_PROFILE, profile.name).apply()
        _state.update { it.copy(environmentProfile = profile) }
    }

    fun onAuthorizationRequested() {
        _state.update { it.copy(statusText = "Авторизация будет добавлена позже") }
    }

    fun requestConspect(sessionId: String) {
        if (!conspectJobs.add(sessionId)) return
        val cached = ConspectCache.get(app, sessionId)
        _state.update {
            it.copy(
                statusText = if (cached != null) "Конспект готов" else "Готовим конспект…",
                conspectDialog = cached?.let { ConspectState(sessionId, "completed", it, "", 0) },
            )
        }
        viewModelScope.launch {
            try {
                var current: ConspectState? = when (val result = api.finalizeConspect(sessionId)) {
                    is ApiResult.Ok -> result.value
                    is ApiResult.Failure -> {
                        if (cached != null && result.status == 0) {
                            _state.update { it.copy(statusText = "Нет сети — показан сохранённый конспект") }
                        } else {
                            _state.update {
                                it.copy(
                                    statusText = "Не удалось открыть конспект",
                                    conspectDialog = ConspectState(sessionId, "failed", "", result.message, 0),
                                )
                            }
                        }
                        return@launch
                    }
                }
                var polls = 0
                while (current != null && current.status != "completed" && current.status != "failed" && polls < 150) {
                    delay(4_000)
                    polls += 1
                    when (val result = api.finalizeConspect(sessionId)) {
                        is ApiResult.Ok -> current = result.value
                        is ApiResult.Failure -> {
                            if (result.status == 0 && cached != null) {
                                _state.update { it.copy(statusText = "Нет сети — показан сохранённый конспект") }
                                return@launch
                            }
                            // The aggregate may still be waiting for STT/indexing; retry finalize.
                            if (result.status != 404 && result.status != 202) {
                                current = ConspectState(sessionId, "failed", "", result.message, 0)
                            }
                        }
                    }
                }
                val finalState = current
                if (finalState != null && finalState.status == "completed") {
                    ConspectCache.save(app, sessionId, finalState.markdown)
                    _state.update { it.copy(statusText = "Конспект готов", conspectDialog = finalState) }
                } else if (finalState != null && finalState.status == "failed") {
                    _state.update { it.copy(statusText = "Конспект не готов", conspectDialog = finalState) }
                } else {
                    _state.update { it.copy(statusText = "Конспект готовится дольше обычного") }
                }
            } finally {
                conspectJobs.remove(sessionId)
            }
        }
    }

    fun dismissConspect() {
        _state.update { it.copy(conspectDialog = null) }
    }

    fun playRecordingById(id: String, startFraction: Float?) {
        if (state.value.isRecording) return
        val item = recordingsById[id] ?: return
        if (item.chunkPaths.isEmpty()) return
        if (state.value.currentPlaybackItemId == id && state.value.isPlaybackPaused && startFraction == null) {
            val player = mediaPlayer ?: return
            player.start()
            _state.update { it.copy(isPlayingLocalAudio = true, isPlaybackPaused = false) }
            return
        }
        viewModelScope.launch {
            startPlayback(item, startFraction ?: 0f)
        }
    }

    fun pausePlaybackForItem(id: String) {
        if (state.value.currentPlaybackItemId != id) return
        val player = mediaPlayer ?: return
        if (!player.isPlaying) return
        player.pause()
        _state.update { it.copy(isPlayingLocalAudio = false, isPlaybackPaused = true) }
    }

    fun stopPlaybackForItem(id: String) {
        if (state.value.currentPlaybackItemId != id) return
        stopPlaybackInternal(clearItem = false)
        _state.update {
            it.copy(
                isPlayingLocalAudio = false,
                isPlaybackPaused = false,
                playbackPositionMs = 0L
            )
        }
    }

    fun seekPlaybackPosition(id: String, fraction: Float) {
        val item = recordingsById[id] ?: return
        if (state.value.currentPlaybackItemId != id) return
        viewModelScope.launch {
            startPlayback(item, fraction.coerceIn(0f, 1f))
        }
    }

    fun deleteRecordings(ids: Set<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { id ->
                val item = recordingsById[id] ?: return@forEach
                if (state.value.currentPlaybackItemId == id) {
                    withContext(Dispatchers.Main) { stopPlaybackInternal(clearItem = true) }
                }
                item.chunkPaths.forEach { path ->
                    runCatching { File(path).delete() }
                    runCatching { dao.deleteByFilePath(path) }
                }
            }
        }
    }

    private fun observeQueue() {
        viewModelScope.launch {
            dao.observeAudioItems().collect { queueItems ->
                val grouped = queueItems.groupBy { parseMetadataValue(it.metadataJson, "sessionId") ?: "legacy_${it.id}" }
                val records = grouped.values
                    .map { buildRecordingSession(it) }
                    .sortedByDescending { it.sortKey }
                    .map { it.item }
                recordingsById = records.associateBy { it.id }
                _state.update {
                    val current = it.currentPlaybackItemId
                    val safeCurrent = if (current != null && records.any { row -> row.id == current }) current else null
                    it.copy(
                        recordings = records,
                        canPlayLocalAudio = records.isNotEmpty(),
                        currentPlaybackItemId = safeCurrent
                    )
                }
            }
        }
    }

    private fun observePendingUploads() {
        viewModelScope.launch {
            dao.observePendingCount().collect { pending ->
                _state.update { it.copy(pendingUploads = pending) }
            }
        }
    }

    private fun observeTelemetry() {
        viewModelScope.launch {
            RecordingTelemetry.soundLevel.collect { level ->
                _state.update { it.copy(soundLevel = level) }
            }
        }
        viewModelScope.launch {
            RecordingTelemetry.averageSoundLevel.collect { avg ->
                _state.update { it.copy(averageSoundLevel = avg) }
            }
        }
        viewModelScope.launch {
            RecordingTelemetry.agcGain.collect { gain ->
                _state.update { it.copy(agcGain = gain) }
            }
        }
    }

    private fun startElapsedTicker() {
        elapsedJob?.cancel()
        elapsedJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val activePaused = if (state.value.isRecordingPaused && pausedAtMs > 0L) now - pausedAtMs else 0L
                val elapsedMs = (now - recordingStartedAtMs - pausedDurationMs - activePaused).coerceAtLeast(0L)
                _state.update { it.copy(elapsedSeconds = elapsedMs / 1000L) }
                delay(250L)
            }
        }
    }

    private fun startStorageTicker() {
        storageJob?.cancel()
        storageJob = viewModelScope.launch {
            while (true) {
                val remainingSec = estimateRemainingSeconds()
                _state.update { it.copy(remainingRecordSeconds = remainingSec) }
                delay(15_000L)
            }
        }
    }

    private fun estimateRemainingSeconds(): Long {
        val freeBytes = app.filesDir.usableSpace
        val bytesPerSecond = (AppConfig.Audio.bitRateBps / 8L).coerceAtLeast(1L)
        return (freeBytes / bytesPerSecond).coerceAtLeast(0L)
    }

    private suspend fun startPlayback(item: RecordingListItem, fraction: Float) {
        val totalMs = item.durationMs.coerceAtLeast(1L)
        val targetMs = (totalMs * fraction.coerceIn(0f, 1f)).toLong()
        val (index, offsetMs, baseMs) = locateChunk(item.chunkDurationsMs, targetMs)
        playbackSessionId = item.id
        playbackChunkPaths = item.chunkPaths
        playbackChunkDurationsMs = item.chunkDurationsMs
        playbackChunkIndex = index
        playbackBaseMs = baseMs
        playChunk(index, offsetMs, totalMs)
    }

    private suspend fun playChunk(index: Int, seekMs: Long, totalMs: Long) {
        if (index >= playbackChunkPaths.size) {
            stopPlaybackInternal(clearItem = false)
            _state.update {
                it.copy(
                    isPlayingLocalAudio = false,
                    isPlaybackPaused = false,
                    playbackPositionMs = totalMs,
                    playbackDurationMs = totalMs
                )
            }
            return
        }

        val path = playbackChunkPaths[index]
        if (!File(path).exists()) {
            val nextBase = playbackBaseMs + playbackChunkDurationsMs.getOrElse(index) { 0L }
            playbackBaseMs = nextBase
            playChunk(index + 1, 0L, totalMs)
            return
        }

        stopPlayerOnly()
        val prepared = withContext(Dispatchers.IO) {
            runCatching {
                MediaPlayer().apply {
                    setDataSource(path)
                    prepare()
                }
            }.getOrNull()
        } ?: return

        mediaPlayer = prepared
        playbackChunkIndex = index
        prepared.setOnCompletionListener {
            viewModelScope.launch {
                val consumed = playbackChunkDurationsMs.getOrElse(playbackChunkIndex) { 0L }
                playbackBaseMs += consumed
                playChunk(playbackChunkIndex + 1, 0L, totalMs)
            }
        }
        prepared.setOnErrorListener { _, _, _ ->
            viewModelScope.launch {
                val consumed = playbackChunkDurationsMs.getOrElse(playbackChunkIndex) { 0L }
                playbackBaseMs += consumed
                playChunk(playbackChunkIndex + 1, 0L, totalMs)
            }
            true
        }

        val safeSeek = seekMs.coerceAtLeast(0L).toInt()
        if (safeSeek > 0) prepared.seekTo(safeSeek)
        prepared.start()
        _state.update {
            it.copy(
                currentPlaybackItemId = playbackSessionId,
                isPlayingLocalAudio = true,
                isPlaybackPaused = false,
                playbackDurationMs = totalMs,
                playbackPositionMs = (playbackBaseMs + seekMs).coerceIn(0L, totalMs)
            )
        }
        startPlaybackProgressTicker(totalMs)
    }

    private fun startPlaybackProgressTicker(totalMs: Long) {
        playbackMonitorJob?.cancel()
        playbackMonitorJob = viewModelScope.launch {
            while (true) {
                val player = mediaPlayer
                if (player == null) {
                    delay(120L)
                    continue
                }
                val inChunk = runCatching { player.currentPosition.toLong() }.getOrDefault(0L)
                val absolute = (playbackBaseMs + inChunk).coerceIn(0L, totalMs)
                _state.update { it.copy(playbackPositionMs = absolute) }
                delay(120L)
            }
        }
    }

    private fun stopPlaybackInternal(clearItem: Boolean) {
        playbackMonitorJob?.cancel()
        playbackMonitorJob = null
        stopPlayerOnly()
        playbackChunkPaths = emptyList()
        playbackChunkDurationsMs = emptyList()
        playbackChunkIndex = 0
        playbackBaseMs = 0L
        if (clearItem) playbackSessionId = null
        _state.update {
            it.copy(
                isPlayingLocalAudio = false,
                isPlaybackPaused = false,
                currentPlaybackItemId = if (clearItem) null else it.currentPlaybackItemId
            )
        }
    }

    private fun stopPlayerOnly() {
        val active = mediaPlayer
        mediaPlayer = null
        runCatching { active?.stop() }
        runCatching { active?.release() }
    }

    private fun buildRecordingSession(sessionItems: List<UploadQueueEntity>): SessionRow {
        val sorted = sessionItems.sortedWith(
            compareBy<UploadQueueEntity>({ parseMetadataValue(it.metadataJson, "chunkIndex")?.toIntOrNull() ?: Int.MAX_VALUE })
                .thenBy { it.createdAtMs }
        )
        val createdAt = sorted.firstOrNull()?.createdAtMs ?: 0L
        val chunkPaths = sorted.map { it.filePath }
        val chunkDurations = chunkPaths.map { resolveDuration(it) }
        val totalDuration = chunkDurations.sum().coerceAtLeast(1_000L)
        val item = RecordingListItem(
            id = parseMetadataValue(sorted.firstOrNull()?.metadataJson, "sessionId") ?: sorted.first().id,
            title = "Запись",
            filePath = sorted.first().filePath,
            chunkPaths = chunkPaths,
            chunkDurationsMs = chunkDurations,
            durationMs = totalDuration,
            createdAtLabel = dateFormatter.format(Date(createdAt)),
            durationLabel = formatDuration(totalDuration),
            status = mergeStatus(sorted.map { it.status }),
            statusLabel = "",
            deleteAfterLabel = ""
        )
        return SessionRow(item = item, sortKey = sorted.maxOfOrNull { it.createdAtMs } ?: createdAt)
    }

    private fun resolveDuration(path: String): Long {
        durationCacheMs[path]?.let { return it }
        val ms = runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val meta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            meta?.toLongOrNull()
        }.getOrNull()?.coerceAtLeast(500L) ?: (AppConfig.Audio.chunkDurationSec * 1000L)
        durationCacheMs[path] = ms
        return ms
    }

    private fun mergeStatus(statuses: List<UploadStatus>): UploadStatus {
        if (statuses.any { it == UploadStatus.FAILED }) return UploadStatus.FAILED
        if (statuses.any { it == UploadStatus.UPLOADING }) return UploadStatus.UPLOADING
        if (statuses.any { it == UploadStatus.PENDING }) return UploadStatus.PENDING
        return UploadStatus.UPLOADED
    }

    private fun parseMetadataValue(metadata: String?, key: String): String? {
        if (metadata.isNullOrBlank()) return null
        return metadata
            .split(';')
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }
    }

    private fun locateChunk(durations: List<Long>, targetMs: Long): Triple<Int, Long, Long> {
        if (durations.isEmpty()) return Triple(0, 0L, 0L)
        var accumulated = 0L
        durations.forEachIndexed { index, duration ->
            val next = accumulated + duration
            if (targetMs < next) {
                return Triple(index, (targetMs - accumulated).coerceAtLeast(0L), accumulated)
            }
            accumulated = next
        }
        val lastIndex = durations.lastIndex
        return Triple(lastIndex, (durations[lastIndex] - 1L).coerceAtLeast(0L), durations.dropLast(1).sum())
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%02d:%02d".format(minutes, seconds)
    }

    companion object {
        private const val PREFS_NAME = "recorder_preferences"
        private const val KEY_FAR_MODE = "far_mode"
        private const val KEY_PAUSE_ON_INTERRUPTION = "pause_on_interruption"
        private const val KEY_ENVIRONMENT_PROFILE = "environment_profile"
    }

    override fun onCleared() {
        super.onCleared()
        elapsedJob?.cancel()
        storageJob?.cancel()
        playbackMonitorJob?.cancel()
        stopPlayerOnly()
        idleMicMonitor.release()
    }
}

private data class SessionRow(
    val item: RecordingListItem,
    val sortKey: Long
)
