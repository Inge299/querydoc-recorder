package com.querydoc.lecturerecorder.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.querydoc.lecturerecorder.R

object NotificationHelper {
    const val channelId = "lecture_recorder_channel"
    const val recordingNotificationId = 1001
    const val limitWarningNotificationId = 1002
    const val limitWarningChannelId = "recording_limit_warning"

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

    fun ensureWarningChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            limitWarningChannelId,
            "Предупреждение о лимите записи",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Звуковое предупреждение перед автоматической остановкой записи"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun showLimitWarning(context: Context, secondsLeft: Long, limitMinutes: Int, canExtend: Boolean) {
        ensureWarningChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val minutes = ((secondsLeft + 59) / 60).coerceAtLeast(1)
        val builder = Notification.Builder(context, limitWarningChannelId)
            .setContentTitle("Запись скоро остановится")
            .setContentText("Осталось $minutes мин. Лимит: $limitMinutes мин.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setAutoCancel(true)
        if (canExtend) {
            val extendIntent = Intent(context, AudioRecordingService::class.java).apply {
                action = AudioRecordingService.ACTION_EXTEND
            }
            val pending = PendingIntent.getService(
                context,
                2001,
                extendIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(android.R.drawable.ic_input_add, "Продлить +15 мин", pending)
        }
        manager.notify(limitWarningNotificationId, builder.build())
    }

    fun cancelLimitWarning(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(limitWarningNotificationId)
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
