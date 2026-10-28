package com.querydoc.lecturerecorder.ui

enum class RecognitionLanguage(val code: String, val label: String) {
    RU("ru-RU", "Русский"),
    RU_EN("ru-RU,en-US", "Русский + English"),
    EN("en-US", "English");

    companion object {
        fun fromName(value: String?): RecognitionLanguage =
            entries.firstOrNull { it.name == value } ?: RU
    }
}
