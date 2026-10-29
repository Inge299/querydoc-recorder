package com.querydoc.lecturerecorder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.querydoc.lecturerecorder.data.local.StudyProgressStore
import com.querydoc.lecturerecorder.data.remote.ApiClient
import com.querydoc.lecturerecorder.data.remote.ApiResult
import com.querydoc.lecturerecorder.data.remote.StudyKit

@Composable
fun StudyKitScreen(
    api: ApiClient,
    sessionId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var forceRefresh by remember { mutableStateOf(false) }
    var kit by remember { mutableStateOf<StudyKit?>(null) }
    var error by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var checked by remember(sessionId) { mutableStateOf(StudyProgressStore.checked(context, sessionId)) }

    LaunchedEffect(refresh) {
        loading = true
        error = ""
        when (val result = api.studyKit(sessionId, forceRefresh)) {
            is ApiResult.Ok -> kit = result.value
            is ApiResult.Failure -> error = result.message
        }
        forceRefresh = false
        loading = false
    }

    BackHandler { onBack() }

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
                "Проверка",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                forceRefresh = true
                refresh += 1
            }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Обновить вопросы", tint = Color(0xFF4338CA))
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF4338CA))
            }
            error.isNotBlank() -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(error, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFDC2626))
                Button(onClick = { refresh += 1 }, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Повторить")
                }
            }
            kit != null -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Filled.School, contentDescription = null, tint = Color(0xFF4338CA))
                            Column {
                                Text("Проверьте себя", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "5 вопросов и короткий чек-лист по этой лекции",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF64748B),
                                )
                            }
                        }
                    }
                }
                itemsIndexed(kit!!.questions) { index, item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "${index + 1}. ${item.question}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF0F172A),
                            )
                            if (index in revealed) {
                                Text(
                                    item.answer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF4338CA),
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            TextButton(
                                onClick = {
                                    revealed = if (index in revealed) revealed - index else revealed + index
                                },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(if (index in revealed) "Скрыть ответ" else "Показать ответ")
                            }
                        }
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Чек-лист повторения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            kit!!.checklist.forEachIndexed { index, text ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val next = if (index in checked) checked - index else checked + index
                                            checked = next
                                            StudyProgressStore.save(context, sessionId, next)
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = index in checked,
                                        onCheckedChange = {
                                            val next = if (index in checked) checked - index else checked + index
                                            checked = next
                                            StudyProgressStore.save(context, sessionId, next)
                                        },
                                    )
                                    Text(text, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                item { Box(Modifier.size(12.dp)) }
            }
        }
    }
}
