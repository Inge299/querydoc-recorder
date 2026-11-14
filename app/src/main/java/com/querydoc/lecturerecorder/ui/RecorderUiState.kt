package com.querydoc.lecturerecorder.ui

import com.querydoc.lecturerecorder.data.remote.ConspectState
import com.querydoc.lecturerecorder.recording.EnvironmentProfile

data class RecorderUiState(
    val hasMicPermission: Boolean = false,
    val isRecording: Boolean = false,
    val isRecordingPaused: Boolean = false,
    val farLecturerMode: Boolean = true,
    val pauseOnInterruptions: Boolean = true,
    val pauseAudioOnLeave: Boolean = true,
    val environmentProfile: EnvironmentProfile = EnvironmentProfile.NORMAL,
    val recognitionLanguage: RecognitionLanguage = RecognitionLanguage.RU,
    val energySaverEnabled: Boolean = true,
    val elapsedSeconds: Long = 0L,
    val maxRecordingMinutes: Int = 90,
    val maxSessionMinutes: Int = 90,
    val audioRetentionDays: Int = 7,
    val audioStorageLimitGb: Int = 2,
    val storageDays: Int = 14,
    val remainingRecordSeconds: Long = 0L,
    val recordingUsedMinutes: Int = 0,
    val recordingLimitMinutes: Int = 90,
    val recordingBonusMinutes: Int = 0,
    val recordingRemainingMinutes: Int = 90,
    val usageLoaded: Boolean = false,
    val usageError: String = "",
    val paywallRequested: Boolean = false,
    val soundLevel: Float = 0f,
    val averageSoundLevel: Float = 0f,
    val agcGain: Float = 1f,
    val pendingUploads: Int = 0,
    val statusText: String = "",
    val canPlayLocalAudio: Boolean = false,
    val isPlayingLocalAudio: Boolean = false,
    val isPlaybackPaused: Boolean = false,
    val currentPlaybackItemId: String? = null,
    val playbackPositionMs: Long = 0L,
    val playbackDurationMs: Long = 0L,
    val playbackStatus: String = "",
    val audioPositions: Map<String, Long> = emptyMap(),
    val audioDownloadSessionId: String? = null,
    val audioDownloadCompleted: Int = 0,
    val audioDownloadTotal: Int = 0,
    val audioDownloadActive: Boolean = false,
    val recordings: List<RecordingListItem> = emptyList(),
    val conspectDialog: ConspectState? = null,
    val importState: ImportUiState? = null
)

enum class ImportStage {
    PREPARING,
    UPLOADING,
    PROCESSING,
    READY,
    ERROR,
}

data class ImportUiState(
    val stage: ImportStage,
    val fileName: String = "",
    val message: String = "",
    val sessionId: String? = null,
)
