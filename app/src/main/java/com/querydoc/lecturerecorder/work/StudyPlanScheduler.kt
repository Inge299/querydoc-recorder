package com.querydoc.lecturerecorder.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object StudyPlanScheduler {
    private const val UNIQUE_WORK_NAME = "study-plan-reminders"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<StudyPlanReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
