package com.querydoc.lecturerecorder.recording

enum class EnvironmentProfile {
    QUIET,
    NORMAL,
    NOISY;

    fun next(): EnvironmentProfile {
        return when (this) {
            QUIET -> NORMAL
            NORMAL -> NOISY
            NOISY -> QUIET
        }
    }

    fun labelRu(): String {
        return when (this) {
            QUIET -> "Тихая аудитория"
            NORMAL -> "Обычная аудитория"
            NOISY -> "Шумная аудитория"
        }
    }

    companion object {
        fun fromName(name: String?): EnvironmentProfile {
            return entries.firstOrNull { it.name == name } ?: NORMAL
        }
    }
}
