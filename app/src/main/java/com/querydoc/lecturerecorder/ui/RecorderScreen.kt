package com.querydoc.lecturerecorder.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.querydoc.lecturerecorder.config.AppConfig
import com.querydoc.lecturerecorder.data.local.UploadStatus
import com.querydoc.lecturerecorder.recording.EnvironmentProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

@Composable
fun RecorderScreen(
    state: StateFlow<RecorderUiState>,
    onStartRecording: () -> Unit,
    onToggleFarLecturerMode: () -> Unit,
    onSetPauseOnInterruptions: (Boolean) -> Unit,
    onSetEnvironmentProfile: (EnvironmentProfile) -> Unit,
    onSetRecognitionLanguage: (RecognitionLanguage) -> Unit,
    onLogout: () -> Unit,
    onPauseOrResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPlayRecording: (String, Float?) -> Unit,
    onPausePlaybackForItem: (String) -> Unit,
    onStopPlaybackForItem: (String) -> Unit,
    onSeekRecording: (String, Float) -> Unit,
    onDeleteRecordings: (Set<String>) -> Unit,
    onBrightnessRequested: (Float?) -> Unit,
    onRequestConspect: (String) -> Unit,
    onDismissConspect: () -> Unit,
    onOpenConspects: () -> Unit
) {
    val uiState by state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var focusedItemId by remember { mutableStateOf<String?>(null) }
    var lingeringItemId by remember { mutableStateOf<String?>(null) }
    var lastPlayingItemId by remember { mutableStateOf<String?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var settingsExpanded by remember { mutableStateOf(false) }
    val selectedMap = remember { mutableStateMapOf<String, Boolean>() }
    val seekByItem = remember { mutableStateMapOf<String, Float>() }
    val selectedIds: Set<String> = selectedMap.filterValues { it }.keys
    val rootTapInteraction = remember { MutableInteractionSource() }

    LaunchedEffect(uiState.isRecording, uiState.energySaverEnabled) {
        if (uiState.isRecording && uiState.energySaverEnabled) {
            onBrightnessRequested(AppConfig.Power.recordingScreenBrightness)
        } else {
            onBrightnessRequested(null)
        }
    }

    LaunchedEffect(uiState.recordings.firstOrNull()?.id) {
        val newestId = uiState.recordings.firstOrNull()?.id ?: return@LaunchedEffect
        focusedItemId = newestId
        listState.animateScrollToItem(0)
    }

    LaunchedEffect(uiState.recordings) {
        val validIds = uiState.recordings.map { it.id }.toSet()
        selectedMap.keys.filter { it !in validIds }.forEach { selectedMap.remove(it) }
        if (selectedMap.none { it.value }) selectionMode = false
    }

    LaunchedEffect(uiState.isPlayingLocalAudio, uiState.currentPlaybackItemId) {
        if (uiState.isPlayingLocalAudio) {
            lastPlayingItemId = uiState.currentPlaybackItemId
            return@LaunchedEffect
        }
        val finishedId = lastPlayingItemId ?: return@LaunchedEffect
        val finishedNow = uiState.currentPlaybackItemId == finishedId &&
            uiState.playbackDurationMs > 0L &&
            uiState.playbackPositionMs >= (uiState.playbackDurationMs - 800L)
        if (finishedNow) {
            lingeringItemId = finishedId
            focusedItemId = null
            delay(1600)
            if (lingeringItemId == finishedId) lingeringItemId = null
        }
    }

    val sectionShape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6FB))
            .clickable(
                interactionSource = rootTapInteraction,
                indication = null,
                enabled = !selectionMode
            ) {
                focusedItemId = null
                lingeringItemId = null
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = sectionShape,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .background(Brush.linearGradient(colors = listOf(Color(0xFF0B1320), Color(0xFF1F3B5A))))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = when {
                                uiState.isRecordingPaused -> "Запись на паузе"
                                uiState.isRecording -> "Идет запись"
                                else -> "Готово к записи"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )
                        Text(
                            text = buildString {
                                append(formatDuration(uiState.elapsedSeconds))
                                if (uiState.pendingUploads > 0) {
                                    append("  ·  загрузка: ")
                                    append(uiState.pendingUploads)
                                }
                            },
                            color = Color(0xFFDDEAFD),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box {
                        IconButton(
                            modifier = Modifier.size(32.dp),
                            onClick = { settingsExpanded = true }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Настройки",
                                tint = Color(0xFFDDEAFD),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = settingsExpanded,
                            onDismissRequest = { settingsExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Режим записи", style = MaterialTheme.typography.bodySmall) },
                                onClick = {}
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        (if (!uiState.farLecturerMode) "✓ " else "") + "Обычный",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                },
                                onClick = {
                                    settingsExpanded = false
                                    if (uiState.farLecturerMode) onToggleFarLecturerMode()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        (if (uiState.farLecturerMode) "✓ " else "") + "Дальний лектор",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                },
                                onClick = {
                                    settingsExpanded = false
                                    if (!uiState.farLecturerMode) onToggleFarLecturerMode()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Профиль микрофона", style = MaterialTheme.typography.bodySmall) },
                                onClick = {}
                            )
                            DropdownMenuItem(
                                text = { Text("Прерывания", style = MaterialTheme.typography.bodySmall) },
                                onClick = {}
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        (if (uiState.pauseOnInterruptions) "✓ " else "") + "Автопауза при звонках",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                },
                                onClick = {
                                    settingsExpanded = false
                                    onSetPauseOnInterruptions(true)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        (if (!uiState.pauseOnInterruptions) "✓ " else "") + "Игнорировать звонки",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                },
                                onClick = {
                                    settingsExpanded = false
                                    onSetPauseOnInterruptions(false)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        (if (uiState.environmentProfile == EnvironmentProfile.QUIET) "✓ " else "") +
                                            EnvironmentProfile.QUIET.labelRu(),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                },
                                onClick = {
                                    settingsExpanded = false
                                    onSetEnvironmentProfile(EnvironmentProfile.QUIET)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        (if (uiState.environmentProfile == EnvironmentProfile.NORMAL) "✓ " else "") +
                                            EnvironmentProfile.NORMAL.labelRu(),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                },
                                onClick = {
                                    settingsExpanded = false
                                    onSetEnvironmentProfile(EnvironmentProfile.NORMAL)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        (if (uiState.environmentProfile == EnvironmentProfile.NOISY) "✓ " else "") +
                                            EnvironmentProfile.NOISY.labelRu(),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                },
                                onClick = {
                                    settingsExpanded = false
                                    onSetEnvironmentProfile(EnvironmentProfile.NOISY)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Язык распознавания", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4338CA)) },
                                onClick = {}
                            )
                            RecognitionLanguage.entries.forEach { language ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            (if (uiState.recognitionLanguage == language) "✓ " else "") + language.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(start = 16.dp)
                                        )
                                    },
                                    onClick = {
                                        settingsExpanded = false
                                        onSetRecognitionLanguage(language)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Выйти из аккаунта", style = MaterialTheme.typography.bodySmall) },
                                onClick = {
                                    settingsExpanded = false
                                    onLogout()
                                }
                            )
                        }
                    }
                }
            }
        }

        Card(shape = sectionShape, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val meterColor = levelColor(uiState.soundLevel)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Уровень микрофона",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF475569),
                    )
                    Text(
                        text = levelWord(uiState.soundLevel),
                        style = MaterialTheme.typography.labelSmall,
                        color = meterColor,
                    )
                }
                LevelBar(
                    label = "",
                    level = uiState.soundLevel,
                    average = uiState.averageSoundLevel,
                    color = meterColor
                )
            }
        }

        Card(shape = sectionShape, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlButton(
                    icon = Icons.Filled.FiberManualRecord,
                    label = "Запись",
                    enabled = !uiState.isRecording,
                    accent = Color(0xFFEF4444),
                    primary = true,
                    onClick = onStartRecording,
                )
                ControlButton(
                    icon = if (uiState.isRecordingPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    label = if (uiState.isRecordingPaused) "Продолжить" else "Пауза",
                    enabled = uiState.isRecording,
                    accent = Color(0xFF334155),
                    onClick = onPauseOrResumeRecording,
                )
                ControlButton(
                    icon = Icons.Filled.Stop,
                    label = "Стоп",
                    enabled = uiState.isRecording,
                    accent = Color(0xFF334155),
                    onClick = onStopRecording,
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenConspects),
            shape = sectionShape,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            Box(
                modifier = Modifier.background(
                    Brush.linearGradient(listOf(Color(0xFFEEF2FF), Color(0xFFE0E7FF))),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF4338CA), Color(0xFF7C6AE8))),
                                RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.MenuBook, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Конспекты",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1E1B4B),
                        )
                        Text(
                            text = "Готовые лекции в одном месте",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B),
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Открыть конспекты", tint = Color(0xFF4338CA))
                }
            }
        }

        Card(shape = sectionShape, modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "История", style = MaterialTheme.typography.bodySmall)
                    if (selectionMode) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconChip(Icons.Filled.Delete, "Удалить текущую", enabled = uiState.currentPlaybackItemId != null) {
                                uiState.currentPlaybackItemId?.let { onDeleteRecordings(setOf(it)) }
                            }
                            IconChip(Icons.Filled.DoneAll, "Выбрать все", enabled = uiState.recordings.isNotEmpty()) {
                                uiState.recordings.forEach { selectedMap[it.id] = true }
                            }
                            IconChip(Icons.Filled.DeleteSweep, "Удалить выбранные", enabled = selectedIds.isNotEmpty()) {
                                onDeleteRecordings(selectedIds)
                                selectedIds.forEach {
                                    selectedMap.remove(it)
                                    seekByItem.remove(it)
                                    if (focusedItemId == it) focusedItemId = null
                                    if (lingeringItemId == it) lingeringItemId = null
                                }
                                if (selectedMap.none { it.value }) selectionMode = false
                            }
                            IconChip(Icons.Filled.Close, "Отмена", enabled = true) {
                                selectionMode = false
                                selectedMap.clear()
                            }
                        }
                    }
                }

                if (uiState.recordings.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Пока нет записей")
                    }
                } else {
                    val listTapInteraction = remember { MutableInteractionSource() }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = listTapInteraction,
                                indication = null,
                                enabled = !selectionMode
                            ) {
                                focusedItemId = null
                                lingeringItemId = null
                            },
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(uiState.recordings, key = { it.id }) { item ->
                            val isCurrent = item.id == uiState.currentPlaybackItemId
                            val isFocused = isCurrent || focusedItemId == item.id || lingeringItemId == item.id
                            val progress = if (isCurrent) {
                                val dur = uiState.playbackDurationMs.coerceAtLeast(1L)
                                (uiState.playbackPositionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                            } else {
                                seekByItem[item.id] ?: 0f
                            }
                            RecordingItemRow(
                                item = item,
                                isCurrent = isCurrent,
                                isFocused = isFocused,
                                isSelected = selectedMap[item.id] == true,
                                isSelectionMode = selectionMode,
                                isRecordingNow = uiState.isRecording,
                                isPlayingNow = isCurrent && uiState.isPlayingLocalAudio,
                                isPausedNow = isCurrent && uiState.isPlaybackPaused,
                                progress = progress,
                                playbackPositionMs = if (isCurrent) uiState.playbackPositionMs else (item.durationMs * progress).toLong(),
                                playbackDurationMs = if (isCurrent) uiState.playbackDurationMs else item.durationMs,
                                onFocus = {
                                    focusedItemId = item.id
                                    lingeringItemId = null
                                },
                                onEnterSelection = {
                                    selectionMode = true
                                    selectedMap[item.id] = !(selectedMap[item.id] ?: false)
                                },
                                onToggleSelection = { selectedMap[item.id] = !(selectedMap[item.id] ?: false) },
                                onPlay = { id, start -> onPlayRecording(id, start) },
                                onPause = onPausePlaybackForItem,
                                onStop = onStopPlaybackForItem,
                                onSeek = { id, value ->
                                    seekByItem[id] = value
                                    onSeekRecording(id, value)
                                },
                                onRequestConspect = onRequestConspect
                            )
                        }
                    }
                }
            }
        }
    }

    uiState.conspectDialog?.let { conspect ->
        AlertDialog(
            onDismissRequest = onDismissConspect,
            title = { Text(if (conspect.status == "completed") "Конспект" else "Конспект не готов") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (conspect.status == "completed") {
                        ConspectMarkdown(conspect.markdown.ifBlank { "Конспект пуст." })
                    } else {
                        Text(
                            text = conspect.error.ifBlank { "Не удалось подготовить конспект." },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissConspect) { Text("Закрыть") }
            },
        )
    }
}

