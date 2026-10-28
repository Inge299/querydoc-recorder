package com.querydoc.lecturerecorder.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.querydoc.lecturerecorder.data.remote.ApiClient
import com.querydoc.lecturerecorder.data.remote.ApiResult
import com.querydoc.lecturerecorder.work.ConspectCache

@Composable
fun ConspectReaderScreen(
    api: ApiClient,
    sessionId: String,
    initialMarkdown: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var markdown by remember { mutableStateOf(initialMarkdown.orEmpty()) }
    var loading by remember { mutableStateOf(markdown.isBlank()) }
    var offline by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var transcript by remember { mutableStateOf(ConspectCache.getTranscript(context, sessionId).orEmpty()) }
    var transcriptLoading by remember { mutableStateOf(false) }
    var transcriptError by remember { mutableStateOf("") }
    var transcriptReload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        val cached = ConspectCache.get(context, sessionId)
        if (markdown.isBlank() && cached != null) markdown = cached
        loading = markdown.isBlank()
        when (val result = api.conspect(sessionId)) {
            is ApiResult.Ok -> {
                markdown = result.value.markdown
                offline = false
                error = ""
            }
            is ApiResult.Failure -> {
                if (markdown.isBlank() && cached != null) markdown = cached
                offline = markdown.isNotBlank()
                error = if (markdown.isBlank()) result.message else ""
            }
        }
        loading = false
    }

    LaunchedEffect(tab, transcriptReload) {
        if (tab != 1 || transcript.isNotBlank()) return@LaunchedEffect
        transcriptLoading = true
        transcriptError = ""
        when (val result = api.transcript(sessionId)) {
            is ApiResult.Ok -> {
                transcript = result.value.transcript
                ConspectCache.saveTranscript(context, sessionId, transcript)
            }
            is ApiResult.Failure -> {
                transcript = ConspectCache.getTranscript(context, sessionId).orEmpty()
                transcriptError = if (transcript.isBlank()) result.message else ""
            }
        }
        transcriptLoading = false
    }

    val bodyMarkdown = markdown.replaceFirst(Regex("(?m)^# .*\\n?"), "").trimStart()
    val title = markdown.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("# ") }
        ?.removePrefix("# ")
        ?.trim()
        .orEmpty()
        .ifBlank { "Конспект" }
    val shareText = if (tab == 0) markdown else transcript
    val canShare = shareText.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6FB))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = Color(0xFF0F172A))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (tab == 0) "Конспект" else "Транскрипт",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A),
                )
                if (offline && tab == 0) {
                    Text(
                        text = "Сохранённая копия",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF92400E),
                    )
                }
            }
            if (canShare) {
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(if (tab == 0) "Конспект" else "Транскрипт", shareText))
                    Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Копировать", tint = Color(0xFF4338CA))
                }
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, title)
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(send, "Поделиться"))
                }) {
                    Icon(Icons.Filled.Share, contentDescription = "Поделиться", tint = Color(0xFF4338CA))
                }
            }
        }

        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .fillMaxWidth()
                .background(Color(0xFFE7EAF3), RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            ReaderTab("Конспект", selected = tab == 0, modifier = Modifier.weight(1f)) { tab = 0 }
            ReaderTab("Транскрипт", selected = tab == 1, modifier = Modifier.weight(1f)) { tab = 1 }
        }

        if (tab == 0) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF4338CA))
                }

                error.isNotBlank() -> ReaderError(error) { reload += 1 }

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF0B1320), Color(0xFF1F3B5A))),
                                RoundedCornerShape(18.dp),
                            )
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                    ) {
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Конспект лекции",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFC7D2FE),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            ConspectMarkdown(bodyMarkdown)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        } else {
            when {
                transcriptLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF4338CA))
                }

                transcriptError.isNotBlank() -> ReaderError(transcriptError) { transcriptReload += 1 }

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Text(
                                text = "Распознанная речь",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = transcript.ifBlank { "Транскрипт пуст." },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF334155),
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ReaderTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(if (selected) Color.White else Color.Transparent, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color(0xFF4338CA) else Color(0xFF64748B),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ReaderError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Не удалось открыть",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0F172A),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF64748B),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Повторить") }
    }
}
