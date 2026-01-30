package com.example.asr.feature_overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.asr.R
import com.example.asr.core.coordinator.RecognitionCoordinator
import com.example.asr.core.pipeline.RecognitionPipeline
import com.example.asr.core.utils.Logger
import kotlinx.coroutines.*
import java.io.InputStream

class RecognitionService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "RecognitionServiceChannel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_AUDIO_URI = "com.example.asr.EXTRA_AUDIO_URI"
        const val ACTION_START_RECOGNITION = "com.example.asr.ACTION_START_RECOGNITION"
        private const val TAG = "RecognitionService"
        private var currentJob: Job? = null
        private var notification: Notification? = null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notification = createNotification()
        Logger.i(TAG, "RecognitionService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        Logger.i(TAG, "RecognitionService started with intent: ${intent?.action}")

        if (intent?.action != ACTION_START_RECOGNITION) {
            Logger.w(TAG, "Invalid action received: ${intent?.action}", fallbackException)
            stopSelf()
            return START_NOT_STICKY
        }

        val audioUriString = intent.getStringExtra(EXTRA_AUDIO_URI)
        val audioUri = audioUriString?.let { Uri.parse(it) }

        if (audioUri == null) {
            Logger.e(TAG, "Audio URI is null")
            sendResultToOverlay("Не удалось получить аудиофайл")
            stopSelf()
            return START_NOT_STICKY
        }

        cancelCurrentJob()

        currentJob = scope.launch {
            try {
                notification?.let {
                    startForeground(NOTIFICATION_ID, it)
                } ?: run {
                    Logger.e(TAG, "Notification is null")
                    stopSelf()
                    return@launch
                }

                if (!isUriAccessible(audioUri)) {
                    Logger.e(TAG, "Audio file is not accessible: $audioUri")
                    sendResultToOverlay("Файл аудио недоступен")
                    return@launch
                }

                val inputStream = contentResolver.openInputStream(audioUri)
                if (inputStream == null) {
                    Logger.e(TAG, "Failed to open input stream for URI: $audioUri")
                    sendResultToOverlay("Не удалось открыть аудиофайл")
                    return@launch
                }

                processAudioStream(inputStream)
            } catch (e: Exception) {
                Logger.e(TAG, "Error processing audio", e)
                sendResultToOverlay("Ошибка обработки аудио")
            } finally {
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun isUriAccessible(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.close()
            true
        } catch (e: Exception) {
            Logger.e(TAG, "URI not accessible: $uri", e)
            false
        }
    }

    private fun cancelCurrentJob() {
        if (currentJob?.isActive == true) {
            currentJob?.cancel()
            Logger.i(TAG, "Previous job cancelled")
        }
        currentJob = null
    }

    private suspend fun processAudioStream(inputStream: InputStream) {
        try {
            Logger.i(TAG, "Starting audio processing pipeline")

            val pipeline = RecognitionPipeline()
            val coordinator = RecognitionCoordinator()
            val result = executeWithRetry(pipeline, coordinator, inputStream)

            Logger.i(TAG, "Audio processing completed successfully")

            if (result.isNullOrEmpty()) {
                Logger.w(TAG, "Empty recognition result", fallbackException)
                sendResultToOverlay("Не удалось распознать речь")
            } else {
                Logger.i(TAG, "Displaying result in overlay: $result")
                sendResultToOverlay(result)
            }

            // Запускаем оверлей
            OverlayService.startService(this@RecognitionService)
        } catch (e: Exception) {
            Logger.e(TAG, "Error in recognition pipeline", e)
            sendResultToOverlay("Ошибка обработки аудио")
        }
    }

    private suspend fun executeWithRetry(
        pipeline: RecognitionPipeline,
        coordinator: RecognitionCoordinator,
        inputStream: InputStream
    ): String? {
        val maxRetries = 3
        var lastException: Exception? = null

        for (attempt in 1..maxRetries) {
            try {
                Logger.i(TAG, "Processing attempt $attempt/$maxRetries")
                return coordinator.execute(pipeline, inputStream)
            } catch (e: Exception) {
                lastException = e
                Logger.w(TAG, "Attempt $attempt failed", e)
                if (attempt < maxRetries) {
                    try {
                        delay(1000L * attempt)
                    } catch (e: CancellationException) {
                        Logger.i(TAG, "Retry delay cancelled")
                        throw e
                    }
                }
            }
        }

        Logger.e(TAG, "All $maxRetries attempts failed", lastException)
        throw lastException!!
    }

    private fun sendResultToOverlay(result: String, isFinal: Boolean = true) {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_UPDATE_RESULT
            putExtra(OverlayService.EXTRA_RESULT_TEXT, result)
            putExtra(OverlayService.EXTRA_IS_FINAL, isFinal)
        }
        startService(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recognition Service",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Service for audio recognition"
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Распознавание аудио")
            .setContentText("Обработка аудиофайла...")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.i(TAG, "RecognitionService destroyed")
        scope.cancel()
        cancelCurrentJob()
    }
}
