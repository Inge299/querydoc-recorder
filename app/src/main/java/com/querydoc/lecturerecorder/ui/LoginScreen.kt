package com.querydoc.lecturerecorder.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.querydoc.lecturerecorder.config.AppConfig
import com.querydoc.lecturerecorder.data.remote.ApiClient
import com.querydoc.lecturerecorder.data.remote.ApiResult
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(api: ApiClient, onLoggedIn: () -> Unit, onRegister: () -> Unit, onRecover: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun openWeb(path: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${AppConfig.Api.baseUrl}$path"))) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Text("QueryDoc", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Запись лекции → конспект",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF64748B),
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(22.dp))

        CompactLoginField(
            value = phone,
            onValueChange = { phone = it },
            placeholder = "Телефон",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(10.dp))
        CompactLoginField(
            value = password,
            onValueChange = { password = it },
            placeholder = "Пароль",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            visualTransformation = PasswordVisualTransformation(),
        )

        if (error.isNotBlank()) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Button(
            onClick = {
                if (busy) return@Button
                busy = true
                error = ""
                scope.launch {
                    when (val result = api.login(phone, password)) {
                        is ApiResult.Ok -> {
                            password = ""
                            onLoggedIn()
                        }
                        is ApiResult.Failure -> {
                            error = when (result.code) {
                                "PHONE_NOT_VERIFIED" -> "Подтвердите телефон на сайте QueryDoc."
                                "RATE_LIMITED" -> "Слишком много попыток. Попробуйте позже."
                                else -> result.message
                            }
                        }
                    }
                    busy = false
                }
            },
            enabled = !busy && phone.isNotBlank() && password.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4338CA)),
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = 14.dp),
        ) {
            if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.height(18.dp))
            else Text("Войти", fontWeight = FontWeight.SemiBold)
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TextButton(onClick = onRegister) { Text("Создать аккаунт") }
            TextButton(onClick = onRecover, modifier = Modifier.padding(top = 0.dp)) { Text("Забыли пароль?") }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CompactLoginField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (focused) Color(0xFF4338CA) else Color(0xFFCBD5E1)
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF0F172A)),
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        cursorBrush = SolidColor(Color(0xFF4338CA)),
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color.White, shape)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 16.dp),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                if (value.isBlank()) {
                    Text(placeholder, color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodyMedium)
                }
                innerTextField()
            }
        },
    )
}
