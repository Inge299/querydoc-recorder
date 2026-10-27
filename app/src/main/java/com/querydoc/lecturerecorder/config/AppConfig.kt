package com.querydoc.lecturerecorder.config

object AppConfig {
    object Api {
        val baseUrl: String = com.querydoc.lecturerecorder.BuildConfig.API_BASE_URL.trimEnd('/')
    }

    object Audio {
        const val chunkDurationSec: Long = 30L
        const val sampleRateHz: Int = 24_000
        const val bitRateBps: Int = 64_000
        const val channelCount: Int = 1
    }

    object Power {
        const val enableEnergySaverByDefault: Boolean = true
        const val recordingScreenBrightness: Float = 0.12f
    }

    object Photo {
        const val jpegQualityDefault: Int = 92
        const val maxLongSidePx: Int = 2800
        const val usePngForHighContrastContent: Boolean = false
        const val autoRectangleDetectionEnabledByDefault: Boolean = true
        const val detectorMaxSidePx: Int = 1400
        const val detectorMinConfidenceRatio: Float = 1.05f
    }

    object Storage {
        // Keep local files for manual replay and deferred cleanup policy.
        const val keepLocalAudioAfterUpload: Boolean = true
        const val cleanupAfterDays: Long = 3
    }
}
