package com.melancholicbastard.cobalt.data

import android.app.Application
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

abstract class BasePlaybackViewModel(application: Application) : AndroidViewModel(application) {

    // MediaPlayer и состояния воспроизведения
    protected var mediaPlayer: MediaPlayer? = null

    protected var playbackJob: Job? = null
        private set

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
            if (isPlaying) {
                pause()
                stop()
            }
            release()
        }
        mediaPlayer = null
        isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
        (playbackPosition as MutableStateFlow).value = 0L
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

    fun setDataSource(file: File) {
        Log.d("BasePlaybackViewModel", "setDataSource(${file.name})")
        if (mediaPlayer == null) {
            mediaPlayer?.apply {
                stop()
                release()
            }
            mediaPlayer = null
            isPlaying.value = false
            playbackJob?.cancel()

            mediaPlayer = MediaPlayer().apply {
                try {
                    setDataSource(file.absolutePath)
                    prepare()
                    start()
                    _playbackDuration.value = duration.toLong()
                    _playbackPosition.value = 0L
                    setOnCompletionListener {
                        _playbackPosition.value = duration.toLong()
                        this@BasePlaybackViewModel.isPlaying.value = false
                    }
                    this@BasePlaybackViewModel.isPlaying.value = true
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
        isPlaying.value = true
        startPlaybackProgressTracking()
    }

    // Вспомогательные методы
    protected fun startPlaybackProgressTracking() {
        Log.d("BasePlaybackViewModel", "startPlaybackProgressTracking()")
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
}
