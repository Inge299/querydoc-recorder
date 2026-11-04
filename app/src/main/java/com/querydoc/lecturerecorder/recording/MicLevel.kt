package com.querydoc.lecturerecorder.recording

import kotlin.math.log10
import kotlin.math.max

/**
 * Maps raw PCM RMS to a stable 0..1 UI level using dBFS, not AGC gain.
 * Speech at a normal distance is roughly -35..-15 dBFS; silence is below -55.
 */
object MicLevel {
    private const val FLOOR_DB = -58.0
    private const val CEILING_DB = -10.0

    fun fromRms(rms: Float): Float {
        if (!rms.isFinite() || rms <= 0.000001f) return 0f
        val db = 20.0 * log10(max(rms.toDouble(), 1e-6))
        return ((db - FLOOR_DB) / (CEILING_DB - FLOOR_DB)).toFloat().coerceIn(0f, 1f)
    }
}
