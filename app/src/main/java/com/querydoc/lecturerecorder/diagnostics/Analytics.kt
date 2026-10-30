package com.querydoc.lecturerecorder.diagnostics

import android.content.Context
import com.querydoc.lecturerecorder.data.remote.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Минимальная продуктовая аналитика: только имена событий и короткие метаданные. */
object Analytics {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun track(context: Context, name: String, props: Map<String, String> = emptyMap()) {
        val appContext = context.applicationContext
        scope.launch {
            runCatching { ApiClient(appContext).trackEvent(name, props) }
        }
    }
}
