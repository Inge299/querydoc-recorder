package com.querydoc.lecturerecorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val text: String,
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = remember {
        listOf(
            OnboardingPage(
                icon = Icons.Filled.Mic,
                title = "Записывайте лекцию",
                text = "Телефон можно убрать в карман: запись идёт в фоне, а части автоматически загружаются в облако.",
            ),
            OnboardingPage(
                icon = Icons.Filled.AutoAwesome,
                title = "Получайте конспект",
                text = "После загрузки мы расшифруем речь и соберём главное. Обычно это занимает несколько минут.",
            ),
            OnboardingPage(
                icon = Icons.Filled.QuestionAnswer,
                title = "Спрашивайте и повторяйте",
                text = "Задайте вопрос по лекции, перейдите к таймкоду, пройдите 5 вопросов и отметьте план повторения.",
            ),
        )
    }
    var page by remember { mutableIntStateOf(0) }
    val current = pages[page]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF4F6FB), Color(0xFFEEF2FF), Color(0xFFF4F6FB)),
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                "${page + 1} / ${pages.size}",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF64748B),
            )
        }

        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(104.dp)
                .background(Color.White, RoundedCornerShape(30.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(current.icon, contentDescription = null, tint = Color(0xFF4338CA), modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(
            current.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            current.text,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF475569),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1.2f))

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pages.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .weight(if (index == page) 2f else 1f)
                        .height(5.dp)
                        .background(
                            if (index == page) Color(0xFF4338CA) else Color(0xFFCBD5E1),
                            RoundedCornerShape(3.dp),
                        ),
                )
            }
        }
        Button(
            onClick = {
                if (page == pages.lastIndex) onFinish() else page += 1
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(if (page == pages.lastIndex) "Начать" else "Далее")
            Spacer(Modifier.size(8.dp))
            Icon(Icons.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(18.dp))
    }
}
