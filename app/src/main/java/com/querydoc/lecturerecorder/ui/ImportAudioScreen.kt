package com.querydoc.lecturerecorder.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.querydoc.lecturerecorder.data.remote.ApiClient
import com.querydoc.lecturerecorder.data.remote.ApiResult
import com.querydoc.lecturerecorder.media.AudioImportExtractor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ImportAudioScreen(api: ApiClient, onBack: () -> Unit, onOpenConspects: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var imported by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedUri = uri
        imported = false
        error = ""
        status = ""
        selectedName = uri?.lastPathSegment?.substringAfterLast('/').orEmpty()
    }

    fun startImport() {
        val uri = selectedUri ?: return
        if (busy) return
        busy = true
        imported = false
        error = ""
        status = "Готовим аудио…"
        scope.launch {
            try {
                val prepared = AudioImportExtractor.prepare(context, uri).getOrElse { throw it }
                if (prepared.durationMs > 120 * 60 * 1000L) {
                    throw IllegalStateException("Лекция длиннее 120 минут")
                }
                selectedName = prepared.fileName
                status = "Загружаем аудио…"
                val sessionId = UUID.randomUUID().toString()
                val chunkId = UUID.randomUUID().toString()
                val upload = when (val result = api.presign(
                    chunkId = chunkId,
                    sessionId = sessionId,
                    chunkIndex = 0,
                    chunkCount = 1,
                    sizeBytes = prepared.file.length(),
                    mimeType = prepared.mimeType,
                    fileName = prepared.fileName,
                    durationMs = prepared.durationMs,
                    capturedAtMs = System.currentTimeMillis(),
                    language = "ru-RU",
                )) {
                    is ApiResult.Ok -> result.value
                    is ApiResult.Failure -> throw IllegalStateException(result.message)
                }
                if (!upload.duplicate) {
                    when (val put = api.putObject(upload, prepared.file)) {
                        is ApiResult.Ok -> Unit
                        is ApiResult.Failure -> throw IllegalStateException(put.message)
                    }
                    when (val complete = api.complete(upload.uploadSessionId, chunkId)) {
                        is ApiResult.Ok -> Unit
                        is ApiResult.Failure -> throw IllegalStateException(complete.message)
                    }
                }
                prepared.file.delete()
                status = "Запускаем конспект…"
                var attempts = 0
                var finalStatus = ""
                var finalError = ""
                while (attempts < 20) {
                    val conspect = api.finalizeConspect(sessionId)
                    if (conspect is ApiResult.Failure) {
                        finalError = conspect.message
                        if (conspect.status != 202 && conspect.status > 0) break
                    } else if (conspect is ApiResult.Ok) {
                        finalStatus = conspect.value.status
                        if (finalStatus == "completed" || finalStatus == "failed") break
                    }
                    attempts += 1
                    status = "Конспект обрабатывается… ${attempts * 3} сек."
                    delay(3000)
                }
                if (finalStatus == "completed") {
                    imported = true
                    status = "Аудио загружено. Конспект готов."
                } else if (finalStatus == "failed") {
                    error = finalError.ifBlank { "Не удалось подготовить конспект" }
                } else {
                    imported = true
                    status = "Аудио загружено. Конспект появится в библиотеке позже."
                }
            } catch (exception: Exception) {
                error = exception.message ?: "Не удалось импортировать файл"
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6FB))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = Color(0xFF0F172A))
            }
            Text("Импорт лекции", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
        }
        Text(
            "Аудиофайл или видео. Из видео загрузится только звуковая дорожка — само видео не хранится.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF475569),
        )
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.UploadFile, contentDescription = null, tint = Color(0xFF4338CA))
                Text(if (selectedName.isBlank()) "Файл не выбран" else selectedName, fontWeight = FontWeight.SemiBold)
                Text("Поддерживаются аудио и видео с аудиодорожкой. Максимум 120 минут.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                Button(onClick = { picker.launch(arrayOf("audio/*", "video/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Выбрать файл")
                }
                Button(onClick = ::startImport, enabled = selectedUri != null && !busy, modifier = Modifier.fillMaxWidth()) {
                    if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(end = 8.dp)) else Text("Импортировать")
                }
            }
        }
        if (status.isNotBlank()) Text(status, color = Color(0xFF4338CA), style = MaterialTheme.typography.bodyMedium)
        if (error.isNotBlank()) Text(error, color = Color(0xFFDC2626), style = MaterialTheme.typography.bodySmall)
        if (imported) {
            TextButton(onClick = onOpenConspects, modifier = Modifier.fillMaxWidth()) { Text("Открыть конспекты") }
        }
        Spacer(Modifier.height(8.dp))
    }
}
