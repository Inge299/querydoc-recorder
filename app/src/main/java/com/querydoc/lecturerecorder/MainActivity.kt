package com.querydoc.lecturerecorder

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import com.querydoc.lecturerecorder.data.remote.ApiClient
import com.querydoc.lecturerecorder.ui.ConspectReaderScreen
import com.querydoc.lecturerecorder.ui.ConspectsScreen
import com.querydoc.lecturerecorder.ui.IntroScreen
import com.querydoc.lecturerecorder.ui.LoginScreen
import com.querydoc.lecturerecorder.ui.RecorderScreen
import com.querydoc.lecturerecorder.ui.RecorderViewModel
import com.querydoc.lecturerecorder.ui.SettingsScreen
import com.querydoc.lecturerecorder.work.CleanupScheduler
import com.querydoc.lecturerecorder.work.ConspectCache
import com.querydoc.lecturerecorder.work.ConspectScheduler
import com.querydoc.lecturerecorder.work.UploadScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: RecorderViewModel by viewModels()
    private val pendingConspectSessionId = mutableStateOf<String?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        viewModel.onPermissionResult(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingConspectSessionId.value = intent?.getStringExtra(EXTRA_CONSPECT_SESSION_ID)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            val api = remember { ApiClient(applicationContext) }
            val recorderState by viewModel.state.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            var loggedIn by remember { mutableStateOf(api.hasSession()) }
            var introVisible by remember { mutableStateOf(true) }
            var permissionsRequested by remember { mutableStateOf(false) }
            var restoreAttempted by remember { mutableStateOf(false) }
            var showConspects by remember { mutableStateOf(false) }
            var showSettings by remember { mutableStateOf(false) }
            var readerSession by remember { mutableStateOf<String?>(null) }
            var readerMarkdown by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                delay(1600)
                introVisible = false
            }
            LaunchedEffect(introVisible, loggedIn) {
                if (!introVisible && !restoreAttempted) {
                    restoreAttempted = true
                    if (!loggedIn && api.restoreSession()) loggedIn = true
                }
                if (!introVisible && loggedIn) {
                    if (!viewModel.state.value.isRecording) {
                        NotificationManagerCompat.from(applicationContext).cancelAll()
                    }
                    if (pendingConspectSessionId.value.isNullOrBlank()) {
                        val latestConspect = ConspectScheduler.consumeLatestUnseen(applicationContext)
                        if (latestConspect != null) pendingConspectSessionId.value = latestConspect
                    }
                    UploadScheduler.enqueue(applicationContext)
                    CleanupScheduler.enqueue(applicationContext)
                    if (!permissionsRequested) {
                        permissionsRequested = true
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ),
                        )
                    }
                }
            }
            val requestedConspect = pendingConspectSessionId.value
            LaunchedEffect(requestedConspect, introVisible, loggedIn) {
                if (!introVisible && loggedIn && !requestedConspect.isNullOrBlank()) {
                    pendingConspectSessionId.value = null
                    readerMarkdown = ConspectCache.get(applicationContext, requestedConspect)
                    readerSession = requestedConspect
                }
            }
            val screen = when {
                introVisible -> "intro"
                !loggedIn -> "login"
                readerSession != null -> "reader"
                showSettings -> "settings"
                showConspects -> "conspects"
                else -> "recorder"
            }
            BackHandler(enabled = readerSession != null) {
                readerSession = null
                readerMarkdown = null
            }
            BackHandler(enabled = showSettings) {
                showSettings = false
            }
            BackHandler(enabled = readerSession == null && showConspects) {
                showConspects = false
            }
            LaunchedEffect(showSettings, showConspects, readerSession) {
                if (!showSettings && !showConspects && readerSession == null) {
                    viewModel.refreshRecordings()
                }
            }
            LaunchedEffect(screen) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = screen != "intro"
                controller.isAppearanceLightNavigationBars = false
            }
            MaterialTheme {
                Surface {
                    Crossfade(targetState = screen, animationSpec = tween(360), label = "querydoc-root") { target ->
                        when (target) {
                            "intro" -> IntroScreen()
                            "recorder" -> RecorderScreen(
                            state = viewModel.state,
                            onStartRecording = viewModel::startRecording,
                            onToggleFarLecturerMode = viewModel::toggleFarLecturerMode,
                            onSetPauseOnInterruptions = viewModel::setPauseOnInterruptions,
                            onSetEnvironmentProfile = viewModel::setEnvironmentProfile,
                            onSetRecognitionLanguage = viewModel::setRecognitionLanguage,
                            onLogout = { scope.launch { api.logout(); loggedIn = false } },
                            onPauseOrResumeRecording = viewModel::pauseOrResumeRecording,
                            onStopRecording = viewModel::stopRecording,
                            onPlayRecording = viewModel::playRecordingById,
                            onPausePlaybackForItem = viewModel::pausePlaybackForItem,
                            onStopPlaybackForItem = viewModel::stopPlaybackForItem,
                            onSeekRecording = viewModel::seekPlaybackPosition,
                            onDeleteRecordings = viewModel::deleteRecordings,
                            onBrightnessRequested = ::applyScreenBrightness,
                            onRequestConspect = viewModel::requestConspect,
                            onDismissConspect = viewModel::dismissConspect,
                            onOpenConspects = { showConspects = true },
                            onOpenSettings = { showSettings = true }
                        )
                            "settings" -> SettingsScreen(
                                farLecturerMode = recorderState.farLecturerMode,
                                environmentProfile = recorderState.environmentProfile,
                                pauseOnInterruptions = recorderState.pauseOnInterruptions,
                                recognitionLanguage = recorderState.recognitionLanguage,
                                onToggleFarLecturerMode = viewModel::toggleFarLecturerMode,
                                onSetPauseOnInterruptions = viewModel::setPauseOnInterruptions,
                                onSetEnvironmentProfile = viewModel::setEnvironmentProfile,
                                onSetRecognitionLanguage = viewModel::setRecognitionLanguage,
                                onLogout = {
                                    showSettings = false
                                    scope.launch { api.logout(); loggedIn = false }
                                },
                                onBack = { showSettings = false },
                            )
                            "conspects" -> ConspectsScreen(
                                api = api,
                                onBack = { showConspects = false },
                                onOpen = { summary ->
                                    readerMarkdown = summary.markdown
                                    readerSession = summary.sessionId
                                },
                            )
                            "reader" -> ConspectReaderScreen(
                                api = api,
                                sessionId = readerSession.orEmpty(),
                                initialMarkdown = readerMarkdown,
                                onBack = {
                                    readerSession = null
                                    readerMarkdown = null
                                },
                            )
                            else -> LoginScreen(api = api, onLoggedIn = { loggedIn = true })
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshRecordings()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingConspectSessionId.value = intent.getStringExtra(EXTRA_CONSPECT_SESSION_ID)
    }

    private fun applyScreenBrightness(brightness: Float?) {
        val layoutParams = window.attributes
        layoutParams.screenBrightness = brightness ?: android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = layoutParams
    }

    companion object {
        const val EXTRA_CONSPECT_SESSION_ID = "conspect_session_id"
    }
}
