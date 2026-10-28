package com.querydoc.lecturerecorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.querydoc.lecturerecorder.data.remote.ApiClient
import com.querydoc.lecturerecorder.data.remote.ApiResult
import com.querydoc.lecturerecorder.data.remote.ConspectSummary
import com.querydoc.lecturerecorder.work.ConspectCache
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ConspectsScreen(
    api: ApiClient,
    onBack: () -> Unit,
    onOpen: (ConspectSummary) -> Unit,
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var offline by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<ConspectSummary>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        when (val result = api.listConspects()) {
            is ApiResult.Ok -> {
                items = result.value
                offline = false
            }
            is ApiResult.Failure -> {
                items = ConspectCache.list(context)
                offline = true
            }
        }
        loading = false
    }

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
            Text(
                text = "Конспекты",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { reload += 1 }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Обновить", tint = Color(0xFF4338CA))
            }
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF0B1320), Color(0xFF1F3B5A), Color(0xFF4338CA)),
                    ),
                )
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Column {
                Text(
                    text = "Ваши лекции",
                    color = Color(0xFFC7D2FE),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (items.isEmpty()) "Пока пусто" else "${items.size} ${conspectWord(items.size)}",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Собраны автоматически после записи",
                    color = Color(0xFF9CA3AF),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (offline) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.CloudOff, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(16.dp))
                Text(
                    text = "Нет сети — показаны сохранённые конспекты",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF92400E),
                )
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF4338CA))
            }

            items.isEmpty() -> EmptyConspects(
                offline = offline,
                onRefresh = { reload += 1 },
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.sessionId }) { item ->
                    ConspectRow(item = item, onClick = { onOpen(item) })
                }
            }
        }
    }
}

private fun conspectWord(count: Int): String = when {
    count % 10 == 1 && count % 100 != 11 -> "конспект"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "конспекта"
    else -> "конспектов"
}

private fun formatConspectDate(value: String): String {
    if (value.isBlank()) return ""
    return runCatching {
        val instant = Instant.parse(value)
        DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale("ru"))
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }.getOrElse { value.take(10) }
}

@Composable
private fun ConspectRow(item: ConspectSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF4338CA), Color(0xFF6D5AE6)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Article, contentDescription = null, tint = Color.White, modifier = Modifier.size(23.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifBlank { "Конспект лекции" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.completedAt.isNotBlank()) {
                    Text(
                        text = formatConspectDate(item.completedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (item.preview.isNotBlank()) {
                    Text(
                        text = item.preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF475569),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Читать",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF4338CA),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF4338CA), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.weight(1f))
                    if (item.length > 0) {
                        Text(
                            text = if (item.length >= 1000) "${item.length / 1000} тыс. знаков" else "${item.length} знаков",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyConspects(offline: Boolean, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFE0E7FF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Article, contentDescription = null, tint = Color(0xFF4338CA), modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (offline) "Нет сети" else "Конспектов пока нет",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0F172A),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (offline) "Подключитесь к интернету, чтобы загрузить список." else "Запишите лекцию — конспект появится здесь автоматически.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF64748B),
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onRefresh) {
            Text("Обновить")
        }
    }
}