@Composable
private fun IconChip(icon: ImageVector, contentDescription: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(modifier = Modifier.size(28.dp), onClick = onClick, enabled = enabled) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) Color(0xFF0F172A) else Color(0xFF94A3B8),
            modifier = Modifier.size(16.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingItemRow(
    item: RecordingListItem,
    isCurrent: Boolean,
    isFocused: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isRecordingNow: Boolean,
    isPlayingNow: Boolean,
    isPausedNow: Boolean,
    progress: Float,
    playbackPositionMs: Long,
    playbackDurationMs: Long,
    onFocus: () -> Unit,
    onEnterSelection: () -> Unit,
    onToggleSelection: () -> Unit,
    onPlay: (String, Float?) -> Unit,
    onPause: (String) -> Unit,
    onStop: (String) -> Unit,
    onSeek: (String, Float) -> Unit,
    onRequestConspect: (String) -> Unit
) {
    val safeDuration = playbackDurationMs.coerceAtLeast(1L)
    var sliderState by remember(progress, safeDuration) { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelection() else onFocus() },
                onLongClick = onEnterSelection
            ),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFDDEAFD) else Color(0xFFF8FAFC)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatusIcon(item.status)
                Text(
                    text = item.createdAtLabel,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Text(
                    text = if (isCurrent) {
                        "${formatDurationMs(playbackPositionMs)}/${formatDurationMs(safeDuration)}"
                    } else {
                        formatDurationMs(safeDuration)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF334155)
                )
            }
            if (isFocused) {
                if (isRecordingNow) {
                    Text(
                        text = "Воспроизведение заблокировано во время записи",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(modifier = Modifier.size(26.dp), onClick = {
                            if (isPlayingNow) {
                                onStop(item.id)
                            } else if (isPausedNow) {
                                onPlay(item.id, null)
                            } else {
                                onPlay(item.id, sliderState)
                            }
                        }) {
                            Icon(
                                imageVector = if (isPlayingNow) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlayingNow) "Стоп" else "Воспроизвести",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            modifier = Modifier.size(26.dp),
                            onClick = { onPause(item.id) },
                            enabled = isCurrent && (isPlayingNow || isPausedNow)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = "Пауза",
                                tint = if (isCurrent && (isPlayingNow || isPausedNow)) Color(0xFF0F172A) else Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (item.status == UploadStatus.UPLOADED) {
                            IconButton(
                                modifier = Modifier.size(26.dp),
                                onClick = { onRequestConspect(item.id) }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Description,
                                    contentDescription = "Сделать конспект",
                                    tint = Color(0xFF4338CA),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Slider(
                            modifier = Modifier
                                .weight(1f)
                                .height(20.dp),
                            value = sliderState,
                            onValueChange = { sliderState = it },
                            onValueChangeFinished = { onSeek(item.id, sliderState) },
                            valueRange = 0f..1f
                        )
                    }
                }
            }
        }
    }
}

