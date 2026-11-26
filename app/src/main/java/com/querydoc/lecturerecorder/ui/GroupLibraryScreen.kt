package com.querydoc.lecturerecorder.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.querydoc.lecturerecorder.config.AppLinks
import com.querydoc.lecturerecorder.data.remote.AccountSummary
import com.querydoc.lecturerecorder.data.remote.ApiClient
import com.querydoc.lecturerecorder.data.remote.ApiResult
import com.querydoc.lecturerecorder.data.remote.ConspectSummary
import com.querydoc.lecturerecorder.data.remote.GroupLibraryItem
import com.querydoc.lecturerecorder.data.remote.GroupSubjectSummary
import com.querydoc.lecturerecorder.data.remote.StudyGroupSummary
import com.querydoc.lecturerecorder.data.remote.TeacherInviteCreated
import kotlinx.coroutines.launch

private val GroupInk = Color(0xFF0F172A)
private val GroupMuted = Color(0xFF64748B)
private val GroupAccent = Color(0xFF4338CA)
private val GroupCanvas = Color(0xFFF4F6FB)
private val GroupPaper = Color(0xFFFFFFFF)

@Composable
fun GroupLibraryScreen(
    api: ApiClient,
    group: StudyGroupSummary,
    onBack: () -> Unit,
) {
    var tab by remember { mutableStateOf("library") }
    var items by remember { mutableStateOf<List<GroupLibraryItem>>(emptyList()) }
    var groupSubjects by remember { mutableStateOf<List<GroupSubjectSummary>>(emptyList()) }
    var me by remember { mutableStateOf<AccountSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var actionMessage by remember { mutableStateOf("") }
    var filterSubjectId by remember { mutableStateOf("") }
    var selectedGroupSubjectId by remember { mutableStateOf("") }
    var conspects by remember { mutableStateOf<List<ConspectSummary>>(emptyList()) }
    var showShare by remember { mutableStateOf(false) }
    var shareBusy by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<GroupLibraryItem?>(null) }
    var pendingDelete by remember { mutableStateOf<GroupLibraryItem?>(null) }
    var deleteBusy by remember { mutableStateOf(false) }
    var showCreateSubject by remember { mutableStateOf(false) }
    var subjectName by remember { mutableStateOf("") }
    var subjectAsTeacher by remember { mutableStateOf(true) }
    var subjectBusy by remember { mutableStateOf(false) }
    var subjectError by remember { mutableStateOf("") }
    var assigningSubjectId by remember { mutableStateOf("") }
    var showInvite by remember { mutableStateOf(false) }
    var inviteSubjectId by remember { mutableStateOf("") }
    var inviteContact by remember { mutableStateOf("") }
    var inviteBusy by remember { mutableStateOf(false) }
    var inviteError by remember { mutableStateOf("") }
    var createdInvite by remember { mutableStateOf<TeacherInviteCreated?>(null) }
    var showQr by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val isOwner = group.role == "owner"
    val myContact = me?.phone?.takeIf { it.isNotBlank() }
        ?: me?.email?.takeIf { it.isNotBlank() }
        ?: ""
    val canTakeQuiz = !(group.role == "teacher" && !group.isMember)

    fun amTeacher(subject: GroupSubjectSummary): Boolean =
        subject.teachers.any { it.userId == me?.id } ||
            (subject.teacherUserId.isNotBlank() && subject.teacherUserId == me?.id)

    fun load() {
        scope.launch {
            loading = true
            error = ""
            when (val result = api.groupLibrary(group.id)) {
                is ApiResult.Ok -> items = result.value
                is ApiResult.Failure -> error = result.message
            }
            when (val result = api.groupSubjects(group.id)) {
                is ApiResult.Ok -> {
                    groupSubjects = result.value
                    if (selectedGroupSubjectId.isBlank()) {
                        selectedGroupSubjectId = result.value.firstOrNull()?.id.orEmpty()
                    }
                }
                is ApiResult.Failure -> if (error.isBlank()) error = result.message
            }
            when (val result = api.me()) {
                is ApiResult.Ok -> me = result.value
                is ApiResult.Failure -> Unit
            }
            loading = false
        }
    }

    fun loadConspects() {
        scope.launch {
            when (val result = api.listConspects()) {
                is ApiResult.Ok -> conspects = result.value
                is ApiResult.Failure -> error = result.message
            }
            when (val result = api.groupSubjects(group.id)) {
                is ApiResult.Ok -> {
                    groupSubjects = result.value
                    if (selectedGroupSubjectId.isBlank()) {
                        selectedGroupSubjectId = result.value.firstOrNull()?.id.orEmpty()
                    }
                }
                is ApiResult.Failure -> Unit
            }
        }
    }

    fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Отправить приглашение"))
    }

    fun copyText(text: String, label: String = "Ссылка скопирована") {
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
    }

    fun assignSelf(subject: GroupSubjectSummary) {
        if (myContact.isBlank()) {
            error = "В аккаунте нет телефона или email — пригласите преподавателя по контакту."
            return
        }
        assigningSubjectId = subject.id
        error = ""
        scope.launch {
            when (val result = api.assignGroupSubjectTeacher(group.id, subject.id, myContact)) {
                is ApiResult.Ok -> {
                    groupSubjects = groupSubjects.map { if (it.id == result.value.id) result.value else it }
                    actionMessage = "Вы назначены преподавателем предмета «${result.value.name}»."
                }
                is ApiResult.Failure -> error = result.message
            }
            assigningSubjectId = ""
        }
    }

    LaunchedEffect(group.id) { load() }

    val activeItem = selectedItem
    if (activeItem != null) {
        GroupLibraryReaderScreen(
            api = api,
            groupId = group.id,
            item = activeItem,
            onBack = { selectedItem = null },
            canTakeQuiz = canTakeQuiz,
        )
        return
    }
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GroupCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = GroupInk)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = GroupInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${roleLabel(group.role)} · ${groupMembersLabel(group.memberCount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = GroupMuted,
                )
            }
            if (tab == "library") {
                IconButton(onClick = {
                    loadConspects()
                    showShare = true
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Добавить конспект", tint = GroupAccent)
                }
            }
            if (isOwner && group.inviteCode.isNotBlank()) {
                IconButton(onClick = { showQr = true }) {
                    Icon(Icons.Filled.QrCode2, contentDescription = "Код и QR группы", tint = GroupAccent)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp)
                .background(Color(0xFFE7EAF3), RoundedCornerShape(12.dp))
                .padding(3.dp),
        ) {
            TabPill("Библиотека", tab == "library", Modifier.weight(1f)) { tab = "library" }
            TabPill("Предметы", tab == "subjects", Modifier.weight(1f)) { tab = "subjects" }
        }

        if (actionMessage.isNotBlank()) {
            Text(
                actionMessage,
                color = Color(0xFF047857),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        if (error.isNotBlank()) {
            Text(
                error,
                color = Color(0xFFB91C1C),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GroupAccent)
            }
        } else if (tab == "library") {
            LibraryTab(
                items = items,
                groupSubjects = groupSubjects,
                filterSubjectId = filterSubjectId,
                onFilter = { filterSubjectId = it },
                onAdd = {
                    loadConspects()
                    showShare = true
                },
                onOpen = { selectedItem = it },
                onDelete = { pendingDelete = it },
            )
        } else {
            SubjectsTab(
                subjects = groupSubjects,
                isOwner = isOwner,
                isTeacher = group.role == "teacher",
                amTeacher = ::amTeacher,
                assigningSubjectId = assigningSubjectId,
                onAssignSelf = ::assignSelf,
                onInvite = { subject ->
                    inviteSubjectId = subject.id
                    inviteContact = ""
                    inviteError = ""
                    showInvite = true
                },
                onAddSubject = {
                    subjectName = ""
                    subjectAsTeacher = true
                    subjectError = ""
                    showCreateSubject = true
                },
            )
        }
    }

    if (showShare) {
        AlertDialog(
            onDismissRequest = { if (!shareBusy) showShare = false },
            title = { Text("Добавить конспект") },
            text = {
                Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Будет добавлена групповая копия. Личный конспект останется у вас.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GroupMuted,
                    )
                    if (groupSubjects.isNotEmpty()) {
                        Text(
                            "Предмет группы",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF475569),
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .padding(vertical = 6.dp)
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            groupSubjects.forEach { subject ->
                                FilterChip(
                                    selected = selectedGroupSubjectId == subject.id,
                                    onClick = { selectedGroupSubjectId = subject.id },
                                    label = { Text(subject.name) },
                                )
                            }
                        }
                    } else {
                        Text(
                            "Сначала добавьте предмет на вкладке «Предметы».",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB45309),
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    if (conspects.isEmpty()) {
                        Text(
                            "У вас пока нет конспектов для добавления.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GroupMuted,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    conspects.forEach { conspect ->
                        TextButton(
                            enabled = !shareBusy,
                            onClick = {
                                shareBusy = true
                                scope.launch {
                                    when (val result = api.shareConspectToGroup(group.id, conspect.sessionId, selectedGroupSubjectId)) {
                                        is ApiResult.Ok -> {
                                            showShare = false
                                            actionMessage = "Конспект добавлен в библиотеку группы."
                                            load()
                                        }
                                        is ApiResult.Failure -> error = result.message
                                    }
                                    shareBusy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                conspect.title.ifBlank { "Конспект" },
                                modifier = Modifier.fillMaxWidth(),
                                color = GroupInk,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !shareBusy, onClick = { showShare = false }) { Text("Закрыть") }
            },
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { if (!deleteBusy) pendingDelete = null },
            title = { Text("Удалить материал?") },
            text = {
                Text(
                    "«${item.title.ifBlank { "Конспект" }}» будет удалён из общей библиотеки группы. Личная копия останется у автора.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !deleteBusy,
                    onClick = {
                        deleteBusy = true
                        scope.launch {
                            when (val result = api.deleteGroupLibraryItem(group.id, item.id)) {
                                is ApiResult.Ok -> {
                                    actionMessage = "Материал удалён из библиотеки группы."
                                    pendingDelete = null
                                    load()
                                }
                                is ApiResult.Failure -> {
                                    error = result.message
                                    pendingDelete = null
                                }
                            }
                            deleteBusy = false
                        }
                    },
                ) { Text(if (deleteBusy) "Удаляем…" else "Удалить", color = Color(0xFFB91C1C)) }
            },
            dismissButton = {
                TextButton(enabled = !deleteBusy, onClick = { pendingDelete = null }) { Text("Отмена") }
            },
        )
    }

    if (showCreateSubject) {
        AlertDialog(
            onDismissRequest = { if (!subjectBusy) showCreateSubject = false },
            title = { Text("Новый предмет") },
            text = {
                Column {
                    Text("Предмет появится в библиотеке и в проверках группы.")
                    OutlinedTextField(
                        value = subjectName,
                        onValueChange = {
                            subjectName = it.take(80)
                            subjectError = ""
                        },
                        singleLine = true,
                        label = { Text("Название предмета") },
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Я буду преподавателем",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GroupInk,
                            )
                            Text(
                                "Можно выключить и пригласить преподавателя позже.",
                                style = MaterialTheme.typography.bodySmall,
                                color = GroupMuted,
                            )
                        }
                        Switch(
                            checked = subjectAsTeacher,
                            onCheckedChange = { subjectAsTeacher = it },
                        )
                    }
                    if (subjectError.isNotBlank()) {
                        Text(
                            subjectError,
                            color = Color(0xFFB91C1C),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = subjectName.isNotBlank() && !subjectBusy,
                    onClick = {
                        subjectBusy = true
                        subjectError = ""
                        scope.launch {
                            when (val result = api.createGroupSubject(
                                groupId = group.id,
                                name = subjectName,
                                teacherContact = if (subjectAsTeacher) myContact else "",
                            )) {
                                is ApiResult.Ok -> {
                                    groupSubjects = (groupSubjects + result.value).distinctBy { it.id }
                                    selectedGroupSubjectId = result.value.id
                                    showCreateSubject = false
                                    actionMessage = "Предмет «${result.value.name}» добавлен."
                                }
                                is ApiResult.Failure -> subjectError = result.message
                            }
                            subjectBusy = false
                        }
                    },
                ) { Text(if (subjectBusy) "Создаём…" else "Создать") }
            },
            dismissButton = {
                TextButton(enabled = !subjectBusy, onClick = { showCreateSubject = false }) { Text("Отмена") }
            },
        )
    }

    if (showInvite) {
        val inviteSubject = groupSubjects.firstOrNull { it.id == inviteSubjectId }
        AlertDialog(
            onDismissRequest = { if (!inviteBusy) showInvite = false },
            title = { Text("Пригласить преподавателя") },
            text = {
                Column {
                    Text(
                        "Отправьте ссылку или приглашение на телефон/email. Преподаватель примет его в приложении.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GroupMuted,
                    )
                    if (groupSubjects.size > 1) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            groupSubjects.forEach { subject ->
                                FilterChip(
                                    selected = inviteSubjectId == subject.id,
                                    onClick = { inviteSubjectId = subject.id },
                                    label = { Text(subject.name) },
                                )
                            }
                        }
                    }
                    if (inviteSubject != null) {
                        Text(
                            "Предмет: ${inviteSubject.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = GroupInk,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    OutlinedTextField(
                        value = inviteContact,
                        onValueChange = {
                            inviteContact = it
                            inviteError = ""
                        },
                        singleLine = true,
                        label = { Text("Телефон или email преподавателя") },
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth(),
                    )
                    if (inviteError.isNotBlank()) {
                        Text(
                            inviteError,
                            color = Color(0xFFB91C1C),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = inviteContact.isNotBlank() && inviteSubjectId.isNotBlank() && !inviteBusy,
                    onClick = {
                        inviteBusy = true
                        inviteError = ""
                        scope.launch {
                            when (val result = api.createTeacherInvite(group.id, inviteSubjectId, inviteContact)) {
                                is ApiResult.Ok -> {
                                    createdInvite = result.value
                                    showInvite = false
                                }
                                is ApiResult.Failure -> inviteError = result.message
                            }
                            inviteBusy = false
                        }
                    },
                ) { Text(if (inviteBusy) "Создаём…" else "Пригласить") }
            },
            dismissButton = {
                TextButton(enabled = !inviteBusy, onClick = { showInvite = false }) { Text("Отмена") }
            },
        )
    }

    createdInvite?.let { invite ->
        AlertDialog(
            onDismissRequest = { createdInvite = null },
            title = { Text("Приглашение создано") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Ссылка для ${invite.contactMasked}. Её можно отправить преподавателю.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GroupMuted,
                    )
                    if (invite.acceptUrl.isNotBlank()) {
                        QrCodeImage(
                            content = invite.acceptUrl,
                            size = 190.dp,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            invite.acceptUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = GroupAccent,
                            modifier = Modifier.padding(top = 6.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (invite.acceptUrl.isNotBlank()) shareText(invite.acceptUrl)
                }) { Text("Поделиться") }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (invite.acceptUrl.isNotBlank()) copyText(invite.acceptUrl, "Ссылка-приглашение скопирована")
                    createdInvite = null
                }) { Text("Копировать") }
            },
        )
    }

    if (showQr && group.inviteCode.isNotBlank()) {
        val joinUrl = api.webUrl("/join/${group.inviteCode}")
        AlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text("Код группы") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Студенты вступают по коду или QR. Код действует, пока вы его не смените.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GroupMuted,
                    )
                    Text(
                        group.inviteCode,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = GroupInk,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    QrCodeImage(
                        content = joinUrl,
                        size = 210.dp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        joinUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = GroupAccent,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { shareText("Вступайте в группу «${group.name}» в QueryDoc: $joinUrl") }) {
                    Text("Поделиться")
                }
            },
            dismissButton = {
                TextButton(onClick = { copyText(joinUrl, "Ссылка на группу скопирована") }) {
                    Text("Копировать")
                }
            },
        )
    }
}

