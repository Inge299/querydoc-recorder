package com.querydoc.lecturerecorder.recording

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AudioChunkFileNamer {
    private val formatter = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    fun createChunkFile(baseDir: File): File {
        val name = "audio_${formatter.format(Date())}.m4a"
        return File(baseDir, name)
    }
}
