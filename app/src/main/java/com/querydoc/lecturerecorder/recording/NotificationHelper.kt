package com.querydoc.lecturerecorder.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.querydoc.lecturerecorder.R

object NotificationHelper {
    const val channelId = "lecture_recorder_channel"
    const val recordingNotificationId = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.notification_recording_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_recording_text)
        }
        manager.createNotificationChannel(channel)
    }

    fun buildRecordingNotification(context: Context, text: String): Notification {
        val builder = Notification.Builder(context, channelId)
            .setContentTitle(context.getString(R.string.notification_recording_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
        return builder.build()
    }
}
