package com.melancholicbastard.cobalt.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.FileInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

object VoskModelManager {
    private const val MODEL_PATH = "vosk-model-small-ru-0.22"
    private var model: Model? = null
    private var modelDir: File? = null

    @Synchronized
    fun initialize(context: Context) {
        if (model != null) return

        val modelsDir = context.getDir("models", Context.MODE_PRIVATE)
        modelDir = File(modelsDir, MODEL_PATH).apply {
            // Копируем только при первом запуске или повреждении модели
            if (!exists() || !isModelValid()) {
                deleteRecursively()
                mkdirs()
                context.extractModelAssets(MODEL_PATH, this)
            }
        }

        model = Model(modelDir!!.absolutePath)
    }

    private fun File.isModelValid(): Boolean {
        val requiredFiles = listOf(
            "am/final.mdl",
            "graph/HCLr.fst",
            "$MODEL_PATH.ini"
        )
        return requiredFiles.all { File(this, it).exists() }
    }

    @Synchronized
    fun recognizeAudio(file: File): String? {
        if (!isWavValidForVosk(file)){
            Log.e("Vosk", "Invalid WAV file format")
            return null
        }

        return try {
            model?.let { loadedModel ->
                Recognizer(loadedModel, 16000.0f).use { recognizer ->
                    processAudioFile(file, recognizer)
                }
            }
        } catch (e: Exception) {
            Log.e("Vosk", "Recognition error: ${e.stackTraceToString()}")
            null
        }
    }

    private fun processAudioFile(file: File, recognizer: Recognizer): String {
        FileInputStream(file).use { input ->
            input.skip(44)
            val buffer = ByteArray(4096)
            var bytesRead: Int

            while (input.read(buffer).also { bytesRead = it } != -1) {
                if (bytesRead > 0) recognizer.acceptWaveForm(buffer, bytesRead)
            }
        }
        return JSONObject(recognizer.finalResult)
            .optString("text", "")
            .trim()
            .takeIf { it.isNotEmpty() } ?: ""
    }

    @Synchronized
    fun release() {
        model?.close()
        model = null
    }

    private fun Context.extractModelAssets(assetDir: String, targetDir: File) {
        targetDir.deleteRecursively()
        targetDir.mkdirs()

        val queue = ArrayDeque<Pair<String, File>>().apply {
            add(assetDir to targetDir)
        }

        while (queue.isNotEmpty()) {
            val (currentAssetPath, currentTargetDir) = queue.removeFirst()

            assets.list(currentAssetPath)?.forEach { assetName ->
                val assetPath = "$currentAssetPath/$assetName"
                val targetFile = File(currentTargetDir, assetName)

                if (assetName.contains('.')) {
                    // Копируем файл
                    assets.open(assetPath).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    // Обрабатываем поддиректорию
                    targetFile.mkdirs()
                    queue.add(assetPath to targetFile)
                }
            } ?: throw IOException("Asset directory $currentAssetPath not found")
        }
    }

    private fun isWavValidForVosk(file: File): Boolean {
        if (!file.exists() || file.length() < 44) return false

        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(44)
                input.read(header)

                val riff = String(header, 0, 4)
                val wave = String(header, 8, 4)
                val audioFormat = ByteBuffer.wrap(header, 20, 2).short.toInt()
                val numChannels = ByteBuffer.wrap(header, 22, 2).short.toInt()
                val sampleRate = ByteBuffer.wrap(header, 24, 4).int
                val bitsPerSample = ByteBuffer.wrap(header, 34, 2).short.toInt()

                riff == "RIFF" && wave == "WAVE" &&
                        audioFormat == 1 && numChannels == 1 &&
                        sampleRate == 16000 && bitsPerSample == 16
            }
        } catch (e: Exception) {
            false
        }.also { isValid ->
            Log.d("Vosk", "WAV valid: $isValid")
        }
    }
}

