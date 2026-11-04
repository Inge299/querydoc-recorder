package com.querydoc.lecturerecorder.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
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
import com.querydoc.lecturerecorder.data.remote.InboxNotification
import com.querydoc.lecturerecorder.diagnostics.Analytics
import kotlinx.coroutines.launch

@Composable
fun NotificationsScreen(api: ApiClient, onBack: () -> Unit, onOpenPlans: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<InboxNotification>>(emptyList()) }
    var unread by remember { mutableStateOf(0) }

    suspend fun reload() {
        loading = true
        when (val result = api.notifications()) {
            is ApiResult.Ok -> {
                items = result.value.notifications
                unread = result.value.unread
                error = ""
            }
            is ApiResult.Failure -> error = result.message
        }
        loading = false
    }

    LaunchedEffect(Unit) {
        Analytics.track(context, "notifications_open")
        reload()
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
            Text(
                "Уведомления",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A),
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { scope.launch { reload() } }, enabled = !loading) { Text("Обновить") }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (unread > 0) "Непрочитанных: $unread" else "Все прочитано", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
            if (unread > 0) {
                TextButton(onClick = {
                    scope.launch {
                        api.markNotificationsRead(emptyList())
                        reload()
                    }
                }) { Text("Прочитать все") }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (loading) CircularProgressIndicator(color = Color(0xFF4338CA), modifier = Modifier.padding(24.dp))
            if (error.isNotBlank()) Text(error, color = Color(0xFFB91C1C), style = MaterialTheme.typography.bodySmall)
            if (!loading && items.isEmpty() && error.isBlank()) {
                Text("Пока нет уведомлений", color = Color(0xFF64748B), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(24.dp))
            }
            items.forEach { item ->
                NotificationCard(
                    item = item,
                    onOpenPlans = onOpenPlans,
                    onOpenUrl = { url ->
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            .onFailure { Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show() }
                    },
                )
            }
        }
    }
}

@Composable
private fun NotificationCard(item: InboxNotification, onOpenPlans: () -> Unit, onOpenUrl: (String) -> Unit) {
    val isPayment = item.kind == "payment"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (item.readAt == null) Color(0xFFEEF2FF) else Color.White),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (isPayment) "Платёж" else if (item.kind == "system") "Система" else "Обновление",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isPayment) Color(0xFFB45309) else Color(0xFF4338CA),
                )
                Text(item.createdAt?.take(10) ?: "", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
            }
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
            Text(item.body, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF334155))
            if (isPayment) {
                TextButton(onClick = onOpenPlans) { Text("Открыть тарифы") }
            } else if (item.actionUrl.isNotBlank()) {
                TextButton(onClick = { onOpenUrl(item.actionUrl) }) { Text("Подробнее") }
            }
        }
    }
}
