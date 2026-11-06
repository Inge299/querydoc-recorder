package com.querydoc.lecturerecorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.querydoc.lecturerecorder.data.remote.ApiClient
import com.querydoc.lecturerecorder.data.remote.ApiResult
import com.querydoc.lecturerecorder.data.remote.SupportMessageItem
import kotlinx.coroutines.launch

@Composable
fun SupportScreen(api: ApiClient, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<SupportMessageItem>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    suspend fun reload() {
        loading = true
        when (val result = api.supportMessages()) {
            is ApiResult.Ok -> {
                messages = result.value.messages
                error = ""
            }
            is ApiResult.Failure -> error = result.message
        }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(messages.size, sending) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6FB))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = Color(0xFF0F172A))
            }
            Text("Поддержка", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF4338CA)) }
        } else {
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Напишите нам", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("Опишите проблему или вопрос. Ответ появится здесь и в уведомлениях.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                                }
                            }
                        }
                    }
                    items(messages, key = { it.id }) { message ->
                        val user = message.role == "user"
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                            Card(
                                modifier = Modifier.fillMaxWidth(0.9f),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = if (user) Color(0xFF4338CA) else Color.White),
                            ) {
                                Text(
                                    message.text,
                                    modifier = Modifier.padding(12.dp),
                                    color = if (user) Color.White else Color(0xFF0F172A),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                    if (error.isNotBlank()) item { Text(error, color = Color(0xFFDC2626), style = MaterialTheme.typography.bodySmall) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFFF4F6FB)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(2000) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Сообщение…") },
                    maxLines = 3,
                )
                IconButton(
                    enabled = input.isNotBlank() && !sending,
                    onClick = {
                        val text = input.trim()
                        if (text.isBlank() || sending) return@IconButton
                        sending = true
                        scope.launch {
                            when (val result = api.sendSupportMessage(text)) {
                                is ApiResult.Ok -> {
                                    input = ""
                                    reload()
                                }
                                is ApiResult.Failure -> error = result.message
                            }
                            sending = false
                        }
                    },
                    modifier = Modifier.background(Color(0xFF4338CA), RoundedCornerShape(24.dp)),
                ) {
                    if (sending) CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(12.dp))
                    else Icon(Icons.Filled.Send, contentDescription = "Отправить", tint = Color.White, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}
