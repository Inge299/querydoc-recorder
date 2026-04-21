package com.querydoc.lecturerecorder.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RecordingTelemetry {
    private val _soundLevel = MutableStateFlow(0f)
    val soundLevel: StateFlow<Float> = _soundLevel.asStateFlow()
    private val _averageSoundLevel = MutableStateFlow(0f)
    val averageSoundLevel: StateFlow<Float> = _averageSoundLevel.asStateFlow()
    private val _agcGain = MutableStateFlow(1f)
    val agcGain: StateFlow<Float> = _agcGain.asStateFlow()

    fun updateSoundLevel(level: Float) {
        _soundLevel.value = level.coerceIn(0f, 1f)
    }

    fun updateAgcGain(gain: Float) {
        _agcGain.value = gain.coerceIn(0.5f, 3.0f)
    }

    fun updateAverageSoundLevel(level: Float) {
        _averageSoundLevel.value = level.coerceIn(0f, 1f)
    }

    fun reset() {
        _soundLevel.value = 0f
        _averageSoundLevel.value = 0f
        _agcGain.value = 1f
    }
}
