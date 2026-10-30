package com.querydoc.lecturerecorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.querydoc.lecturerecorder.BuildConfig
import com.querydoc.lecturerecorder.data.remote.ApiClient
import com.querydoc.lecturerecorder.data.remote.ApiResult
import com.querydoc.lecturerecorder.data.remote.UsageSummary
import com.querydoc.lecturerecorder.recording.EnvironmentProfile

@Composable
fun SettingsScreen(
    api: ApiClient,
    farLecturerMode: Boolean,
    environmentProfile: EnvironmentProfile,
    pauseOnInterruptions: Boolean,
    pauseAudioOnLeave: Boolean,
    recognitionLanguage: RecognitionLanguage,
    onToggleFarLecturerMode: () -> Unit,
    onSetPauseOnInterruptions: (Boolean) -> Unit,
    onSetPauseAudioOnLeave: (Boolean) -> Unit,
    onSetEnvironmentProfile: (EnvironmentProfile) -> Unit,
    onSetRecognitionLanguage: (RecognitionLanguage) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    var logoutConfirm by remember { mutableStateOf(false) }
    var usage by remember { mutableStateOf<UsageSummary?>(null) }
    var usageError by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        when (val result = api.usage()) {
            is ApiResult.Ok -> usage = result.value
            is ApiResult.Failure -> usageError = result.message
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6FB))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = Color(0xFF0F172A))
            }
            Text(
                text = "Настройки",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsSection(title = "ЗАПИСЬ") {
                SettingRow(
                    icon = Icons.Filled.Mic,
                    title = "Режим записи",
                    subtitle = if (farLecturerMode) "Дальний лектор" else "Обычный микрофон",
                    onClick = onToggleFarLecturerMode,
                    trailing = { CheckMark() },
                )
                SettingDivider()
                SettingsLabelRow(
                    icon = Icons.Filled.SettingsVoice,
                    title = "Профиль микрофона",
                    subtitle = environmentProfile.labelRu(),
                )
                Column {
                    EnvironmentProfile.entries.forEach { profile ->
                        RadioRow(
                            title = profile.labelRu(),
                            selected = environmentProfile == profile,
                            onClick = { onSetEnvironmentProfile(profile) },
                        )
                    }
                }
                SettingDivider()
                ToggleRow(
                    icon = Icons.Filled.Notifications,
                    title = "Автопауза",
                    subtitle = "При входящем звонке запись ставится на паузу",
                    checked = pauseOnInterruptions,
                    onCheckedChange = onSetPauseOnInterruptions,
                )
            }

            SettingsSection(title = "АУДИО") {
                ToggleRow(
                    icon = Icons.Filled.Headphones,
                    title = "Пауза при переходе",
                    subtitle = "Останавливать аудио при уходе с вкладки «Аудио»",
                    checked = pauseAudioOnLeave,
                    onCheckedChange = onSetPauseAudioOnLeave,
                )
            }

            SettingsSection(title = "ЯЗЫК РАСПОЗНАВАНИЯ") {
                RecognitionLanguage.entries.forEach { language ->
                    RadioRow(
                        title = language.label,
                        subtitle = when (language) {
                            RecognitionLanguage.RU -> "Для русских лекций"
                            RecognitionLanguage.RU_EN -> "Для лекций с английскими терминами"
                            RecognitionLanguage.EN -> "Для английских лекций"
                        },
                        selected = recognitionLanguage == language,
                        onClick = { onSetRecognitionLanguage(language) },
                    )
                }
            }

            SettingsSection(title = "ЛИМИТЫ") {
                val current = usage
                if (current == null) {
                    Text(
                        if (usageError.isBlank()) "Загрузка…" else usageError,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    UsageRow("Запись", current.recordingUsedMinutes, current.recordingLimitMinutes, "мин")
                    UsageDivider()
                    UsageRow("Вопросы сегодня", current.qaUsed, current.qaLimit, "")
                    UsageDivider()
                    UsageRow("Проверка сегодня", current.studyKitUsed, current.studyKitLimit, "")
                }
            }

            SettingsSection(title = "АККАУНТ") {
                SettingRow(
                    icon = Icons.Filled.Logout,
                    title = "Выйти из аккаунта",
                    subtitle = "Потребуется снова ввести пароль",
                    tint = Color(0xFFDC2626),
                    onClick = { logoutConfirm = true },
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = Color(0xFF64748B))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("QueryDoc", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Версия ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (logoutConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { logoutConfirm = false },
            title = { Text("Выйти из аккаунта?") },
            text = { Text("Локальные записи останутся на устройстве. Для новой загрузки понадобится войти снова.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    logoutConfirm = false
                    onLogout()
                }) { Text("Выйти", color = Color(0xFFDC2626)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { logoutConfirm = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun UsageRow(label: String, used: Int, limit: Int, unit: String) {
    val fraction = if (limit > 0) (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f) else 0f
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF334155))
            Text(
                "$used / $limit${if (unit.isNotBlank()) " $unit" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF64748B),
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp).height(4.dp),
            color = if (fraction > 0.9f) Color(0xFFDC2626) else Color(0xFF4338CA),
            trackColor = Color(0xFFE2E8F0),
        )
    }
}

@Composable
private fun UsageDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(Color(0xFFF1F5F9)))
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 6.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = Color(0xFF4338CA),
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (tint == Color(0xFFDC2626)) tint else Color(0xFF0F172A))
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun SettingsLabelRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF4338CA), modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF0F172A))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
        }
    }
}

@Composable
private fun RadioRow(title: String, subtitle: String? = null, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) Color(0xFF4338CA) else Color(0xFF334155),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
            }
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF4338CA), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF4338CA), modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF0F172A))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 50.dp)
            .height(1.dp)
            .background(Color(0xFFF1F5F9)),
    )
}

@Composable
private fun CheckMark() {
    Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF4338CA), modifier = Modifier.size(20.dp))
}

@Composable
private fun Chevron() {
    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
}
