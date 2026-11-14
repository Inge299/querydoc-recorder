package com.querydoc.lecturerecorder.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.querydoc.lecturerecorder.study.StudyPlanStore
import java.util.concurrent.TimeUnit

object StudyPlanScheduler {
    private const val UNIQUE_WORK_NAME = "study-plan-reminder"

    fun schedule(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        val plan = StudyPlanStore.load(context) ?: return
        val reminderAt = plan.examAtMillis - plan.reminderDays * 24L * 60L * 60L * 1_000L
        val delayMs = (reminderAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<StudyPlanReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
