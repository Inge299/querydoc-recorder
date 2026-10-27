package com.querydoc.lecturerecorder

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.view.WindowCompat
import com.querydoc.lecturerecorder.data.remote.ApiClient
import com.querydoc.lecturerecorder.ui.LoginScreen
import com.querydoc.lecturerecorder.ui.RecorderScreen
import com.querydoc.lecturerecorder.ui.RecorderViewModel
import com.querydoc.lecturerecorder.work.CleanupScheduler
import com.querydoc.lecturerecorder.work.UploadScheduler
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: RecorderViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        viewModel.onPermissionResult(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )

        setContent {
            val api = remember { ApiClient(applicationContext) }
            val scope = rememberCoroutineScope()
            var loggedIn by remember { mutableStateOf(api.hasSession()) }
            LaunchedEffect(loggedIn) {
                if (loggedIn) {
                    UploadScheduler.enqueue(applicationContext)
                    CleanupScheduler.enqueue(applicationContext)
                }
            }
            MaterialTheme {
                Surface {
                    if (loggedIn) {
                        RecorderScreen(
                            state = viewModel.state,
                            onStartRecording = viewModel::startRecording,
                            onToggleFarLecturerMode = viewModel::toggleFarLecturerMode,
                            onSetPauseOnInterruptions = viewModel::setPauseOnInterruptions,
                            onSetEnvironmentProfile = viewModel::setEnvironmentProfile,
                            onLogout = { scope.launch { api.logout(); loggedIn = false } },
                            onPauseOrResumeRecording = viewModel::pauseOrResumeRecording,
                            onStopRecording = viewModel::stopRecording,
                            onPlayRecording = viewModel::playRecordingById,
                            onPausePlaybackForItem = viewModel::pausePlaybackForItem,
                            onStopPlaybackForItem = viewModel::stopPlaybackForItem,
                            onSeekRecording = viewModel::seekPlaybackPosition,
                            onDeleteRecordings = viewModel::deleteRecordings,
                            onBrightnessRequested = ::applyScreenBrightness
                        )
                    } else {
                        LoginScreen(api = api, onLoggedIn = { loggedIn = true })
                    }
                }
            }
        }
    }

    private fun applyScreenBrightness(brightness: Float?) {
        val layoutParams = window.attributes
        layoutParams.screenBrightness = brightness ?: android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = layoutParams
    }
}
