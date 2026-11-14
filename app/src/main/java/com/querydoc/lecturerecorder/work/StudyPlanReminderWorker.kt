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
import com.querydoc.lecturerecorder.study.SubjectDeadline
import kotlin.math.ceil
import kotlin.math.max

class StudyPlanReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val all = StudyPlanStore.all(applicationContext)
        val totalRemaining = all.sumOf { remainingMinutes(it) }
        val globalThreshold = if (totalRemaining > 0) {
            ceil(totalRemaining / 60.0).toLong().plus(1L).coerceIn(1L, 7L)
        } else {
            1L
        }
        val due = all.filter { deadline ->
            val days = deadline.daysUntil()
            val threshold = max(reminderThresholdDays(deadline), globalThreshold)
            days >= 0L && (days == 0L || days == 1L || days == threshold)
        }
        if (due.isEmpty()) return Result.success()

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
        val title = if (due.size == 1) "Скоро экзамен" else "Скоро экзамены"
        val text = due.joinToString("\n") { deadline ->
            val days = deadline.daysUntil()
            val remaining = remainingMinutes(deadline)
            val whenLabel = when {
                days <= 0L -> "сегодня"
                days == 1L -> "завтра"
                else -> "через $days дн."
            }
            if (remaining > 0) "${deadline.subjectName}: $whenLabel · ~$remaining мин"
            else "${deadline.subjectName}: $whenLabel"
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

    private fun reminderThresholdDays(deadline: SubjectDeadline): Long {
        val remaining = remainingMinutes(deadline)
        if (remaining <= 0) return max(1L, deadline.reminderDays.toLong())
        val requiredDays = ceil(remaining / 60.0).toLong().coerceAtLeast(1L)
        return (requiredDays + 1L).coerceIn(1L, 7L)
    }

    private fun remainingMinutes(deadline: SubjectDeadline): Int {
        if (deadline.totalCount <= 0 || deadline.estimatedMinutes <= 0) return 0
        val left = (deadline.totalCount - deadline.reviewedCount).coerceAtLeast(0)
        return max(10, (deadline.estimatedMinutes * left / deadline.totalCount.toDouble()).toInt())
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
