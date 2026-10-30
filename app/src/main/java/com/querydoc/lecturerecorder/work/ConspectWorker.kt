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
import com.querydoc.lecturerecorder.AppGraph
import com.querydoc.lecturerecorder.MainActivity
import com.querydoc.lecturerecorder.R
import com.querydoc.lecturerecorder.data.remote.ApiResult
import com.querydoc.lecturerecorder.data.remote.ConspectState
import com.querydoc.lecturerecorder.diagnostics.Analytics
import com.querydoc.lecturerecorder.diagnostics.Diagnostics
import kotlinx.coroutines.delay

class ConspectWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(ConspectScheduler.KEY_SESSION_ID).orEmpty()
        if (sessionId.isBlank()) return Result.failure()
        val api = AppGraph.apiClient(applicationContext)
        Diagnostics.info(applicationContext, "ConspectWorker: started $sessionId")

        var polls = 0
        var lastError = ""
        while (!isStopped && polls < 120) {
            when (val result = api.finalizeConspect(sessionId)) {
                is ApiResult.Ok -> {
                    val state = result.value
                    if (state.status == "completed") {
                        ConspectScheduler.markCompleted(applicationContext, sessionId)
                        ConspectCache.save(applicationContext, sessionId, state.markdown)
                        ConspectScheduler.markUnseen(applicationContext, sessionId)
                        notifyCompleted(sessionId, state.markdown)
                        Analytics.track(applicationContext, "conspect_completed")
                        Diagnostics.info(applicationContext, "ConspectWorker: completed $sessionId")
                        return Result.success()
                    }
                    if (state.status == "failed") {
                        notifyFailed(sessionId, state.error.ifBlank { "Не удалось подготовить конспект" })
                        Diagnostics.warn(applicationContext, "ConspectWorker: failed $sessionId")
                        return Result.success()
                    }
                }

                is ApiResult.Failure -> {
                    lastError = result.message
                    if (result.status == 401 || result.status == 403) {
                        Diagnostics.warn(applicationContext, "ConspectWorker: authorization required")
                        return Result.failure()
                    }
                    if (result.status !in listOf(404, 202)) {
                        Diagnostics.warn(applicationContext, "ConspectWorker: poll failure ${result.status} ${result.message}")
                    }
                }
            }
            polls += 1
            delay(5_000)
        }

        if (isStopped) return Result.retry()
        notifyFailed(sessionId, lastError.ifBlank { "Конспект готовится слишком долго" })
        return Result.success()
    }

    private fun notifyCompleted(sessionId: String, markdown: String) {
        val text = markdown.ifBlank { "Конспект готов" }
        notify(
            sessionId = sessionId,
            title = "Конспект готов",
            text = notificationPreview(text),
            bigText = text.take(4_000),
            id = 1000 + (sessionId.hashCode() and 0x7fff),
        )
    }

    private fun notificationPreview(markdown: String): String =
        markdown.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
            ?.take(160)
            ?: "Откройте, чтобы прочитать конспект"

    private fun notifyFailed(sessionId: String, error: String) {
        notify(
            sessionId = sessionId,
            title = "Конспект не готов",
            text = error.take(180).ifBlank { "Попробуйте открыть запись и запустить ещё раз" },
            bigText = error.take(1_000),
            id = 2000 + (sessionId.hashCode() and 0x7fff),
        )
    }

    private fun notify(sessionId: String, title: String, text: String, bigText: String, id: Int) {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Diagnostics.warn(applicationContext, "ConspectWorker: notification permission not granted")
            return
        }
        ensureChannel()
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_CONSPECT_SESSION_ID, sessionId)
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_conspect)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(id, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Конспекты",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Готовые конспекты лекций"
            },
        )
    }

    companion object {
        private const val CHANNEL_ID = "querydoc_conspects"
    }
}
