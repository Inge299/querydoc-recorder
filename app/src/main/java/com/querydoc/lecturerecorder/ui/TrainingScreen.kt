package com.querydoc.lecturerecorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.querydoc.lecturerecorder.data.remote.ApiClient
import com.querydoc.lecturerecorder.data.remote.ApiResult
import com.querydoc.lecturerecorder.data.remote.ConspectSummary
import com.querydoc.lecturerecorder.data.remote.ServerQuizStats
import com.querydoc.lecturerecorder.data.remote.SubjectSummary
import com.querydoc.lecturerecorder.study.StudyPlanStore
import com.querydoc.lecturerecorder.study.SubjectQuizStore
import java.text.DateFormat
import java.util.Date

@Composable
fun TrainingScreen(
    api: ApiClient,
    onOpenQuiz: (SubjectSummary) -> Unit,
) {
    val context = LocalContext.current
    var subjects by remember { mutableStateOf<List<SubjectSummary>>(emptyList()) }
    var remoteStats by remember { mutableStateOf<Map<String, ServerQuizStats>>(emptyMap()) }
    var conspects by remember { mutableStateOf<List<ConspectSummary>>(emptyList()) }
    var seenIds by remember {
        mutableStateOf(
            context.getSharedPreferences("conspect_prefs", android.content.Context.MODE_PRIVATE)
                .getStringSet("seen_session_ids", emptySet())?.toSet() ?: emptySet(),
        )
    }
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
        when (val result = api.listConspects()) {
            is ApiResult.Ok -> conspects = result.value
            is ApiResult.Failure -> conspects = emptyList()
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
                    reviewed = conspectsForSubject(conspects, subject).count { it.sessionId in seenIds },
                    total = maxOf(conspectsForSubject(conspects, subject).size, subject.count),
                    minutes = estimatedMinutes(conspectsForSubject(conspects, subject)),
                    deadline = StudyPlanStore.forSubject(context, subject.id, subject.name),
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
    reviewed: Int,
    total: Int,
    minutes: Int,
    deadline: com.querydoc.lecturerecorder.study.SubjectDeadline?,
    onOpenQuiz: () -> Unit,
) {
    val attempts = remote?.attempts ?: local.attempts
    val accuracy = remote?.accuracy ?: local.accuracy
    val readiness = remote?.readiness ?: SubjectQuizStore.readiness(local)
    val weakTopics = remote?.weakTopics ?: local.weakTopics
    val progress = if (total > 0) reviewed.toFloat() / total else 0f
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenQuiz),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(subject.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A), modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, contentDescription = "Открыть проверку", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
            }
            Text(
                if (total > 0) "Просмотрено $reviewed из $total · ~${trainingMinutes(minutes)}" else "${subject.count} конспектов",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B),
            )
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    color = if (progress >= 0.7f) Color(0xFF15803D) else Color(0xFF4338CA),
                    trackColor = Color(0xFFE2E8F0),
                )
            }
            if (deadline != null) {
                val days = deadline.daysUntil()
                val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(deadline.examAtMillis))
                Text(
                    when {
                        days < 0 -> "Экзамен: $date"
                        days == 0L -> "Экзамен сегодня · $date"
                        else -> "Экзамен: $date · через $days дн."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        days <= 3 -> Color(0xFFB91C1C)
                        days <= 7 -> Color(0xFFB45309)
                        else -> Color(0xFF4338CA)
                    },
                )
            }
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
            Text(
                if (attempts > 0) "Продолжить проверку" else "Начать проверку",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF4338CA),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun trainingMinutes(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60} ч${if (minutes % 60 > 0) " ${minutes % 60} мин" else ""}" else "$minutes мин"

private fun conspectsForSubject(conspects: List<ConspectSummary>, subject: SubjectSummary): List<ConspectSummary> =
    conspects.filter {
        (it.subjectId.isNotBlank() && it.subjectId == subject.id) ||
            (it.subjectId.isBlank() && it.subject.trim().equals(subject.name.trim(), ignoreCase = true))
    }

private fun estimatedMinutes(rows: List<ConspectSummary>): Int =
    rows.sumOf { row -> maxOf(10, kotlin.math.ceil((row.length.coerceAtLeast(1) / 1000.0) * 5.0).toInt()) }

private fun trainingWord(count: Int): String = when {
    count % 10 == 1 && count % 100 != 11 -> "конспект"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "конспекта"
    else -> "конспектов"
}
