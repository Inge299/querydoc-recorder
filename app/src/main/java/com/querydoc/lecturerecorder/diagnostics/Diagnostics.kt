package com.querydoc.lecturerecorder.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Diagnostics {
    private const val TAG = "LectureRecorder"
    private const val LOG_FILE_NAME = "diagnostics.log"
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun info(context: Context, message: String) {
        Log.i(TAG, message)
        append(context, "INFO", message)
    }

    fun warn(context: Context, message: String) {
        Log.w(TAG, message)
        append(context, "WARN", message)
    }

    fun error(context: Context, message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        val details = if (throwable != null) "$message | ${throwable.javaClass.simpleName}: ${throwable.message}" else message
        append(context, "ERROR", details)
    }

    private fun append(context: Context, level: String, message: String) {
        runCatching {
            val logDir = File(context.filesDir, "diagnostics").apply { mkdirs() }
            val logFile = File(logDir, LOG_FILE_NAME)
            val line = "${formatter.format(Date())} [$level] $message\n"
            logFile.appendText(line)
        }
    }
}
