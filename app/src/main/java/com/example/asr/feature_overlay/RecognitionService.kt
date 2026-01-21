package com.example.asr.feature_overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
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

/**
 * Сервис для обработки аудио файлов через пайплайн распознавания
 * Запускается из ShareReceiverActivity при получении аудио через Share
 */
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
        // Создаем уведомление один раз
        notification = createNotification()
        Logger.i(TAG, "RecognitionService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        Logger.i(TAG, "RecognitionService started with intent: ${intent?.action}")
        
        // Проверяем действие
        if (intent?.action != ACTION_START_RECOGNITION) {
            Logger.w(TAG, "Invalid action received: ${intent?.action}")
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Проверяем contentResolver
        if (contentResolver == null) {
            Logger.e(TAG, "ContentResolver is null")
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Получаем URI аудио из интента
        val audioUriString = intent.getStringExtra(EXTRA_AUDIO_URI)
        val audioUri = audioUriString?.let { Uri.parse(it) }
        
        if (audioUri == null) {
            Logger.e(TAG, "Audio URI is null")
            stopSelf()
            return START_NOT_STICKY
        }
        
        Logger.i(TAG, "Processing audio from URI: $audioUri")
        
        // Отменяем предыдущую задачу если есть и она активна
        cancelCurrentJob()
        
        // Запускаем обработку аудио в фоновом потоке
        currentJob = scope.launch {
            try {
                // Создаем уведомление для foreground service
                notification?.let { 
                    startForeground(NOTIFICATION_ID, it)
                } ?: run {
                    Logger.e(TAG, "Notification is null")
                    stopSelf()
                    return@launch
                }
                
                // Проверяем существование файла по URI
                if (!isUriAccessible(audioUri)) {
                    Logger.e(TAG, "Audio file is not accessible: $audioUri")
                    OverlayService.updateResultText(this@RecognitionService, "Файл аудио недоступен")
                    return@launch
                }
                
                // Открываем InputStream и обрабатываем аудио
                val inputStream = contentResolver.openInputStream(audioUri)
                if (inputStream == null) {
                    Logger.e(TAG, "Failed to open input stream for URI: $audioUri")
                    OverlayService.updateResultText(this@RecognitionService, "Не удалось открыть аудиофайл")
                    return@launch
                }
                
                processAudioStream(inputStream)
            } catch (e: Exception) {
                Logger.e(TAG, "Error processing audio", e)
                Log.e(TAG, "Error processing audio", e)
                OverlayService.updateResultText(this@RecognitionService, "Ошибка обработки аудио")
            } finally {
                // Останавливаем сервис после завершения обработки
                stopSelf()
            }
        }
        
        return START_STICKY
    }
    
    /**
     * Проверяет доступность URI файла
     */
    private fun isUriAccessible(uri: Uri): Boolean {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            inputStream?.close()
            true
        } catch (e: Exception) {
            Logger.e(TAG, "URI not accessible: $uri", e)
            false
        }
    }
    
    /**
     * Отменяет текущую задачу если она активна
     */
    private fun cancelCurrentJob() {
        if (currentJob?.isActive == true) {
            currentJob?.cancel()
            Logger.i(TAG, "Previous job cancelled")
        }
        currentJob = null
    }
    
    /**
     * Обработка аудио потока через пайплайн
     */
    private suspend fun processAudioStream(inputStream: InputStream) {
        try {
            Logger.i(TAG, "Starting audio processing pipeline")
            
            // Создаем пайплайн и координатор (можно улучшить с помощью DI)
            val pipeline = RecognitionPipeline()
            val coordinator = RecognitionCoordinator()
            
            // Запускаем пайплайн обработки с повторными попытками
            val result = executeWithRetry(pipeline, coordinator, inputStream)
            
            Logger.i(TAG, "Audio processing completed successfully")
            
            // Отображаем результат в оверлее
            if (result.isNullOrEmpty()) {
                Logger.w(TAG, "Empty recognition result received")
                OverlayService.updateResultText(this@RecognitionService, "Не удалось распознать речь")
            } else {
                Logger.i(TAG, "Displaying result in overlay: $result")
                OverlayService.updateResultText(this@RecognitionService, result)
            }
            
            // Запускаем оверлей для отображения результата
            OverlayService.startService(this@RecognitionService)
            
        } catch (e: Exception) {
            Logger.e(TAG, "Error in recognition pipeline", e)
            Log.e(TAG, "Error in recognition pipeline", e)
            throw e
        }
    }
    
    /**
     * Выполняет обработку с повторными попытками
     */
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
                    // Ожидание перед повторной попыткой с обработкой прерывания
                    try {
                        delay(1000 * attempt)
                    } catch (e: CancellationException) {
                        Logger.i(TAG, "Retry delayed cancelled")
                        throw e
                    }
                }
            }
        }
        
        Logger.e(TAG, "All $maxRetries attempts failed", lastException)
        throw lastException!!
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    /**
     * Создание канала уведомлений для foreground service
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recognition Service",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Service for audio recognition"
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Создание уведомления для foreground service
     */
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

