package com.melancholicbastard.cobalt.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
//import com.arthenica.mobileffmpeg.FFmpeg


class AudioRecorder(private val context: Context) {
    // Хранит текущий экземпляр AudioRecord для записи аудио
    private var audioRecord: AudioRecord? = null
    // Файл, в который идет запись в данный момент
    private var outputFile: File? = null
    private var isRecording = false
    // Список временных файлов для хранения фрагментов на устройствах с API < 24 (до Android 7)
    private var pausedFiles = mutableListOf<File>()
    // Конфигурация аудио для Vosk
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    // Для потока записи
    private var recordingThread: Thread? = null

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

        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            audioFormat
        ) * 2

        val newFile = File.createTempFile("audio_", ".wav", context.cacheDir)
        outputFile = newFile

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        isRecording = true
        audioRecord?.startRecording()

        recordingThread = Thread {
            writeWavHeader(newFile) // Записываем заголовок WAV
            val buffer = ByteArray(bufferSize)
            val fos = FileOutputStream(newFile, true)

            while (isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                if (bytesRead > 0) {
                    fos.write(buffer, 0, bytesRead)
                }
            }

            fos.close()
        }.apply { start() }

        return newFile
    }

    /**
     * Приостанавливает запись
     * Для Android N+ использует встроенную функцию pause(),
     * для старых версий сохраняет текущий файл и останавливает запись
     */
    fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            isRecording = false
        } else {
            stopInternal()
            outputFile?.let { pausedFiles.add(it) }
            outputFile = null
        }
    }

    /**
     * Возобновляет приостановленную запись
     * @return файл, в который продолжается запись
     * @throws IllegalStateException если запись не была приостановлена
     */
    @Throws(IllegalStateException::class)
    fun resumeRecording(): File {
        if (outputFile == null) {
            throw IllegalStateException("No paused recording to resume")
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Для API 24+ просто продолжаем запись в тот же файл
            isRecording = true
            audioRecord?.apply {
                // Создаем новый поток записи
                startRecordingThread()
            } ?: throw IllegalStateException("AudioRecord not initialized")
            outputFile!!
        } else {
            // Для старых версий начинаем новую запись
            startRecording()
        }
    }

    private fun startRecordingThread() {
        recordingThread?.interrupt()
        recordingThread = Thread {
            val fos = FileOutputStream(outputFile, true) // Открываем файл в режиме дописывания

            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                channelConfig,
                audioFormat
            )
            val buffer = ByteArray(bufferSize)

            while (isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                if (bytesRead > 0) {
                    fos.write(buffer, 0, bytesRead)
                }
            }

            fos.close()
        }.apply { start() }
    }

    /**
     * Записывает WAV-заголовок в указанный файл
     *
     * @param file Файл для записи заголовка
     * @param sampleRate Частота дискретизации (по умолчанию 16000 Гц)
     * @param channels Количество каналов (1 - моно, 2 - стерео)
     * @param bitsPerSample Битность (16 бит для PCM)
     */
    private fun writeWavHeader(
        file: File,
        sampleRate: Int = 16000,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        RandomAccessFile(file, "rw").use { raf ->
            // RIFF header
            raf.write("RIFF".toByteArray())          // Chunk ID
            raf.writeInt(0)                          // Chunk size (пока 0, обновится позже)
            raf.write("WAVE".toByteArray())          // Format

            // Format sub-chunk
            raf.write("fmt ".toByteArray())          // Subchunk ID
            raf.writeInt(16)                         // Subchunk size (16 для PCM)
            raf.writeShort(1)                        // Audio format (1 = PCM)
            raf.writeShort(channels)       // Channels
            raf.writeInt(sampleRate)                 // Sample rate
            raf.writeInt(byteRate)                   // Byte rate
            raf.writeShort(blockAlign)     // Block align
            raf.writeShort(bitsPerSample)  // Bits per sample

            // Data sub-chunk
            raf.write("data".toByteArray())          // Subchunk ID
            raf.writeInt(0)                          // Data size (пока 0)
        }
    }

    /**
     * Останавливает запись и объединяет фрагменты для старых устройств
     * @return итоговый файл с полной записью
     */
    fun stopRecording(): ProcessedAudio? {
        stopInternal()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N || pausedFiles.isEmpty()) {
            // Новые Android или нет пауз - обрабатываем текущий WAV
            outputFile?.let { wavFile ->
                try {
                    // 1. Распознавание текста
                    val text = VoskModelManager.recognizeAudio(wavFile) ?: "Ошибка распознавания"
                    Log.d("Text", text)
                    // 2. Конвертация в AAC/MP4
                    val mp4File = convertToAac(wavFile)
                    // 3. Удаление временного WAV
                    wavFile.delete()

                    ProcessedAudio(mp4File, text)
                } catch (e: Exception) {
                    Log.e("AudioRecorder", "Ошибка при обработке файла", e)
                    null
                }
            }
        } else {
            // Старые Android с паузами - особый случай
            processLegacyRecording()
        }
    }

    private fun stopInternal() {
        isRecording = false
        recordingThread?.join(2000)
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        // Обновляем заголовок только если файл существует и содержит данные
        outputFile?.let { file ->
            if (file.exists() && file.length() >= 44) {
                try {
                    updateWavHeader(file)
                } catch (e: IOException) {
                    Log.e("AudioRecorder", "Не удалось обновить заголовок: ${e.message}")
                    file.delete()
                    outputFile = null
                }
            } else {
                Log.w("AudioRecorder", "Файл слишком маленький для WAV-заголовка, удаляем: ${file.absolutePath}")
                file.delete()
                outputFile = null
            }
        }
    }

    /**
     * Обновляет заголовок WAV-файла, устанавливая корректные размеры данных.
     *
     * @param file WAV-файл для обновления
     * @throws IOException если файл не существует или не является валидным WAV-файлом
     */
    @Throws(IOException::class)
    private fun updateWavHeader(file: File) {
        if (!file.exists() || file.length() < 44) {
            throw IOException("Invalid WAV file: ${file.absolutePath}")
        }

        val fileSize = file.length()
        val dataSize = fileSize - 44  // 44 байта - размер стандартного WAV-заголовка

        RandomAccessFile(file, "rw").use { raf ->
            // 1. Обновляем общий размер файла (ChunkSize) в позиции 4
            // Формат: RIFF[4] + ChunkSize[4] + WAVE[4] + ...
            raf.seek(4)
            raf.writeInt((fileSize - 8).toInt())  // 8 = RIFF(4) + ChunkSize(4)

            // 2. Обновляем размер аудиоданных (Subchunk2Size) в позиции 40
            // Формат: ... + data[4] + Subchunk2Size[4] + данные
            raf.seek(40)
            raf.writeInt(dataSize.toInt())
        }
    }

    @Throws(IOException::class)
    private fun convertToAac(wavFile: File): File {
        if (!wavFile.exists()) {
            throw IOException("WAV файл не найден: ${wavFile.absolutePath}")
        }

        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: context.filesDir
        val outputFile = File(outputDir, "converted_${System.currentTimeMillis()}.mp4")

        if (!outputFile.exists()) {
            outputFile.createNewFile()
        }

//        val outputFile = File.createTempFile("converted_", ".mp4", context.cacheDir)
        val inputStream = FileInputStream(wavFile).apply { skip(44) } // Пропускаем заголовок WAV
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // Настройка формата AAC
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 16000, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }

        val inputBuffers = codec.inputBuffers
        val outputBuffers = codec.outputBuffers
        val bufferInfo = MediaCodec.BufferInfo()
        var trackAdded = false
        var presentationTimeUs = 0L

        try {
            val chunkSize = 1024
            val pcmBuffer = ByteArray(chunkSize)
            var isEndOfStream = false
            var totalBytesRead = 0L

            while (!isEndOfStream) {
                val inputBufferIndex = codec.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = inputBuffers[inputBufferIndex]
                    inputBuffer.clear()

                    val bytesRead = inputStream.read(pcmBuffer)
                    if (bytesRead == -1) {
                        // Завершаем ввод
                        codec.queueInputBuffer(
                            inputBufferIndex,
                            0, 0,
                            presentationTimeUs,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        isEndOfStream = true
                    } else {
                        inputBuffer.put(pcmBuffer, 0, bytesRead)
                        codec.queueInputBuffer(
                            inputBufferIndex,
                            0, bytesRead,
                            presentationTimeUs,
                            0
                        )
                        // Обновляем время в микросекундах
                        // 16-битный PCM, моно → 2 байта на семпл, 16000 семплов в секунде
                        presentationTimeUs += (bytesRead / 2 * 1000000L / 16000)
                        totalBytesRead += bytesRead
                    }
                }

                // Обрабатываем выходные буферы
                while (true) {
                    val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                    if (outputBufferIndex < 0) break

                    if (!trackAdded) {
                        muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        trackAdded = true
                    }

                    val outputBuffer = outputBuffers[outputBufferIndex]
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                    val outData = ByteArray(bufferInfo.size).apply {
                        outputBuffer.get(this)
                    }

                    if (bufferInfo.size > 0) {
                        muxer.writeSampleData(0, ByteBuffer.wrap(outData), bufferInfo)
                    }

                    codec.releaseOutputBuffer(outputBufferIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }

            // После окончания ввода — обрабатываем оставшиеся выходные буферы
            while (true) {
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferIndex < 0) break

                if (!trackAdded) {
                    muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    trackAdded = true
                }

                val outputBuffer = outputBuffers[outputBufferIndex]
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                val outData = ByteArray(bufferInfo.size).apply {
                    outputBuffer.get(this)
                }

                if (bufferInfo.size > 0) {
                    muxer.writeSampleData(0, ByteBuffer.wrap(outData), bufferInfo)
                }

                codec.releaseOutputBuffer(outputBufferIndex, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }

            // Завершаем работу
            muxer.stop()
            muxer.release()
            inputStream.close()
            codec.stop()
            codec.release()

            return outputFile

        } catch (e: Exception) {
            // Очистка при ошибке
            muxer.stop()
            muxer.release()
            inputStream.close()
            codec.stop()
            codec.release()
            outputFile.delete()
            throw IOException("Ошибка кодирования в AAC: ${e.message}")
        }
    }

    private fun processLegacyRecording(): ProcessedAudio {
        // 1. Объединяем WAV-фрагменты
        val mergedWav = mergeWavFiles(pausedFiles + outputFile!!)

        // 2. Распознаем текст из объединенного WAV
        val text = VoskModelManager.recognizeAudio(mergedWav) ?: "Ошибка распознавания"

        // 3. Конвертируем в AAC/MP4
        val mp4File = convertToAac(mergedWav)

        // 4. Очистка
        pausedFiles.forEach { it.delete() }
        mergedWav.delete()
        pausedFiles.clear()

        return ProcessedAudio(mp4File, text)
    }

    /**
     * Объединяет несколько WAV-аудиофайлов в один
     * @param files список файлов для объединения
     * @return итоговой файл с объединенной записью
     */
    private fun mergeWavFiles(files: List<File>): File {
        val mergedFile = File.createTempFile("merged_", ".wav", context.cacheDir)

        // Простое объединение RAW PCM данных
        FileOutputStream(mergedFile).use { out ->
            files.forEach { file ->
                FileInputStream(file).use { input ->
                    if (file != files.first()) {
                        input.skip(44) // Пропускаем заголовок у всех кроме первого
                    }
                    input.copyTo(out)
                }
            }
        }

        // Обновляем заголовок
        updateWavHeader(mergedFile)
        return mergedFile
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

    data class ProcessedAudio(
        val audioFile: File,
        val transcript: String
    )
}