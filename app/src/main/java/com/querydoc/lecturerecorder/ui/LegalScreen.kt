package com.querydoc.lecturerecorder.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LegalScreen(onBack: () -> Unit) {
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
                title = "Статус документа",
                text = "QueryDoc находится в стадии бета-теста. Реквизиты оператора персональных данных будут опубликованы после государственной регистрации организации. До этого сервис используется в ограниченном тестовом режиме.",
            )
            LegalCard(
                title = "Политика конфиденциальности",
                text = """
                    Мы обрабатываем номер телефона для входа, записи лекций, транскрипты, конспекты, историю использования и технические данные приложения.

                    Аудио и текст передаются внешним поставщикам распознавания речи и языковых моделей для формирования конспекта и ответов. Мы не продаём персональные данные и не используем содержание лекций для рекламы.

                    Аудио хранится ограниченное время, заданное тарифом и настройками сервиса. Конспекты и транскрипты доступны в библиотеке, пока вы не удалите их или аккаунт.
                """.trimIndent(),
            )
            LegalCard(
                title = "Согласие на обработку",
                text = """
                    Нажимая «Запись» или загружая аудио, вы соглашаетесь на обработку записи для расшифровки и подготовки конспекта.

                    Вы можете отозвать согласие, удалив конспект или аккаунт в настройках. Удаление аккаунта удаляет конспекты, аудио и связанные данные из сервиса.
                """.trimIndent(),
            )
            LegalCard(
                title = "Условия использования",
                text = """
                    Сервис предназначен для учебных целей. Конспекты и ответы генерируются автоматически и могут содержать ошибки: проверяйте важные факты по первоисточнику.

                    Запрещено загружать материалы, на которые у вас нет прав, и использовать сервис для нарушения закона или прав других людей.

                    Запись разговоров и лекций должна соответствовать применимому законодательству и согласию участников.
                """.trimIndent(),
            )
            LegalCard(
                title = "Контакты",
                text = "После регистрации организации здесь будут указаны юридическое название, ИНН/ОГРН, адрес и email для обращений по персональным данным.",
            )
            Text(
                "Редакция для бета-теста. Перед публичным запуском документы должны быть проверены юристом и дополнены реквизитами оператора.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF92400E),
                modifier = Modifier.padding(8.dp, 8.dp, 8.dp, 24.dp),
            )
        }
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
