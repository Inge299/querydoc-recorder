package com.querydoc.lecturerecorder.ui

import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.querydoc.lecturerecorder.data.remote.ApiClient
import com.querydoc.lecturerecorder.data.remote.ApiResult
import com.querydoc.lecturerecorder.data.remote.AudioSessionSource
import com.querydoc.lecturerecorder.data.remote.GroupLibraryItem
import java.util.Locale

@Composable
fun GroupLibraryReaderScreen(
    api: ApiClient,
    item: GroupLibraryItem,
    onBack: () -> Unit,
) {
    var audio by remember(item.id) { mutableStateOf<AudioSessionSource?>(null) }
    var loadingAudio by remember(item.id) { mutableStateOf(true) }
    var audioError by remember(item.id) { mutableStateOf("") }
    var player by remember(item.id) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(item.id) { mutableStateOf(false) }

    LaunchedEffect(item.id, item.sourceSessionId) {
        loadingAudio = true
        if (item.sourceSessionId.isBlank()) {
            audioError = "У этого материала нет исходного аудио."
            loadingAudio = false
            return@LaunchedEffect
        }
        when (val result = api.audioSession(item.sourceSessionId)) {
            is ApiResult.Ok -> audio = result.value
            is ApiResult.Failure -> audioError = result.message
        }
        loadingAudio = false
    }
    DisposableEffect(item.id) {
        onDispose {
            player?.release()
            player = null
        }
    }
    BackHandler { onBack() }

    fun playFrom(globalMs: Long) {
        val source = audio ?: return
        var offset = 0L
        val chunk = source.chunks.firstOrNull { candidate ->
            val end = offset + candidate.durationMs.coerceAtLeast(1L)
            if (globalMs in offset until end) true else {
                offset = end
                false
            }
        } ?: source.chunks.firstOrNull() ?: return
        val localMs = (globalMs - offset).coerceAtLeast(0L).toInt()
        player?.release()
        playing = false
        val next = MediaPlayer()
        player = next
        runCatching {
            next.setDataSource(chunk.url)
            next.setOnPreparedListener {
                it.seekTo(localMs)
                it.start()
                playing = true
            }
            next.setOnCompletionListener { playing = false }
            next.setOnErrorListener { _, _, _ ->
                playing = false
                audioError = "Не удалось воспроизвести аудио"
                true
            }
            next.prepareAsync()
        }.onFailure {
            audioError = it.message ?: "Не удалось воспроизвести аудио"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6FB))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = Color(0xFF0F172A))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Конспект группы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                Text(item.subject.ifBlank { "Без предмета" }, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
            }
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(item.title.ifBlank { "Конспект" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Text(
                "Материал из общей библиотеки группы. Личная копия автора не изменяется.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            )
            when {
                loadingAudio -> CircularProgressIndicator(color = Color(0xFF4338CA), modifier = Modifier.padding(8.dp))
                audioError.isNotBlank() -> Text(audioError, color = Color(0xFF92400E), style = MaterialTheme.typography.bodySmall)
                else -> {
                    val source = audio
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(onClick = {
                            if (playing) {
                                player?.pause()
                                playing = false
                            } else {
                                playFrom(0L)
                            }
                        }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = if (playing) "Пауза" else "Слушать", tint = Color(0xFF4338CA))
                        }
                        Text(if (source?.chunks.isNullOrEmpty()) "Аудио недоступно" else "Слушать лекцию", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF334155))
                    }
                }
            }

            if (item.timeline.isNotEmpty()) {
                Text("Таймкоды", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A), modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
                item.timeline.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { playFrom(entry.startMs) }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        TextButton(onClick = { playFrom(entry.startMs) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                            Text(formatTimeline(entry.startMs), style = MaterialTheme.typography.labelMedium, color = Color(0xFF4338CA))
                        }
                        Text(entry.text, style = MaterialTheme.typography.bodySmall, color = Color(0xFF334155), modifier = Modifier.weight(1f))
                    }
                }
            }

            Text("Конспект", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A), modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
            ConspectMarkdown(item.markdownSnapshot.ifBlank { "Конспект пуст." })
        }
    }
}

private fun formatTimeline(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)
}
