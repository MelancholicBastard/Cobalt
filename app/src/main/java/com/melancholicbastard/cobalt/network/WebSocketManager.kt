package com.melancholicbastard.cobalt.network

import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object WebSocketManager {
    private const val TAG = "WebSocketManager"
    private var webSocket: WebSocket? = null

    private val client = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS) // Без таймаута на чтение
        .pingInterval(0, TimeUnit.SECONDS)
        .build()


    suspend fun sendAudio(file: File): String? = suspendCancellableCoroutine { continuation ->
        try {
            val request = Request.Builder()
                .url("ws://192.168.3.15:2700")
                .build()

            client.newWebSocket(request, object : WebSocketListener() {
                val resultText = StringBuilder()
                var isFinal = false

                override fun onOpen(ws: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket connected")

                    try {
                        // 1. Отправка аудиоданных
                        // Отправляем аудио как BINARY сообщения
                        file.inputStream().use { stream ->
                            // Пропускаем 44-байтовый заголовок WAV
                            if (file.extension.equals("wav", ignoreCase = true)) {
                                stream.skip(44)
                            }

                            val buffer = ByteArray(6400)
                            var bytesRead: Int
                            while (stream.read(buffer).also { bytesRead = it } != -1) {
                                ws.send(ByteString.of(*buffer.copyOf(bytesRead)))
                            }
                        }
                        // 2. EOF тоже как бинарное сообщение
                        ws.send("""{"eof":1}""")
                        Log.d(TAG, "Audio and EOF sent")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending audio", e)
                        continuation.resumeWithException(e)
                    }
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    Log.d(TAG, "Received message: $text")
                    try {
                        val json = JSONObject(text)
                        when {
                            json.has("text") -> {
                                resultText.append(json.getString("text"))
                                if (json.optBoolean("final", false) || resultText.isNotEmpty()) {
                                    isFinal = true
                                    continuation.resume(resultText.toString())
                                    ws.close(1000, "Done")
                                }
                            }

                            json.has("partial") -> {
                                Log.d(TAG, "Partial: ${json.getString("partial")}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка парсинга", e)
                    }
                }
                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    if (!isFinal && !continuation.isCompleted) {
                    continuation.resume(null)
                } }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket error", t)
                    if (!continuation.isCompleted) {
                        continuation.resumeWithException(t)
                    }
                }
            }).also { webSocket = it }
            continuation.invokeOnCancellation {
                webSocket?.close(1000, "Cancelled")
            }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }


    fun close() {
        webSocket?.close(1000, null)
    }
}