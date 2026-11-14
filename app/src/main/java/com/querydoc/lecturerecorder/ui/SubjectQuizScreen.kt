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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.querydoc.lecturerecorder.data.remote.ApiClient
import com.querydoc.lecturerecorder.data.remote.ApiResult
import com.querydoc.lecturerecorder.data.remote.QuizQuestion
import com.querydoc.lecturerecorder.data.remote.SubjectQuiz
import com.querydoc.lecturerecorder.data.remote.SubjectSummary
import com.querydoc.lecturerecorder.study.SubjectQuizStore
import kotlinx.coroutines.launch

@Composable
fun SubjectQuizScreen(
    api: ApiClient,
    subject: SubjectSummary,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    var quiz by remember(subject.id) { mutableStateOf<SubjectQuiz?>(null) }
    var loading by remember(subject.id) { mutableStateOf(true) }
    var error by remember(subject.id) { mutableStateOf("") }
    var index by remember(subject.id) { mutableIntStateOf(0) }
    var selected by remember(subject.id) { mutableIntStateOf(-1) }
    var submitted by remember(subject.id) { mutableStateOf(false) }
    var correctCount by remember(subject.id) { mutableIntStateOf(0) }
    var finished by remember(subject.id) { mutableStateOf(false) }
    var retry by remember(subject.id) { mutableIntStateOf(0) }
    var regenerate by remember(subject.id) { mutableStateOf(false) }
    var reportState by remember(subject.id) { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(subject.id, retry) {
        loading = true
        error = ""
        when (val result = api.subjectQuiz(subject.id, regenerate)) {
            is ApiResult.Ok -> {
                regenerate = false
                quiz = result.value
                index = 0
                selected = -1
                submitted = false
                correctCount = 0
                finished = false
                reportState = ""
                SubjectQuizStore.setBankSize(context, subject.id, result.value.questions.size)
            }
            is ApiResult.Failure -> error = result.message
        }
        loading = false
    }
    BackHandler { onBack() }

    Column(
        modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFFF4F6FB))
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = androidx.compose.ui.graphics.Color(0xFF0F172A)) }
            Column(Modifier.weight(1f)) {
                Text("Проверка знаний", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = androidx.compose.ui.graphics.Color(0xFF0F172A))
                Text(subject.name, style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color(0xFF64748B))
            }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = androidx.compose.ui.graphics.Color(0xFF4338CA)) }
            error.isNotBlank() -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(error, color = androidx.compose.ui.graphics.Color(0xFFB91C1C))
                Button(onClick = { retry += 1 }) { Text("Попробовать снова") }
                TextButton(onClick = onBack) { Text("Назад к предметам") }
            }
            finished -> {
                val total = quiz?.questions?.size ?: 0
                val accuracy = if (total > 0) correctCount * 100 / total else 0
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Проверка завершена", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFF0F172A))
                    Text("Правильно: $correctCount из $total", style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color(0xFF4338CA))
                    Text("Точность: $accuracy%", style = MaterialTheme.typography.bodyLarge, color = androidx.compose.ui.graphics.Color(0xFF334155))
                    Text("Результат сохранён в прогрессе предмета.", style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color(0xFF64748B))
                    val stats = SubjectQuizStore.stats(context, subject.id)
                    if (stats.weakTopics.isNotEmpty()) {
                        Text("Слабые темы: ${stats.weakTopics.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color(0xFFB45309))
                    }
                    Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text("К предметам") }
                    TextButton(onClick = {
                        regenerate = true
                        retry += 1
                    }, modifier = Modifier.fillMaxWidth()) { Text("Новые вопросы") }
                }
            }
            quiz != null -> {
                val currentQuiz = quiz!!
                val question = currentQuiz.questions[index]
                val total = currentQuiz.questions.size
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    LinearProgressIndicator(progress = { (index + 1).toFloat() / total }, modifier = Modifier.fillMaxWidth(), color = androidx.compose.ui.graphics.Color(0xFF4338CA), trackColor = androidx.compose.ui.graphics.Color(0xFFE2E8F0))
                    Text("Вопрос ${index + 1} из $total", style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color(0xFF64748B), modifier = Modifier.padding(top = 10.dp))
                    Text(question.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = androidx.compose.ui.graphics.Color(0xFF0F172A), modifier = Modifier.padding(top = 12.dp))
                    if (question.conspectTitle.isNotBlank()) {
                        Text(question.conspectTitle, style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color(0xFF64748B), modifier = Modifier.padding(top = 4.dp))
                    }
                    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        question.options.forEachIndexed { optionIndex, option ->
                            val isCorrect = submitted && optionIndex == question.correctIndex
                            val isWrong = submitted && optionIndex == selected && optionIndex != question.correctIndex
                            val background = when {
                                isCorrect -> androidx.compose.ui.graphics.Color(0xFFDCFCE7)
                                isWrong -> androidx.compose.ui.graphics.Color(0xFFFEE2E2)
                                optionIndex == selected -> androidx.compose.ui.graphics.Color(0xFFE0E7FF)
                                else -> androidx.compose.ui.graphics.Color.White
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !submitted) { selected = optionIndex },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = background),
                            ) {
                                Text(option, modifier = Modifier.padding(14.dp), color = androidx.compose.ui.graphics.Color(0xFF0F172A), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (submitted) {
                        Text(
                            if (selected == question.correctIndex) "Верно" else "Неверно",
                            color = if (selected == question.correctIndex) androidx.compose.ui.graphics.Color(0xFF15803D) else androidx.compose.ui.graphics.Color(0xFFB91C1C),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        if (question.explanation.isNotBlank()) {
                            Text(question.explanation, style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color(0xFF334155), modifier = Modifier.padding(top = 6.dp))
                        }
                        if (question.sourceQuote.isNotBlank()) {
                            Text("Из конспекта: «${question.sourceQuote}»", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color(0xFF64748B), modifier = Modifier.padding(top = 6.dp))
                        }
                        TextButton(
                            onClick = {
                                if (reportState.isBlank()) {
                                    scope.launch {
                                        reportState = when (val result = api.reportQuizQuestion(subject.id, question.id, "Некорректный или неоднозначный вопрос")) {
                                            is ApiResult.Ok -> "Спасибо, вопрос отправлен на проверку."
                                            is ApiResult.Failure -> result.message
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.padding(0.dp),
                        ) { Text(if (reportState.isBlank()) "Вопрос некорректный?" else reportState, style = MaterialTheme.typography.labelSmall) }
                    }
                    Button(
                        onClick = {
                            if (!submitted) {
                                if (selected < 0) return@Button
                                val correct = selected == question.correctIndex
                                if (correct) correctCount += 1
                                SubjectQuizStore.record(context, subject.id, question.id + ":" + question.question.take(40), correct, total, question.conspectTitle)
                                submitted = true
                            } else if (index + 1 >= total) {
                                finished = true
                            } else {
                                index += 1
                                selected = -1
                                submitted = false
                                reportState = ""
                            }
                        },
                        enabled = submitted || selected >= 0,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF4338CA)),
                    ) {
                        Text(if (!submitted) "Ответить" else if (index + 1 >= total) "Показать результат" else "Дальше")
                    }
                }
            }
        }
    }
}
