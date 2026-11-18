package com.querydoc.lecturerecorder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import com.querydoc.lecturerecorder.data.remote.ConspectSummary
import com.querydoc.lecturerecorder.data.remote.GroupLibraryItem
import com.querydoc.lecturerecorder.data.remote.GroupSubjectSummary
import com.querydoc.lecturerecorder.data.remote.StudyGroupSummary
import kotlinx.coroutines.launch

@Composable
fun GroupLibraryScreen(
    api: ApiClient,
    group: StudyGroupSummary,
    onBack: () -> Unit,
) {
    var items by remember { mutableStateOf<List<GroupLibraryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var showShare by remember { mutableStateOf(false) }
    var conspects by remember { mutableStateOf<List<ConspectSummary>>(emptyList()) }
    var groupSubjects by remember { mutableStateOf<List<GroupSubjectSummary>>(emptyList()) }
    var selectedGroupSubjectId by remember { mutableStateOf("") }
    var shareBusy by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<GroupLibraryItem?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            when (val result = api.groupLibrary(group.id)) {
                is ApiResult.Ok -> {
                    items = result.value
                    error = ""
                }
                is ApiResult.Failure -> error = result.message
            }
            loading = false
        }
    }

    fun loadConspects() {
        scope.launch {
            when (val result = api.listConspects()) {
                is ApiResult.Ok -> conspects = result.value
                is ApiResult.Failure -> error = result.message
            }
            when (val result = api.groupSubjects(group.id)) {
                is ApiResult.Ok -> {
                    groupSubjects = result.value
                    if (selectedGroupSubjectId.isBlank()) selectedGroupSubjectId = result.value.firstOrNull()?.id.orEmpty()
                }
                is ApiResult.Failure -> Unit
            }
        }
    }

    LaunchedEffect(group.id) { load() }
    val activeItem = selectedItem
    if (activeItem != null) {
        GroupLibraryReaderScreen(api = api, groupId = group.id, item = activeItem, onBack = { selectedItem = null })
        return
    }
    BackHandler { onBack() }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF4F6FB)).statusBarsPadding().navigationBarsPadding().padding(horizontal = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = Color(0xFF0F172A))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Библиотека группы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                Text(group.name, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
            }
            IconButton(onClick = {
                loadConspects()
                showShare = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить конспект", tint = Color(0xFF4338CA))
            }
        }

        Text(
            "Общие материалы группы. Личная копия остаётся у автора; владелец может удалить любой материал, преподаватель — свой.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64748B),
            modifier = Modifier.padding(vertical = 8.dp),
        )

        if (actionMessage.isNotBlank()) {
            Text(actionMessage, color = Color(0xFF15803D), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (error.isNotBlank()) {
            Text(error, color = Color(0xFFB91C1C), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF4338CA)) }
            items.isEmpty() -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Пока пусто", style = MaterialTheme.typography.titleMedium, color = Color(0xFF334155))
                Text("Добавьте свой конспект в общую библиотеку.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B), modifier = Modifier.padding(top = 6.dp))
            }
            else -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEach { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selectedItem = item },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title.ifBlank { "Конспект" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                                Text(item.groupSubjectName.ifBlank { item.subject.ifBlank { "Без предмета" } }, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                                if (item.timeline.isNotEmpty()) {
                                    Text("Таймкоды: ${item.timeline.size}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4338CA))
                                }
                            }
                            if (item.canDelete) {
                                IconButton(onClick = {
                                    scope.launch {
                                        when (val result = api.deleteGroupLibraryItem(group.id, item.id)) {
                                            is ApiResult.Ok -> {
                                                actionMessage = "Материал перемещён в корзину"
                                                load()
                                            }
                                            is ApiResult.Failure -> error = result.message
                                        }
                                    }
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Удалить из группы", tint = Color(0xFFB91C1C))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showShare) {
        AlertDialog(
            onDismissRequest = { if (!shareBusy) showShare = false },
            title = { Text("Добавить конспект") },
            text = {
                Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text("Будет добавлена групповая копия. Личный конспект останется у вас.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                    if (groupSubjects.isNotEmpty()) {
                        Text("Предмет группы", style = MaterialTheme.typography.labelMedium, color = Color(0xFF475569), modifier = Modifier.padding(top = 10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                            groupSubjects.forEach { subject ->
                                TextButton(onClick = { selectedGroupSubjectId = subject.id }) {
                                    Text(
                                        subject.name,
                                        color = if (selectedGroupSubjectId == subject.id) Color(0xFF4338CA) else Color(0xFF64748B),
                                        fontWeight = if (selectedGroupSubjectId == subject.id) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                    conspects.forEach { conspect ->
                        TextButton(
                            enabled = !shareBusy,
                            onClick = {
                                shareBusy = true
                                scope.launch {
                                    when (val result = api.shareConspectToGroup(group.id, conspect.sessionId, selectedGroupSubjectId)) {
                                        is ApiResult.Ok -> {
                                            showShare = false
                                            actionMessage = "Конспект добавлен в библиотеку группы"
                                            load()
                                        }
                                        is ApiResult.Failure -> error = result.message
                                    }
                                    shareBusy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(conspect.title.ifBlank { "Конспект" }, modifier = Modifier.fillMaxWidth(), color = Color(0xFF0F172A))
                        }
                    }
                }
            },
            confirmButton = { TextButton(enabled = !shareBusy, onClick = { showShare = false }) { Text("Закрыть") } },
        )
    }
}
