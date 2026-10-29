package com.querydoc.lecturerecorder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.querydoc.lecturerecorder.data.remote.ApiClient
import com.querydoc.lecturerecorder.data.remote.ApiResult
import com.querydoc.lecturerecorder.data.remote.SubjectSummary
import kotlinx.coroutines.launch

@Composable
fun SubjectsManagerScreen(
    api: ApiClient,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    var items by remember { mutableStateOf<List<SubjectSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var newName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<SubjectSummary?>(null) }
    var editName by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<SubjectSummary?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            when (val result = api.listSubjects()) {
                is ApiResult.Ok -> {
                    items = result.value
                    error = ""
                }
                is ApiResult.Failure -> error = result.message
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }
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
                "Предметы",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A),
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it.take(80) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Новый предмет") },
            )
            Button(
                onClick = {
                    val name = newName.trim()
                    if (name.isBlank() || busy) return@Button
                    busy = true
                    scope.launch {
                        when (val result = api.createSubject(name)) {
                            is ApiResult.Ok -> {
                                newName = ""
                                onChanged()
                                load()
                            }
                            is ApiResult.Failure -> error = result.message
                        }
                        busy = false
                    }
                },
                enabled = newName.isNotBlank() && !busy,
            ) { Text("Добавить") }
        }

        if (error.isNotBlank()) {
            Text(
                error,
                color = Color(0xFFDC2626),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF4338CA))
            }
        } else if (items.isEmpty()) {
            Text(
                "Пока нет предметов. Добавьте первый — и назначайте его лекциям.",
                color = Color(0xFF64748B),
                modifier = Modifier.padding(20.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { subject ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(subject.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(
                                    if (subject.count > 0) "${subject.count} конспектов" else "Нет конспектов",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B),
                                )
                            }
                            IconButton(onClick = {
                                editing = subject
                                editName = subject.name
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Переименовать", tint = Color(0xFF64748B), modifier = Modifier.size(19.dp))
                            }
                            IconButton(onClick = { deleting = subject }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = Color(0xFFDC2626), modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { subject ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Переименовать предмет") },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it.take(80) },
                    singleLine = true,
                    label = { Text("Название") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editName.isNotBlank() && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            when (val result = api.renameSubject(subject.id, editName.trim())) {
                                is ApiResult.Ok -> {
                                    editing = null
                                    onChanged()
                                    load()
                                }
                                is ApiResult.Failure -> error = result.message
                            }
                            busy = false
                        }
                    },
                ) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Отмена") } },
        )
    }

    deleting?.let { subject ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить предмет?") },
            text = { Text("Лекции останутся, но перейдут в «Без предмета».") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            when (val result = api.deleteSubject(subject.id)) {
                                is ApiResult.Ok -> {
                                    deleting = null
                                    onChanged()
                                    load()
                                }
                                is ApiResult.Failure -> error = result.message
                            }
                            busy = false
                        }
                    },
                ) { Text("Удалить", color = Color(0xFFDC2626)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
        )
    }
}
