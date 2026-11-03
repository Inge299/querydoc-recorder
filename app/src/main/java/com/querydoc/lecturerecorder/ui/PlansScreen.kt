package com.querydoc.lecturerecorder.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.querydoc.lecturerecorder.data.remote.ApiClient
import com.querydoc.lecturerecorder.data.remote.ApiResult
import com.querydoc.lecturerecorder.data.remote.PlanInfo
import com.querydoc.lecturerecorder.data.remote.PlansPayload
import kotlinx.coroutines.launch

@Composable
fun PlansScreen(api: ApiClient, onBack: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf<PlansPayload?>(null) }
    var buyingId by remember { mutableStateOf("") }

    suspend fun reload() {
        loading = true
        when (val result = api.plans()) {
            is ApiResult.Ok -> {
                payload = result.value
                error = ""
            }
            is ApiResult.Failure -> error = result.message
        }
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

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
                "Тарифы",
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Лимиты применяются на сервере. После оплаты они обновятся в приложении.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B),
            )
            if (loading) {
                CircularProgressIndicator(color = Color(0xFF4338CA), modifier = Modifier.padding(24.dp))
            }
            if (error.isNotBlank()) {
                Text(error, color = Color(0xFFB91C1C), style = MaterialTheme.typography.bodySmall)
            }
            payload?.let { data ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1320)),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Текущий тариф", color = Color(0xFFC7D2FE), style = MaterialTheme.typography.labelMedium)
                        Text(data.currentPlanName.ifBlank { data.currentPlanId }, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (data.testBilling) {
                            Text("Тестовый режим: оплата без реальных денег", color = Color(0xFFFBBF24), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                data.plans.forEach { plan ->
                    PlanCard(
                        plan = plan,
                        current = plan.id == data.currentPlanId,
                        testBilling = data.testBilling,
                        busy = buyingId == plan.id,
                        onBuy = {
                            if (!data.testBilling) return@PlanCard
                            buyingId = plan.id
                            scope.launch {
                                when (val result = api.testPurchase(plan.id)) {
                                    is ApiResult.Ok -> {
                                        Toast.makeText(context, "Тестовый тариф активирован", Toast.LENGTH_SHORT).show()
                                        reload()
                                        onChanged()
                                    }
                                    is ApiResult.Failure -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                }
                                buyingId = ""
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanCard(plan: PlanInfo, current: Boolean, testBilling: Boolean, busy: Boolean, onBuy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                Text(if (plan.priceMonthly == 0) "Бесплатно" else "${plan.priceMonthly} ₽/мес", fontWeight = FontWeight.Bold, color = Color(0xFF4338CA))
            }
            Text(
                "${plan.monthlyRecordingMinutes / 60} ч записей · ${plan.dailyQa} вопросов/день · хранение ${plan.storageDays} дн.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B),
            )
            Button(
                onClick = onBuy,
                enabled = !current && !busy && testBilling,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(when {
                    current -> "Текущий тариф"
                    busy -> "Активируем…"
                    testBilling -> "Активировать тестовый тариф"
                    else -> "Оплата скоро"
                })
            }
        }
    }
}
