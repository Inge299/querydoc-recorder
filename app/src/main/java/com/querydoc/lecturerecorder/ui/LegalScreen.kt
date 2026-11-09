package com.querydoc.lecturerecorder.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val SITE = "https://querydoc.ru"

@Composable
fun LegalScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
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
                "Правовая информация",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            LegalCard(
                title = "Продавец",
                text = """
                    Ходыкина Ольга Александровна
                    Плательщик налога на профессиональный доход (самозанятый)
                    ИНН: 263214814373
                    г. Пятигорск
                """.trimIndent(),
            )
            LegalCard(
                title = "Политика конфиденциальности",
                text = """
                    Мы обрабатываем номер телефона для входа, записи, транскрипты, конспекты, документы, историю использования и технические данные приложения.

                    Аудио и текст передаются поставщикам распознавания речи и языковых моделей для формирования конспекта и ответов. Мы не продаём персональные данные и не используем содержание лекций для рекламы.

                    Аудио хранится ограниченное время, заданное тарифом. Конспекты и транскрипты доступны, пока вы не удалите их или аккаунт.
                """.trimIndent(),
            )
            LegalCard(
                title = "Согласие на обработку",
                text = """
                    Используя запись или загрузку аудио, вы соглашаетесь на обработку записи для расшифровки и подготовки конспекта.

                    Вы можете отозвать согласие, удалив конспект или аккаунт. Удаление аккаунта удаляет конспекты, аудио и связанные данные из сервиса.

                    Запись разговоров и лекций должна соответствовать закону и согласию участников.
                """.trimIndent(),
            )
            LegalCard(
                title = "Условия использования",
                text = """
                    Сервис предназначен для учебных и рабочих материалов. Конспекты и ответы генерируются автоматически и могут содержать ошибки: проверяйте важные факты по первоисточнику.

                    Запрещено загружать материалы без прав и использовать сервис для нарушения закона или прав других людей.
                """.trimIndent(),
            )
            LegalCard(
                title = "Контакты",
                text = "Email: admin.RAGpro@gmail.com\nТелефон: +7 962 424-59-36\nСайт: https://querydoc.ru",
            )
            TextButton(onClick = { openUrl(context, "$SITE/requisites") }) {
                Text("Реквизиты на сайте")
            }
            TextButton(onClick = { openUrl(context, "$SITE/offer") }) {
                Text("Публичная оферта")
            }
            TextButton(onClick = { openUrl(context, "$SITE/privacy") }) {
                Text("Политика конфиденциальности")
            }
            TextButton(onClick = { openUrl(context, "$SITE/consent") }) {
                Text("Согласие на обработку данных")
            }
            Text(
                "Редакция от 09.11.2026. Актуальные документы всегда доступны на querydoc.ru.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(8.dp, 8.dp, 8.dp, 24.dp),
            )
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

@Composable
private fun LegalCard(title: String, text: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E1B4B))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF475569),
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
