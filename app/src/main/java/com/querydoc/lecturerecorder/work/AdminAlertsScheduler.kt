package com.querydoc.lecturerecorder.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AdminAlertsScheduler {
    const val CHANNEL_ID = "admin_alerts"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Платежи и админ-уведомления", NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }

    fun schedule(context: Context) {
        ensureChannel(context)
        val request = PeriodicWorkRequestBuilder<AdminAlertsWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.enqueueUniquePeriodicWork(
            "admin-alerts-check",
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        workManager.enqueueUniqueWork(
            "admin-alerts-now",
            androidx.work.ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AdminAlertsWorker>().build(),
        )
    }
}
