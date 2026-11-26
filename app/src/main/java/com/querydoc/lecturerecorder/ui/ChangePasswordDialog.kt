package com.querydoc.lecturerecorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.querydoc.lecturerecorder.data.remote.ApiClient
import com.querydoc.lecturerecorder.data.remote.ApiResult
import kotlinx.coroutines.launch

@Composable
fun ChangePasswordDialog(
    api: ApiClient,
    onDismiss: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var success by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (success) "Пароль изменён" else "Сменить пароль") },
        text = {
            if (success) {
                Text("Новый пароль сохранён. Используйте его при следующем входе.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it; error = "" },
                        label = { Text("Текущий пароль") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; error = "" },
                        label = { Text("Новый пароль") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = repeatPassword,
                        onValueChange = { repeatPassword = it; error = "" },
                        label = { Text("Повторите новый пароль") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (error.isNotBlank()) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        "Не помните текущий пароль? Восстановите его по телефону.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            onDismiss()
                            onForgotPassword()
                        },
                        enabled = !busy,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Забыли пароль?")
                    }
                }
            }
        },
        confirmButton = {
            if (success) {
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            } else {
                Button(
                    enabled = !busy,
                    onClick = {
                        val validation = when {
                            currentPassword.isBlank() -> "Введите текущий пароль"
                            newPassword.length < 6 -> "Новый пароль должен быть не короче 6 символов"
                            newPassword != repeatPassword -> "Пароли не совпадают"
                            newPassword == currentPassword -> "Новый пароль совпадает с текущим"
                            else -> ""
                        }
                        if (validation.isNotBlank()) {
                            error = validation
                            return@Button
                        }
                        busy = true
                        error = ""
                        scope.launch {
                            when (val result = api.changePassword(currentPassword, newPassword)) {
                                is ApiResult.Ok -> {
                                    success = true
                                    currentPassword = ""
                                    newPassword = ""
                                    repeatPassword = ""
                                }
                                is ApiResult.Failure -> error = result.message
                            }
                            busy = false
                        }
                    },
                ) {
                    Text(if (busy) "Меняем…" else "Сменить")
                }
            }
        },
        dismissButton = {
            if (!success) {
                TextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена") }
            }
        },
    )
}
