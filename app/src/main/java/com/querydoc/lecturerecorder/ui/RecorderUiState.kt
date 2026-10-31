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
    val remainingRecordSeconds: Long = 0L,
    val recordingUsedMinutes: Int = 0,
    val recordingLimitMinutes: Int = 180,
    val recordingRemainingMinutes: Int = 180,
    val usageLoaded: Boolean = false,
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
    val conspectDialog: ConspectState? = null
)
