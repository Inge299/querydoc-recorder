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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.querydoc.lecturerecorder.data.remote.ApiClient
import com.querydoc.lecturerecorder.data.remote.ApiResult
import com.querydoc.lecturerecorder.data.remote.StudyGroupSummary
import com.querydoc.lecturerecorder.data.remote.TeacherInviteSummary
import kotlinx.coroutines.launch

private val Ink = Color(0xFF0F172A)
private val Muted = Color(0xFF64748B)
private val Accent = Color(0xFF4338CA)
private val Paper = Color(0xFFFFFFFF)
private val Canvas = Color(0xFFF4F6FB)

/** Mirrors the server rule in `app/api/v1/mobile/groups/route.ts`. */
private const val FREE_PLAN_ID = "free"

@Composable
fun MaterialsScreen(
    api: ApiClient,
    onBack: () -> Unit,
    onOpenConspects: () -> Unit,
    onOpenGroup: (StudyGroupSummary) -> Unit,
) {
    var groups by remember { mutableStateOf<List<StudyGroupSummary>>(emptyList()) }
    var invites by remember { mutableStateOf<List<TeacherInviteSummary>>(emptyList()) }
    var conspectCount by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }
    var groupsError by remember { mutableStateOf("") }
    var inviteNotice by remember { mutableStateOf("") }
    var inviteError by remember { mutableStateOf("") }
    var inviteBusyId by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    var createError by remember { mutableStateOf("") }
    var createBusy by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }
    var joinError by remember { mutableStateOf("") }
    var joinBusy by remember { mutableStateOf(false) }
    var planId by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reload) {
        loading = true
        groupsError = ""
        when (val result = api.usage()) {
            is ApiResult.Ok -> planId = result.value.planId
            is ApiResult.Failure -> Unit
        }
        when (val result = api.listStudyGroups()) {
            is ApiResult.Ok -> groups = result.value
            is ApiResult.Failure -> groupsError = result.message
        }
        when (val result = api.listTeacherInvites()) {
            is ApiResult.Ok -> invites = result.value
            is ApiResult.Failure -> Unit
        }
        when (val result = api.listConspects()) {
            is ApiResult.Ok -> conspectCount = result.value.size
            is ApiResult.Failure -> Unit
        }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
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
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = Ink)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Материалы",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                )
                Text(
                    "Конспекты и группы",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
            IconButton(onClick = { reload += 1 }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Обновить", tint = Accent)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            Spacer(Modifier.height(6.dp))
            ConspectsHero(count = conspectCount, onClick = onOpenConspects)

            if (invites.isNotEmpty() || inviteNotice.isNotBlank() || inviteError.isNotBlank()) {
                Spacer(Modifier.height(18.dp))
                SectionLabel("ПРИГЛАШЕНИЯ")
                if (inviteNotice.isNotBlank()) {
                    Text(
                        inviteNotice,
                        color = Color(0xFF047857),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (inviteError.isNotBlank()) {
                    Text(
                        inviteError,
                        color = Color(0xFFB91C1C),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                invites.forEach { invite ->
                    InviteCard(
                        invite = invite,
                        busy = inviteBusyId.isNotEmpty(),
                        onAccept = {
                            inviteBusyId = invite.id
                            inviteError = ""
                            inviteNotice = ""
                            scope.launch {
                                when (val result = api.acceptTeacherInvite(invite.id)) {
                                    is ApiResult.Ok -> {
                                        invites = invites.filterNot { it.id == invite.id }
                                        inviteNotice = "Вы приняли приглашение: ${invite.subjectName}."
                                        reload += 1
                                    }
                                    is ApiResult.Failure -> inviteError = result.message
                                }
                                inviteBusyId = ""
                            }
                        },
                        onDecline = {
                            inviteBusyId = invite.id
                            inviteError = ""
                            inviteNotice = ""
                            scope.launch {
                                when (val result = api.declineTeacherInvite(invite.id)) {
                                    is ApiResult.Ok -> {
                                        invites = invites.filterNot { it.id == invite.id }
                                        inviteNotice = "Приглашение отклонено."
                                    }
                                    is ApiResult.Failure -> inviteError = result.message
                                }
                                inviteBusyId = ""
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel("ГРУППЫ")
            if (groupsError.isNotBlank()) {
                Text(
                    groupsError,
                    color = Color(0xFFB91C1C),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            when {
                loading && groups.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 30.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                }

                groups.isEmpty() -> EmptyGroups()
                else -> groups.forEach { group ->
                    GroupCard(group = group, onClick = { onOpenGroup(group) })
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        joinCode = ""
                        joinError = ""
                        showJoin = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Войти по коду")
                }
                Button(
                    onClick = {
                        createName = ""
                        createError = ""
                        showCreate = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Создать")
                }
            }
            Text(
                if (planId == FREE_PLAN_ID) {
                    "Создать группу можно на платном тарифе. Предметы и преподаватели настраиваются внутри группы."
                } else {
                    "Предметы и преподаватели настраиваются внутри группы."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { if (!createBusy) showCreate = false },
            title = { Text("Создать группу") },
            text = {
                Column {
                    Text("Вы станете владельцем группы. Затем добавьте предметы и назначьте преподавателей.")
                    OutlinedTextField(
                        value = createName,
                        onValueChange = {
                            createName = it.take(60)
                            createError = ""
                        },
                        singleLine = true,
                        label = { Text("Название группы") },
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    )
                    if (createError.isNotBlank()) {
                        Text(
                            createError,
                            color = Color(0xFFB91C1C),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = createName.isNotBlank() && !createBusy,
                    onClick = {
                        createBusy = true
                        createError = ""
                        scope.launch {
                            when (val result = api.createStudyGroup(createName)) {
                                is ApiResult.Ok -> {
                                    groups = (groups + result.value).distinctBy { it.id }
                                    showCreate = false
                                    onOpenGroup(result.value)
                                }
                                is ApiResult.Failure -> createError = result.message
                            }
                            createBusy = false
                        }
                    },
                ) { Text(if (createBusy) "Создаём…" else "Создать") }
            },
            dismissButton = {
                TextButton(enabled = !createBusy, onClick = { showCreate = false }) { Text("Отмена") }
            },
        )
    }

    if (showJoin) {
        AlertDialog(
            onDismissRequest = { if (!joinBusy) showJoin = false },
            title = { Text("Присоединиться к группе") },
            text = {
                Column {
                    Text("Введите код, который выдал владелец или преподаватель.")
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = {
                            joinCode = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(8)
                            joinError = ""
                        },
                        singleLine = true,
                        label = { Text("Код группы") },
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    )
                    if (joinError.isNotBlank()) {
                        Text(
                            joinError,
                            color = Color(0xFFB91C1C),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = joinCode.isNotBlank() && !joinBusy,
                    onClick = {
                        joinBusy = true
                        joinError = ""
                        scope.launch {
                            when (val result = api.joinStudyGroup(joinCode)) {
                                is ApiResult.Ok -> {
                                    groups = (groups + result.value).distinctBy { it.id }
                                    showJoin = false
                                    onOpenGroup(result.value)
                                }
                                is ApiResult.Failure -> joinError = result.message
                            }
                            joinBusy = false
                        }
                    },
                ) { Text(if (joinBusy) "Входим…" else "Войти") }
            },
            dismissButton = {
                TextButton(enabled = !joinBusy, onClick = { showJoin = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun ConspectsHero(count: Int?, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(listOf(Color(0xFFEEF2FF), Color(0xFFE0E7FF))),
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Brush.linearGradient(listOf(Accent, Color(0xFF7C6AE8))),
                            RoundedCornerShape(15.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.MenuBook, contentDescription = null, tint = Color.White, modifier = Modifier.size(25.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Мои конспекты",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E1B4B),
                    )
                    Text(
                        when {
                            count == null -> "Лекции, документы и проверки"
                            count == 1 -> "1 конспект"
                            count in 2..4 -> "$count конспекта"
                            else -> "$count конспектов"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = "Открыть конспекты", tint = Accent)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF64748B),
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun GroupCard(group: StudyGroupSummary, onClick: () -> Unit) {
    val roleLabel = when (group.role) {
        "owner" -> "Владелец"
        "teacher" -> "Преподаватель"
        else -> "Участник"
    }
    val roleColor = when (group.role) {
        "owner" -> Accent
        "teacher" -> Color(0xFF0369A1)
        else -> Color(0xFF047857)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(roleColor.copy(alpha = 0.10f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (group.role == "teacher") Icons.Filled.School else Icons.Filled.Group,
                    contentDescription = null,
                    tint = roleColor,
                    modifier = Modifier.size(23.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 3.dp),
                ) {
                    Text(
                        roleLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = roleColor,
                    )
                    Text("·", color = Muted, style = MaterialTheme.typography.labelSmall)
                    Text(
                        membersLabel(group.memberCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = "Открыть группу", tint = Color(0xFF94A3B8))
        }
    }
}

private fun membersLabel(count: Int): String = when {
    count <= 0 -> "пока никого"
    count == 1 -> "1 участник"
    count in 2..4 -> "$count участника"
    else -> "$count участников"
}

@Composable
private fun EmptyGroups() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Group, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(34.dp))
            Text(
                "Пока нет групп",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "Создайте группу и пригласите преподавателя или вступите по коду.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun InviteCard(
    invite: TeacherInviteSummary,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                invite.groupName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
            )
            Text(
                "${invite.subjectName} · приглашение преподавать",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (invite.message.isNotBlank()) {
                Text(
                    invite.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF475569),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onAccept,
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) { Text("Принять") }
                OutlinedButton(
                    onClick = onDecline,
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Отклонить") }
            }
        }
    }
}
