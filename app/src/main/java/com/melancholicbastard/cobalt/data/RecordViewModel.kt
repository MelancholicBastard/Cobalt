package com.melancholicbastard.cobalt.data

import android.app.Application
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

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
    private var _textFromAudioFile = MutableStateFlow("")
    val textFromAudioFile: StateFlow<String> = _textFromAudioFile

    // Состояния UI
    var recordingState by mutableStateOf(RecordingState.IDLE)
        private set
    var elapsedTime by mutableStateOf(0L)
        private set
    var permissionNeeded = mutableStateOf(false)
        private set

    private var timerJob: Job? = null
    private var startTime = 0L             // Начальное время для таймера
    private var accumulatedTime: Long = 0L // Накопленное время при паузах

    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null
    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition
    private val _playbackDuration = MutableStateFlow(0L)
    val playbackDuration: StateFlow<Long> = _playbackDuration
    private var _isPlaying = mutableStateOf(false)
    val isPlaying: MutableState<Boolean> = _isPlaying

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
        recordingState = RecordingState.LOADING
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                audioRecorder.stopRecording()
            } catch (e: IOException) {
                null
            }

            withContext(Dispatchers.Main) {
                if (result != null) {
                    audioFile = result.audioFile

                    _textFromAudioFile.value = result.transcript

                    _playbackDuration.value = getAudioDurationFrom(result.audioFile)

                    recordingState = RecordingState.STOPPED
                    stopTimer()
                } else {
                    recordingState = RecordingState.IDLE
                    stopTimer()
                }
            }
        }
    }

    private fun getAudioDurationFrom(file: File): Long {
        return try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(file.absolutePath)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        } catch (e: Exception) {
            Log.e("RecordViewModel", "Ошибка получения длительности: ${e.message}")
            0L
        }
    }

    fun cancelRecording() {
        recordingState = RecordingState.LOADING
        viewModelScope.launch(Dispatchers.IO) {
            audioRecorder.cancelRecording()
            withContext(Dispatchers.Main) {
                recordingState = RecordingState.IDLE
                elapsedTime = 0L
                stopTimer()
            }
        }
    }

    fun togglePlayback() {
        if (_isPlaying.value) {
            pausePlayback()
        } else {
            playRecording()
        }
    }

    private fun pausePlayback() {
        mediaPlayer?.pause()
        _isPlaying.value = false
        playbackJob?.cancel()
    }

    private fun playRecording() {
        audioFile?.let { file ->
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer().apply {
                    try {
                        setDataSource(file.absolutePath)
                        prepare()
                        start()

                        val duration = mediaPlayer?.duration?.toLong() ?: 0L
                        _playbackDuration.value = duration
                        _playbackPosition.value = 0L
                        setOnCompletionListener {
                            _playbackPosition.value = duration // Переводим позицию на конец
                            this@RecordViewModel._isPlaying.value = false
                        }
                    } catch (e: IOException) {
                        Log.e("RecordViewModel", "Ошибка подготовки медиаплеера: ${e.message}")
                        mediaPlayer?.release()
                        mediaPlayer = null
                    }
                }
            } else {
                if (!mediaPlayer!!.isPlaying) {
                    mediaPlayer!!.start()
                }
            }

            _isPlaying.value = true
        }
        // Запускаем обновление позиции
        startPlaybackProgressTracking()
    }

    private fun startPlaybackProgressTracking() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (isActive && mediaPlayer?.isPlaying == true) {
                _playbackPosition.value = mediaPlayer?.currentPosition?.toLong() ?: 0L
                delay(500)
            }
            // После окончания воспроизведения:
            _isPlaying.value = false
        }
    }

    fun stopPlayback() {
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
        isPlaying.value = false
        playbackJob?.cancel()
        _playbackPosition.value = 0L
    }

    fun fastForward() {
        mediaPlayer?.let {
            val newPosition = (it.currentPosition + 5000).coerceAtMost(it.duration)
            it.seekTo(newPosition)
            _playbackPosition.value = newPosition.toLong()
        }
    }

    fun fastBackward() {
        mediaPlayer?.let {
            val newPosition = (it.currentPosition - 5000).coerceAtLeast(0)
            it.seekTo(newPosition)
            _playbackPosition.value = newPosition.toLong()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
        _playbackPosition.value = positionMs
    }

    fun updateTranscribedText(newText: String) {
        _textFromAudioFile.value = newText
    }

    fun saveToDatabase(text: String) {
        // TODO: Реализовать сохранение в Room / Firebase / файлы
        Log.d("RecordViewModel", "Сохраняю в базу: $text")
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
        IDLE, RECORDING, PAUSED, STOPPED, LOADING
    }
}