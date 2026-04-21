package com.querydoc.lecturerecorder

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.view.WindowCompat
import com.querydoc.lecturerecorder.ui.RecorderScreen
import com.querydoc.lecturerecorder.ui.RecorderViewModel

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
            MaterialTheme {
                Surface {
                    RecorderScreen(
                        state = viewModel.state,
                        onStartRecording = viewModel::startRecording,
                        onToggleFarLecturerMode = viewModel::toggleFarLecturerMode,
                        onSetPauseOnInterruptions = viewModel::setPauseOnInterruptions,
                        onSetEnvironmentProfile = viewModel::setEnvironmentProfile,
                        onAuthorizationRequested = viewModel::onAuthorizationRequested,
                        onPauseOrResumeRecording = viewModel::pauseOrResumeRecording,
                        onStopRecording = viewModel::stopRecording,
                        onPlayRecording = viewModel::playRecordingById,
                        onPausePlaybackForItem = viewModel::pausePlaybackForItem,
                        onStopPlaybackForItem = viewModel::stopPlaybackForItem,
                        onSeekRecording = viewModel::seekPlaybackPosition,
                        onDeleteRecordings = viewModel::deleteRecordings,
                        onBrightnessRequested = ::applyScreenBrightness
                    )
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