@Composable
private fun TabPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(if (selected) GroupPaper else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) GroupInk else GroupMuted,
        )
    }
}

@Composable
private fun LibraryTab(
    items: List<GroupLibraryItem>,
    groupSubjects: List<GroupSubjectSummary>,
    filterSubjectId: String,
    onFilter: (String) -> Unit,
    onAdd: () -> Unit,
    onOpen: (GroupLibraryItem) -> Unit,
    onDelete: (GroupLibraryItem) -> Unit,
) {
    val filtered = items.filter { filterSubjectId.isBlank() || it.groupSubjectId == filterSubjectId }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "Общие материалы группы. Личная копия остаётся у автора; владелец может удалить любой материал, преподаватель — свой.",
            style = MaterialTheme.typography.bodySmall,
            color = GroupMuted,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        if (groupSubjects.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                FilterChip(
                    selected = filterSubjectId.isBlank(),
                    onClick = { onFilter("") },
                    label = { Text("Все") },
                )
                groupSubjects.forEach { subject ->
                    FilterChip(
                        selected = filterSubjectId == subject.id,
                        onClick = { onFilter(subject.id) },
                        label = { Text(subject.name) },
                    )
                }
            }
        }
        if (filtered.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = GroupPaper),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.Group, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(34.dp))
                    Text(
                        "Пока пусто",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = GroupInk,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "Добавьте свой конспект в общую библиотеку.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GroupMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Button(
                        onClick = onAdd,
                        modifier = Modifier.padding(top = 14.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GroupAccent),
                    ) {
                        Text("Добавить конспект")
                    }
                }
            }
        } else {
            filtered.forEach { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { onOpen(item) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = GroupPaper),
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.title.ifBlank { "Конспект" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = GroupInk,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                item.groupSubjectName.ifBlank { item.subject.ifBlank { "Без предмета" } },
                                style = MaterialTheme.typography.bodySmall,
                                color = GroupMuted,
                            )
                            if (item.timeline.isNotEmpty()) {
                                Text(
                                    "Таймкоды: ${item.timeline.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GroupAccent,
                                )
                            }
                        }
                        if (item.canDelete) {
                            IconButton(onClick = { onDelete(item) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Удалить из группы",
                                    tint = Color(0xFFB91C1C),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SubjectsTab(
    subjects: List<GroupSubjectSummary>,
    isOwner: Boolean,
    isTeacher: Boolean,
    amTeacher: (GroupSubjectSummary) -> Boolean,
    assigningSubjectId: String,
    onAssignSelf: (GroupSubjectSummary) -> Unit,
    onInvite: (GroupSubjectSummary) -> Unit,
    onAddSubject: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            if (isOwner) {
                "Предметы группы. Назначьте себя преподавателем или пригласите коллегу."
            } else {
                "Предметы и преподаватели группы."
            },
            style = MaterialTheme.typography.bodySmall,
            color = GroupMuted,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        if (subjects.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = GroupPaper),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.School, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(34.dp))
                    Text(
                        "Пока нет предметов",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = GroupInk,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        if (isOwner) "Добавьте предмет и назначьте преподавателя." else "Владелец ещё не добавил предметы.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GroupMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (isOwner) {
                        Button(
                            onClick = onAddSubject,
                            modifier = Modifier.padding(top = 14.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GroupAccent),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Добавить предмет")
                        }
                    }
                }
            }
        } else {
            subjects.forEach { subject ->
                val mine = amTeacher(subject)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = GroupPaper),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                subject.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = GroupInk,
                                modifier = Modifier.weight(1f),
                            )
                            if (mine) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF047857), modifier = Modifier.size(15.dp))
                                    Text(
                                        "Вы преподаватель",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF047857),
                                    )
                                }
                            }
                        }
                        val names = subject.teachers.mapNotNull { it.name.takeIf { name -> name.isNotBlank() } }
                        Text(
                            if (names.isEmpty()) "Преподаватель не назначен" else names.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (names.isEmpty()) Color(0xFFB45309) else GroupMuted,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                        if (isOwner) {
                            Row(
                                modifier = Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (!mine) {
                                    TextButton(
                                        enabled = assigningSubjectId.isBlank(),
                                        onClick = { onAssignSelf(subject) },
                                    ) {
                                        Text(
                                            if (assigningSubjectId == subject.id) "Назначаем…" else "Назначить себя",
                                        )
                                    }
                                }
                                if (subject.canAddTeacher) {
                                    TextButton(onClick = { onInvite(subject) }) {
                                        Text("Пригласить преподавателя")
                                    }
                                } else if (!mine) {
                                    Text(
                                        "Лимит преподавателей",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GroupMuted,
                                        modifier = Modifier.padding(start = 4.dp, top = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (isOwner) {
                Button(
                    onClick = onAddSubject,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GroupAccent),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Добавить предмет")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun roleLabel(role: String): String = when (role) {
    "owner" -> "Владелец"
    "teacher" -> "Преподаватель"
    else -> "Участник"
}

private fun groupMembersLabel(count: Int): String = when {
    count <= 0 -> "пока никого"
    count == 1 -> "1 участник"
    count in 2..4 -> "$count участника"
    else -> "$count участников"
}
