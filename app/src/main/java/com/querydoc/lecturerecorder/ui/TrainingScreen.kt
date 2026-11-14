package com.querydoc.lecturerecorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.querydoc.lecturerecorder.data.remote.ApiClient
import com.querydoc.lecturerecorder.data.remote.ApiResult
import com.querydoc.lecturerecorder.data.remote.ServerQuizStats
import com.querydoc.lecturerecorder.data.remote.SubjectSummary
import com.querydoc.lecturerecorder.study.SubjectQuizStore

@Composable
fun TrainingScreen(
    api: ApiClient,
    onOpenQuiz: (SubjectSummary) -> Unit,
) {
    val context = LocalContext.current
    var subjects by remember { mutableStateOf<List<SubjectSummary>>(emptyList()) }
    var remoteStats by remember { mutableStateOf<Map<String, ServerQuizStats>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(retry) {
        loading = true
        error = ""
        when (val result = api.listSubjects()) {
            is ApiResult.Ok -> subjects = result.value.filter { it.count > 0 }
            is ApiResult.Failure -> error = result.message
        }
        when (val result = api.subjectQuizStats()) {
            is ApiResult.Ok -> remoteStats = result.value.associateBy { it.subjectId }
            is ApiResult.Failure -> Unit
        }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Выберите предмет для проверки знаний",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF64748B),
        )
        when {
            loading -> CircularProgressIndicator(color = Color(0xFF4338CA), modifier = Modifier.padding(20.dp))
            error.isNotBlank() -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(error, color = Color(0xFFB91C1C))
                TextButton(onClick = { retry += 1 }) { Text("Повторить") }
            }
            subjects.isEmpty() -> Text(
                "Для тренировки нужен хотя бы один готовый конспект.",
                color = Color(0xFF64748B),
                modifier = Modifier.padding(vertical = 20.dp),
            )
            else -> subjects.forEach { subject ->
                TrainingSubjectCard(
                    subject = subject,
                    remote = remoteStats[subject.id],
                    local = SubjectQuizStore.stats(context, subject.id),
                    onOpenQuiz = { onOpenQuiz(subject) },
                )
            }
        }
    }
}

@Composable
private fun TrainingSubjectCard(
    subject: SubjectSummary,
    remote: ServerQuizStats?,
    local: com.querydoc.lecturerecorder.study.SubjectQuizStats,
    onOpenQuiz: () -> Unit,
) {
    val attempts = remote?.attempts ?: local.attempts
    val accuracy = remote?.accuracy ?: local.accuracy
    val readiness = remote?.readiness ?: SubjectQuizStore.readiness(local)
    val weakTopics = remote?.weakTopics ?: local.weakTopics
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(subject.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
            Text(
                "${subject.count} ${trainingWord(subject.count)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B),
            )
            if (attempts > 0) {
                Text(
                    "Проверки: $attempts · точность $accuracy% · готовность: $readiness",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF475569),
                )
                if (weakTopics.isNotEmpty()) {
                    Text(
                        "Слабые темы: ${weakTopics.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB45309),
                    )
                }
            } else {
                Text("Проверок ещё не было", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
            }
            Button(
                onClick = onOpenQuiz,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4338CA)),
            ) {
                Text(if (attempts > 0) "Продолжить проверку" else "Начать проверку")
            }
        }
    }
}

private fun trainingWord(count: Int): String = when {
    count % 10 == 1 && count % 100 != 11 -> "конспект"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "конспекта"
    else -> "конспектов"
}
