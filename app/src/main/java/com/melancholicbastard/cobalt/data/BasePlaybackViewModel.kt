package com.melancholicbastard.cobalt.data

import android.app.Application
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

abstract class BasePlaybackViewModel(application: Application) : AndroidViewModel(application) {

    // MediaPlayer и состояния воспроизведения
    protected var mediaPlayer: MediaPlayer? = null

    protected var playbackJob: Job? = null
        private set

    private var isPrepared = false

    // Состояния для UI
    val isPlaying = mutableStateOf(false)
    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition

    val _playbackDuration = MutableStateFlow(0L)
    val playbackDuration: StateFlow<Long> = _playbackDuration

    // Абстрактный метод playRecording(), должен быть реализован в дочерних ViewModel
    abstract fun playRecording()

    // Методы управления воспроизведением
    fun pausePlayback() {
        Log.d("BasePlaybackViewModel", "pausePlayback()")
        mediaPlayer?.pause()
        isPlaying.value = false
        playbackJob?.cancel()
    }

    fun stopPlayback() {
        Log.d("BasePlaybackViewModel", "stopPlayback()")
        mediaPlayer?.apply {
            pause()
            stop()
            release()
        }
        mediaPlayer = null
        isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
        _playbackPosition.value = 0L
        isPrepared = false
        Log.d("BasePlaybackViewModel", "В stopPlayback isPrepared == $isPrepared")
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
        Log.d("BasePlaybackViewModel", "seekTo($positionMs)")
        _playbackPosition.value = positionMs
    }

    fun fastForward() {
        mediaPlayer?.let { player ->
            val newPosition = (player.currentPosition + 5000).coerceAtMost(player.duration)
            Log.d("BasePlaybackViewModel", "fastForward → $newPosition")
            seekTo(newPosition.toLong())
        }
    }

    fun fastBackward() {
        mediaPlayer?.let { player ->
            val newPosition = (player.currentPosition - 5000).coerceAtLeast(0)
            Log.d("BasePlaybackViewModel", "fastBackward → $newPosition")
            seekTo(newPosition.toLong())
        }
    }

    // Блокировка для thread-safe операций
    private val lock = Any()

    fun setDataSource(file: File) {
        Log.d("BasePlaybackViewModel", "setDataSource вызван для ${file.name}")
        if (isPrepared && mediaPlayer != null)
        {
            Log.d("BasePlaybackViewModel", "mediaPlayer подготовлен ${isPrepared}")
            return // Уже подготовлен
        }
        stopPlayback() // Остановить предыдущий плеер
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                prepare() // Только подготовка, без start()
                isPrepared = true
                _playbackDuration.value = duration.toLong()
                _playbackPosition.value = 0L
                setOnCompletionListener {
                    _playbackPosition.value = duration.toLong()
                    this@BasePlaybackViewModel.isPlaying.value = false
                }
            } catch (e: IOException) {
                Log.e("BasePlaybackViewModel", "Ошибка инициализации плеера", e)
                release()
                mediaPlayer = null
                isPrepared = false
            }
        }
    }

    // Вспомогательные методы
    protected fun startPlaybackProgressTracking() {
        Log.d("BasePlaybackViewModel", "startPlaybackProgressTracking()")
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (mediaPlayer?.isPlaying == true) {
                (playbackPosition as MutableStateFlow).value = mediaPlayer!!.currentPosition.toLong() ?: 0L
                delay(500)
            }
            isPlaying.value = false
        }
    }

    protected fun getAudioDurationFrom(file: File): Long {
        return try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(file.absolutePath)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        } catch (e: Exception) {
            Log.e("BasePlaybackViewModel", "Ошибка получения длительности: ${e.message}")
            0L
        }
    }
    override fun onCleared() {
        stopPlayback()
        super.onCleared()
    }
}
