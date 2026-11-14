package com.querydoc.lecturerecorder.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.querydoc.lecturerecorder.MainActivity
import com.querydoc.lecturerecorder.R
import com.querydoc.lecturerecorder.study.StudyPlanStore

class StudyPlanReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val today = StudyPlanStore.all(applicationContext).filter { deadline ->
            val days = deadline.daysUntil()
            days == 0L || days == 1L || days == deadline.reminderDays.toLong()
        }
        if (today.isEmpty()) return Result.success()

        ensureChannel()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (today.size == 1) "Скоро экзамен" else "Скоро экзамены"
        val text = today.joinToString("\n") { deadline ->
            val days = deadline.daysUntil()
            when {
                days <= 0L -> "${deadline.subjectName}: сегодня"
                days == 1L -> "${deadline.subjectName}: завтра"
                else -> "${deadline.subjectName}: через $days дн."
            }
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_conspect)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    private fun ensureChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Учёба и дедлайны",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Напоминания об экзаменах по предметам"
            },
        )
    }

    companion object {
        const val CHANNEL_ID = "querydoc_study"
        const val NOTIFICATION_ID = 2401
    }
}