private fun levelWord(level: Float): String = when {
    level < 0.02f -> "Тишина"
    level < 0.2f -> "Тихо"
    level < 0.75f -> "Хороший уровень"
    else -> "Громко"
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    accent: Color,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val diameter = if (primary) 58.dp else 48.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(diameter)
                .background(
                    if (enabled) accent.copy(alpha = if (primary) 0.14f else 0.08f) else Color(0xFFE2E8F0),
                    CircleShape,
                ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (!enabled) Color(0xFF94A3B8) else if (primary) accent else accent,
                modifier = Modifier.size(if (primary) 28.dp else 22.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) Color(0xFF334155) else Color(0xFF94A3B8),
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusIcon(status: UploadStatus) {
    val (icon, tint, cd) = when (status) {
        UploadStatus.PENDING -> Triple(Icons.Filled.Schedule, Color(0xFF94A3B8), "Ожидает")
        UploadStatus.UPLOADING -> Triple(Icons.Filled.CloudUpload, Color(0xFF0284C7), "Загружается")
        UploadStatus.UPLOADED -> Triple(Icons.Filled.CheckCircle, Color(0xFF16A34A), "Загружено")
        UploadStatus.FAILED -> Triple(Icons.Filled.ErrorOutline, Color(0xFFDC2626), "Ошибка")
    }
    Icon(imageVector = icon, contentDescription = cd, tint = tint, modifier = Modifier.size(14.dp))
}

@Composable
private fun LevelBar(label: String, level: Float, average: Float, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = Color(0xFF475569))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .background(Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(level.coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(color, RoundedCornerShape(8.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(average.coerceIn(0f, 1f))
                    .height(8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(8.dp)
                        .background(Color(0xFF0F172A), RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

private fun levelColor(level: Float): Color {
    return when {
        level < 0.2f -> Color(0xFFF59E0B)
        level < 0.75f -> Color(0xFF22C55E)
        else -> Color(0xFFEF4444)
    }
}

private fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val h = safe / 3600
    val m = (safe % 3600) / 60
    val s = safe % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
