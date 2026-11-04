package com.querydoc.lecturerecorder.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.querydoc.lecturerecorder.MainActivity
import com.querydoc.lecturerecorder.data.remote.ApiClient
import com.querydoc.lecturerecorder.data.remote.ApiResult

class AdminAlertsWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val api = ApiClient(applicationContext)
        if (!api.hasSession()) return Result.success()
        return when (val result = api.adminAlerts()) {
            is ApiResult.Ok -> {
                val unread = result.value.alerts.filter { it.readAt == null }
                if (unread.isEmpty()) return Result.success()
                if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return Result.success()
                }
                val manager = NotificationManagerCompat.from(applicationContext)
                val openIntent = PendingIntent.getActivity(
                    applicationContext,
                    0,
                    Intent(applicationContext, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                unread.forEach { alert ->
                    val notification = NotificationCompat.Builder(applicationContext, AdminAlertsScheduler.CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_notify_more)
                        .setContentTitle(alert.title)
                        .setContentText(alert.body)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
                        .setContentIntent(openIntent)
                        .setAutoCancel(true)
                        .build()
                    manager.notify(alert.id.hashCode(), notification)
                }
                api.markAdminAlertsRead(unread.map { it.id })
                Result.success()
            }
            is ApiResult.Failure -> if (result.status == 401 || result.status == 403) Result.success() else Result.retry()
        }
    }
}
