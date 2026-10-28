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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import kotlinx.coroutines.launch
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
    var deleteTarget by remember { mutableStateOf<ConspectSummary?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var cachedIds by remember { mutableStateOf(ConspectCache.list(context).map { it.sessionId }.toSet()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload) {
        loading = true
        when (val result = api.listConspects()) {
            is ApiResult.Ok -> {
                items = result.value
                cachedIds = ConspectCache.list(context).map { it.sessionId }.toSet()
                offline = false
            }
            is ApiResult.Failure -> {
                items = ConspectCache.list(context)
                cachedIds = items.map { it.sessionId }.toSet()
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
                    text = "БИБЛИОТЕКА",
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
                    text = "Только готовые конспекты",
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
                    ConspectRow(
                        item = item,
                        hasLocalCopy = item.sessionId in cachedIds,
                        onClick = { onOpen(item) },
                        onRemoveLocal = {
                            ConspectCache.remove(context, item.sessionId)
                            cachedIds = cachedIds - item.sessionId
                            if (offline) items = items.filterNot { it.sessionId == item.sessionId }
                            Toast.makeText(context, "Копия удалена с устройства", Toast.LENGTH_SHORT).show()
                        },
                        onDeleteRemote = { deleteTarget = item },
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!deleting) deleteTarget = null },
            title = { Text("Удалить конспект?") },
            text = { Text("Конспект будет удалён с сервера и с устройства. Аудиозапись останется.") },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        scope.launch {
                            when (val result = api.deleteConspect(target.sessionId)) {
                                is ApiResult.Ok -> {
                                    ConspectCache.remove(context, target.sessionId)
                                    items = items.filterNot { it.sessionId == target.sessionId }
                                    cachedIds = cachedIds - target.sessionId
                                    deleteTarget = null
                                    Toast.makeText(context, "Конспект удалён", Toast.LENGTH_SHORT).show()
                                }
                                is ApiResult.Failure -> {
                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                }
                            }
                            deleting = false
                        }
                    },
                ) { Text(if (deleting) "Удаляем…" else "Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { if (!deleting) deleteTarget = null }) { Text("Отмена") }
            },
        )
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
private fun ConspectRow(
    item: ConspectSummary,
    hasLocalCopy: Boolean,
    onClick: () -> Unit,
    onRemoveLocal: () -> Unit,
    onDeleteRemote: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7FF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                    .background(Brush.linearGradient(listOf(Color(0xFF4338CA), Color(0xFF7C6AE8)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MenuBook, contentDescription = null, tint = Color.White, modifier = Modifier.size(23.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "КОНСПЕКТ",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6D5AE6),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                    )
                    Spacer(Modifier.weight(1f))
                    if (item.completedAt.isNotBlank()) {
                        Text(
                            text = formatConspectDate(item.completedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8),
                        )
                    }
                    Box {
                        IconButton(
                            modifier = Modifier.size(26.dp),
                            onClick = { menuExpanded = true },
                        ) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Действия", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            if (hasLocalCopy) {
                                DropdownMenuItem(
                                    text = { Text("Убрать с устройства") },
                                    onClick = {
                                        menuExpanded = false
                                        onRemoveLocal()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Удалить конспект", color = Color(0xFFDC2626)) },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFDC2626)) },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteRemote()
                                },
                            )
                        }
                    }
                }
                if (item.subject.isNotBlank()) {
                    Text(
                        text = item.subject.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF7C6AE8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    text = displayTitle(item.title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E1B4B),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (item.preview.isNotBlank()) {
                    Text(
                        text = item.preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val sizeLabel = if (item.length >= 1000) "${item.length / 1000} тыс. знаков" else "${item.length} знаков"
                    Text(
                        text = "$sizeLabel · ≈ ${readingMinutes(item.length)} мин чтения",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF64748B),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun readingMinutes(length: Int): Int = ((length + 999) / 1000).coerceAtLeast(1)

private fun displayTitle(raw: String): String {
    val title = raw.trim()
    if (title.isBlank()) return "Конспект лекции"
    if (title.startsWith("Аудиозапись", ignoreCase = true) || title.startsWith("Аудиосессия", ignoreCase = true)) {
        return "Конспект лекции"
    }
    val unwrapped = title
        .removePrefix("Конспект лекции «")
        .removeSuffix("»")
        .trim()
    return if (unwrapped.startsWith("Аудиозапись", ignoreCase = true) || unwrapped.startsWith("Аудиосессия", ignoreCase = true)) {
        "Конспект лекции"
    } else {
        unwrapped.ifBlank { "Конспект лекции" }
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
