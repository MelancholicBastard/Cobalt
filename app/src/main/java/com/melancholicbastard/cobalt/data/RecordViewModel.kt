package com.melancholicbastard.cobalt.data

import android.app.Application
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.google.android.material.snackbar.Snackbar
import com.melancholicbastard.cobalt.db.VoiceNote
import com.melancholicbastard.cobalt.db.VoiceNoteDB
import com.melancholicbastard.cobalt.db.VoiceNoteRepository
import com.melancholicbastard.cobalt.network.WebSocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException

class RecordViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecordViewModel::class.java))
            return RecordViewModel(application) as T
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class RecordViewModel(application: Application) : BasePlaybackViewModel(application) {
    // Получаем контекст через application
    private val audioRecorder: AudioRecorder = AudioRecorder(application.applicationContext)
    private var audioFile: File? = null
    private var _textFromAudioFile = MutableStateFlow("")
    val textFromAudioFile: StateFlow<String> = _textFromAudioFile

    // Состояния UI
    var recordingState by mutableStateOf(RecordingState.IDLE)
        private set
    var elapsedTime by mutableLongStateOf(0L)
        private set
    var permissionNeeded = mutableStateOf(false)
        private set

    private var timerJob: Job? = null
    private var startTime = 0L             // Начальное время для таймера
    private var accumulatedTime: Long = 0L // Накопленное время при паузах

    // Репозиторий
    private val repository: VoiceNoteRepository = VoiceNoteRepository(
        VoiceNoteDB.getDB(application).voiceNoteDAO()
    )
    private val _recordSavedEvent = MutableSharedFlow<Unit>()
    val recordSavedEvent = _recordSavedEvent.asSharedFlow()

    val sharedPreferences = SharedPreferencesHelper(application)
    val networkChecker = NetworkChecker(application)

    init {
        VoskModelManager.initialize(application)
    }

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
                return@launch
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
            val wavFile = try {
                audioRecorder.stopRecording()
            } catch (e: IOException) {
                Log.e("AudioRecorder", "Ошибка при обработке файла", e)
                null
            } ?: run {
                withContext(Dispatchers.Main) {
                    recordingState = RecordingState.IDLE
                    stopTimer()
                }
                return@launch
            }

            try {
                val useServer = sharedPreferences.useServerModel
                val hasInternet = networkChecker.isInternetAvailable()

                // 1. Распознавание текста
                val text = withTimeoutOrNull(90_000) {
                    try {
                        when {
                            useServer && hasInternet -> {
                                // Отправка на сервер
                                WebSocketManager.sendAudio(wavFile)
                            }
                            else -> {
                                // Локальное распознавание
                                VoskModelManager.recognizeAudio(wavFile)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("RecordViewModel", "Ошибка распознавания", e)
                        null
                    }
                }?: "Превышено время ожидания"

                // 2. Конвертация в AAC/MP4
                val mp4File = audioRecorder.convertToAac(wavFile)

                withContext(Dispatchers.Main) {
                    Log.d("Text", "$text")
                    _textFromAudioFile.value = text ?: "Ошибка распознавания"

                    audioFile = mp4File

                    (playbackDuration as MutableStateFlow).value = getAudioDurationFrom(audioFile!!)

                    recordingState = RecordingState.STOPPED
                    stopTimer()
                }
            } catch (e: Exception) {
                Log.e("RecordViewModel", "Ошибка обработки", e)
                withContext(Dispatchers.Main) {
                    _textFromAudioFile.value = "Ошибка: ${e.localizedMessage}"
                    recordingState = RecordingState.STOPPED
                    stopTimer()
                }
            } finally {
                try {
                    WebSocketManager.close()
                    wavFile.delete()
                } catch (e: Exception) {
                    Log.e("RecordViewModel", "Ошибка при очистке", e)
                }
            }
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
        if (isPlaying.value) {
            pausePlayback()
        } else {
            playRecording()
        }
    }

    override fun playRecording() {
        audioFile?.let { file ->
            setDataSource(file)
            startPlayback()
        }
    }

    // Новый метод для явного запуска воспроизведения
    private fun startPlayback() {
        mediaPlayer?.let { player ->
            if (!player.isPlaying) {
                player.start()
                isPlaying.value = true
                startPlaybackProgressTracking()
            }
        }
    }

    fun updateTranscribedText(newText: String) {
        _textFromAudioFile.value = newText
    }

    fun saveToDatabase(title: String) {
        val audioFile = this.audioFile ?: return // Проверяем, что аудиофайл существует

        val note = VoiceNote(
            id = System.currentTimeMillis(),
            title = title,
            dateCreated = System.currentTimeMillis(),
            audioPath = audioFile.absolutePath,
            transcript = textFromAudioFile.value
        )

        viewModelScope.launch(Dispatchers.IO) {
            repository.insert(note)
            _recordSavedEvent.emit(Unit)
            stopPlayback()
        }
        Log.d("RecordViewModel", "Сохраняю запись: $title, путь: ${audioFile.absolutePath}")
    }

    fun clearRecordingData() {
        audioFile = null
        _textFromAudioFile.value = ""
        recordingState = RecordingState.IDLE
        elapsedTime = 0L
        accumulatedTime = 0L
        mediaPlayer?.release()
    }

    fun snackbar() {
        Toast.makeText(application, "Используются локальные мощности", Toast.LENGTH_SHORT).show()
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