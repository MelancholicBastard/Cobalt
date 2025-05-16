package com.melancholicbastard.cobalt.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

class AudioRecorder(private val context: Context) {
    // Хранит текущий экземпляр MediaRecorder для записи аудио
    private var mediaRecorder: MediaRecorder? = null
    // Файл, в который идет запись в данный момент
    private var outputFile: File? = null
    // Список временных файлов для хранения фрагментов на устройствах с API < 24 (до Android 7)
    private var pausedFiles = mutableListOf<File>()

    /**
     * Проверяет, есть ли у приложения разрешение на запись звука
     */
    fun hasAudioRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Начинает новую запись
     * @return файл, в который записывается аудио
     * @throws SecurityException если разрешение не получено
     */
    @Throws(SecurityException::class)
    fun startRecording(): File {
        if (!hasAudioRecordPermission()) {
            throw SecurityException("Audio recording permission not granted")
        }

        // Создаем временный файл для записи
        val newFile = File.createTempFile("audio_", ".mp4", context.cacheDir).apply {
            createNewFile()
        }

        outputFile = newFile
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)       // Источник - микрофон
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)  // Формат - MPEG-4
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)     // Кодек - AAC
            setOutputFile(newFile.absolutePath)                 // Путь к файлу
            setAudioEncodingBitRate(128000)                     // 128 kbps для лучшего качества
            prepare()
            start()
        }
        return newFile
    }

    /**
     * Приостанавливает запись
     * Для Android N+ использует встроенную функцию pause(),
     * для старых версий сохраняет текущий файл и останавливает запись
     */
    fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder?.pause()
        } else {
            stopRecording()
            outputFile?.let { pausedFiles.add(it) }
            outputFile = null
            mediaRecorder = null
        }
    }

    /**
     * Возобновляет запись
     * @return файл, в который продолжается запись
     */
    fun resumeRecording(): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder?.resume()
            outputFile!!
        } else {
            // Создаём новый файл для продолжения записи
            startRecording()
        }
    }

    /**
     * Останавливает запись и объединяет фрагменты для старых устройств
     * @return итоговый файл с полной записью
     */
    fun stopRecording(): File? {
        mediaRecorder?.apply {
            try {
                stop()
            } catch (e: IllegalStateException) {
                Log.e("AudioRecorder", "stop() failed", e)
            }
            release()
        }
        mediaRecorder = null

        // Для старых API объединяем все фрагменты
        if (pausedFiles.isNotEmpty() && outputFile != null) {
            pausedFiles.add(outputFile!!)
            outputFile = mergeAudioFiles(pausedFiles)
            pausedFiles.clear()
        }
        return outputFile
    }

    /**
     * Объединяет несколько аудиофайлов в один
     * @param files список файлов для объединения
     * @return итоговой файл с объединенной записью
     */
    private fun mergeAudioFiles(files: List<File>): File {if (files.isEmpty()) throw IllegalArgumentException("No files to merge")
        // Создаем временный файл для результата
        val mergedFile = File.createTempFile("merged_", ".mp4", context.cacheDir)
        // Инициализируем MediaMuxer для объединения файлов
        val muxer = MediaMuxer(mergedFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var audioTrackIndex = -1
        var sampleRate = -1
        var channelCount = -1
        var totalDuration = 0L
        val buffer = ByteBuffer.allocate(1024 * 1024) // Буфер 1MB для чтения данных

        try {
            // 1. Сначала проходим по всем файлам для проверки совместимости
            files.forEach { file ->
                MediaExtractor().apply {
                    setDataSource(file.absolutePath)
                    // Ищем аудиотрек в файле
                    (0 until trackCount).forEach { i ->
                        if (getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                            if (audioTrackIndex == -1) {
                                // Берем параметры первого файла как эталонные
                                val format = getTrackFormat(i)
                                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                audioTrackIndex = muxer.addTrack(format)
                                muxer.start()
                            }
                            release()
                            return@forEach
                        }
                    }
                    release()
                }
            }

            // 2. Обрабатываем каждый файл и объединяем их
            files.forEach { file ->
                MediaExtractor().apply {
                    setDataSource(file.absolutePath)

                    // Находим аудиотрек
                    val trackIndex = selectTrackWithMimeType("audio/")
                    if (trackIndex >= 0) {
                        val format = getTrackFormat(trackIndex)
                        selectTrack(trackIndex)

                        // Проверяем совместимость параметров
                        if (format.getInteger(MediaFormat.KEY_SAMPLE_RATE) != sampleRate ||
                            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) != channelCount) {
                            throw IOException("Incompatible audio formats")
                        }
                        val duration = format.getLong(MediaFormat.KEY_DURATION)
                        // Читаем и записываем данные
                        while (true) {
                            val bufferInfo = MediaCodec.BufferInfo()
                            val sampleSize = readSampleData(buffer, 0)
                            if (sampleSize < 0) break

                            // Устанавливаем временную метку
                            bufferInfo.presentationTimeUs = totalDuration + sampleTime
                            // Преобразуем флаги MediaExtractor в MediaCodec-совместимые
                            bufferInfo.flags = when {
                                (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ->
                                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                                else -> 0
                            }
                            bufferInfo.size = sampleSize

                            muxer.writeSampleData(audioTrackIndex, buffer, bufferInfo)
                            advance()
                        }

                        totalDuration += duration
                    }
                    release()
                }
            }
        } finally {
            // Освобождаем ресурсы
            muxer.stop()
            muxer.release()
        }

        return mergedFile
    }

    /**
     * Вспомогательный метод для поиска аудиотрека в файле
     */
    private fun MediaExtractor.selectTrackWithMimeType(mimePrefix: String): Int {
        for (i in 0 until trackCount) {
            val format = getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith(mimePrefix) == true) {
                return i
            }
        }
        return -1
    }

    /**
     * Отменяет текущую запись, удаляя все созданные файлы
     */
    fun cancelRecording() {
        stopRecording()
        outputFile?.delete()
        pausedFiles.forEach { it.delete() }
        outputFile = null
        pausedFiles.clear()
    }
}