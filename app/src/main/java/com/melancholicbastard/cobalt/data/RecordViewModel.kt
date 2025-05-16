package com.melancholicbastard.cobalt.data

import android.app.Application
import android.media.MediaPlayer
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class RecordViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RecordViewModel(application) as T
    }
}

class RecordViewModel(application: Application) : AndroidViewModel(application) {
    // Получаем контекст через application
    private val audioRecorder: AudioRecorder = AudioRecorder(application.applicationContext)
    private var audioFile: File? = null

    // Состояния UI
    var recordingState by mutableStateOf(RecordingState.IDLE)
        private set
    var elapsedTime by mutableStateOf(0L)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var permissionNeeded = mutableStateOf(false)
        private set

    private var timerJob: Job? = null
    private var startTime = 0L
    private var accumulatedTime: Long = 0L // Накопленное время при паузах

    fun startRecording() {
        viewModelScope.launch(Dispatchers.IO) {
            // Запоминаем, нужно ли нам запрашивать разрешение у пользователя
            try {
                if (!audioRecorder.hasAudioRecordPermission()) {
                    permissionNeeded.value = true
                    return@launch
                }
            } catch (e: SecurityException) {
                permissionNeeded.value = true
            }
            audioFile = audioRecorder.startRecording()
            recordingState = RecordingState.RECORDING
            startTimer()
        }
    }

    fun pauseRecording() {
        audioRecorder.pauseRecording()
        recordingState = RecordingState.PAUSED
        pauseTimer()
    }

    fun resumeRecording() {
        audioRecorder.resumeRecording()
        recordingState = RecordingState.RECORDING
        startTimer()
    }

    fun stopRecording() {
        audioFile = audioRecorder.stopRecording()
        recordingState = RecordingState.STOPPED
        stopTimer()
    }

    fun cancelRecording() {
        audioRecorder.cancelRecording()
        recordingState = RecordingState.IDLE
        elapsedTime = 0L
        stopTimer()
    }

    fun playRecording() {
        audioFile?.let { file ->
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                this@RecordViewModel.isPlaying = true
                setOnCompletionListener {
                    this@RecordViewModel.isPlaying = false
                }
            }
        }
    }

    private fun startTimer() {
        startTime = SystemClock.elapsedRealtime()
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                elapsedTime = accumulatedTime + SystemClock.elapsedRealtime() - startTime
                delay(16) // ~60 FPS обновлений
            }
        }
    }

    private fun pauseTimer() {
        accumulatedTime += SystemClock.elapsedRealtime() - startTime
        timerJob?.cancel()
        timerJob = null
    }

    private fun stopTimer() {
        accumulatedTime = 0L
        elapsedTime = 0L
        timerJob?.cancel()
        timerJob = null
    }

    enum class RecordingState {
        IDLE, RECORDING, PAUSED, STOPPED
    }
}