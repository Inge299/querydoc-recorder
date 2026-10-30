package com.querydoc.lecturerecorder.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.querydoc.lecturerecorder.data.remote.ApiClient
import com.querydoc.lecturerecorder.data.remote.ApiResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(api: ApiClient, onBack: () -> Unit, onCompleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf("form") }
    var callPhone by remember { mutableStateOf("") }
    var callCheckId by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun openDialer() {
        val normalized = callPhone.replace(Regex("[^+0-9]"), "")
        if (normalized.isNotBlank()) {
            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$normalized"))) }
        }
    }

    LaunchedEffect(stage, callCheckId) {
        if (stage != "call" || callCheckId.isBlank()) return@LaunchedEffect
        while (true) {
            delay(3_000L)
            when (api.confirmRegistrationCall(phone, callCheckId)) {
                is ApiResult.Ok -> {
                    when (val login = api.login(phone, password)) {
                        is ApiResult.Ok -> onCompleted()
                        is ApiResult.Failure -> {
                            error = login.message
                            stage = "form"
                        }
                    }
                    return@LaunchedEffect
                }
                is ApiResult.Failure -> Unit
            }
        }
    }

    AuthScreenShell(title = "Регистрация", subtitle = "Создайте аккаунт по номеру телефона", onBack = onBack) {
        if (stage == "form") {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Телефон, например +7 999 123-45-67") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text("Повторите пароль") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp))
            Button(
                onClick = {
                    if (busy) return@Button
                    if (password.length < 6) {
                        error = "Пароль должен быть не короче 6 символов"
                        return@Button
                    }
                    if (password != confirm) {
                        error = "Пароли не совпадают"
                        return@Button
                    }
                    busy = true
                    error = ""
                    scope.launch {
                        when (val result = api.register(phone, password)) {
                            is ApiResult.Ok -> {
                                if (result.value.callCheckId.isBlank()) {
                                    error = result.value.message.ifBlank { "Не удалось начать подтверждение телефона" }
                                } else {
                                    callPhone = result.value.callPhone
                                    callCheckId = result.value.callCheckId
                                    stage = "call"
                                }
                            }
                            is ApiResult.Failure -> error = result.message
                        }
                        busy = false
                    }
                },
                enabled = phone.isNotBlank() && password.isNotBlank() && confirm.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp)) else Text("Зарегистрироваться")
            }
        } else {
            Text("Позвоните на номер", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                callPhone.ifBlank { "номер не получен" },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
            Text(
                "Позвоните с указанного телефона. Мы автоматически проверим звонок — обычно это занимает несколько секунд.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { openDialer() },
                enabled = callPhone.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text("Позвонить") }
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        when (api.confirmRegistrationCall(phone, callCheckId)) {
                            is ApiResult.Ok -> {
                                when (api.login(phone, password)) {
                                    is ApiResult.Ok -> onCompleted()
                                    is ApiResult.Failure -> error = "Телефон подтверждён, но вход не выполнен"
                                }
                            }
                            is ApiResult.Failure -> error = "Звонок ещё не подтверждён. Завершите вызов и попробуйте снова."
                        }
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(if (busy) "Проверяем…" else "Я позвонил — проверить") }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp))
            TextButton(onClick = { stage = "form" }, modifier = Modifier.padding(top = 4.dp)) { Text("Изменить номер") }
        }
    }
}

@Composable
fun RecoveryScreen(api: ApiClient, onBack: () -> Unit, onCompleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var callPhone by remember { mutableStateOf("") }
    var callCheckId by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf("form") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun openDialer() {
        val normalized = callPhone.replace(Regex("[^+0-9]"), "")
        if (normalized.isNotBlank()) runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$normalized"))) }
    }

    LaunchedEffect(stage, callCheckId) {
        if (stage != "call" || callCheckId.isBlank()) return@LaunchedEffect
        while (true) {
            delay(3_000L)
            when (api.confirmPasswordResetCall(phone, callCheckId)) {
                is ApiResult.Ok -> {
                    stage = "password"
                    error = ""
                    return@LaunchedEffect
                }
                is ApiResult.Failure -> Unit
            }
        }
    }

    AuthScreenShell(title = "Восстановление доступа", subtitle = "Подтвердите телефон и задайте новый пароль", onBack = onBack) {
        when (stage) {
            "form" -> {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Телефон") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp))
                Button(
                    onClick = {
                        busy = true
                        error = ""
                        scope.launch {
                            when (val result = api.requestPasswordReset(phone)) {
                                is ApiResult.Ok -> {
                                    if (result.value.callCheckId.isBlank()) {
                                        error = "Для этого номера недоступно восстановление"
                                    } else {
                                        callPhone = result.value.callPhone
                                        callCheckId = result.value.callCheckId
                                        stage = "call"
                                    }
                                }
                                is ApiResult.Failure -> error = result.message
                            }
                            busy = false
                        }
                    },
                    enabled = phone.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) { Text(if (busy) "Проверяем…" else "Продолжить") }
            }
            "call" -> {
                Text("Позвоните на номер", style = MaterialTheme.typography.labelLarge)
                Text(callPhone.ifBlank { "номер не получен" }, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
                Text("После звонка нажмите проверку или подождите — приложение проверит автоматически.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { openDialer() }, enabled = callPhone.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("Позвонить") }
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            when (api.confirmPasswordResetCall(phone, callCheckId)) {
                                is ApiResult.Ok -> { stage = "password"; error = "" }
                                is ApiResult.Failure -> error = "Звонок ещё не подтверждён"
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text(if (busy) "Проверяем…" else "Я позвонил — проверить") }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp))
            }
            else -> {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Новый пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Повторите пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp))
                Button(
                    onClick = {
                        if (password.length < 6) { error = "Пароль должен быть не короче 6 символов"; return@Button }
                        if (password != confirm) { error = "Пароли не совпадают"; return@Button }
                        busy = true
                        scope.launch {
                            when (val result = api.resetPassword(phone, password)) {
                                is ApiResult.Ok -> onCompleted()
                                is ApiResult.Failure -> error = result.message
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) { Text(if (busy) "Сохраняем…" else "Сохранить пароль") }
            }
        }
    }
}

@Composable
private fun AuthScreenShell(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        content()
        TextButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Text("Назад") }
    }
}
