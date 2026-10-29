package com.querydoc.lecturerecorder.ui

enum class RecordingStage(val label: String) {
    LOCAL("Записано"),
    CLOUD("В облаке"),
    RECOGNIZED("Распознано"),
    FAILED("Требует внимания"),
}
