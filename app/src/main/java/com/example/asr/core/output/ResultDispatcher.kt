package com.example.asr.core.output

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.asr.core.error.DomainError
import com.example.asr.feature_overlay.OverlayService
import com.example.asr.feature_overlay.TransparentActivity
import com.example.asr.core.utils.Logger

/**
 * Диспетчер результатов распознавания речи
 * Отвечает за отправку результатов пользователю через overlay или fallback активити
 */
class ResultDispatcher(
    private val context: Context,
    private val logger: Logger = Logger
) {
    
    companion object {
        private const val TAG = "ResultDispatcher"
        
        // Константы для передачи данных
        const val EXTRA_RECOGNITION_RESULT = "com.example.asr.EXTRA_RECOGNITION_RESULT"
        const val EXTRA_ERROR_MESSAGE = "com.example.asr.EXTRA_ERROR_MESSAGE"
        const val EXTRA_IS_SUCCESS = "com.example.asr.EXTRA_IS_SUCCESS"
    }
    
    /**
     * Отправляет результат распознавания в overlay сервис
     * Если overlay недоступен, использует fallback TransparentActivity
     * @param result текст распознавания или null если ошибка
     * @param error ошибка при распознавании или null если успех
     */
    fun dispatchResult(result: String?, error: DomainError?) {
        try {
            // Проверяем наличие разрешений
            if (!checkOverlayPermission()) {
                logger.logWarning(TAG, "No permission to draw overlay")
                sendToFallbackActivity(result, error)
                return
            }
            
            // Проверяем доступность и работоспособность сервиса
            if (isOverlayServiceAvailable() && isOverlayServiceRunning()) {
                sendToOverlayService(result, error)
            } else {
                logger.logInfo(TAG, "Overlay service not available or running, using fallback")
                sendToFallbackActivity(result, error)
            }
        } catch (e: Exception) {
            logger.logError(TAG, "Failed to dispatch result", e)
            // В случае ошибки отправляем через fallback
            sendToFallbackActivity(result, error)
        }
    }
    
    /**
     * Отправляет результат в OverlayService
     * @param result текст распознавания или null если ошибка
     * @param error ошибка при распознавании или null если успех
     */
    private fun sendToOverlayService(result: String?, error: DomainError?) {
        try {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = OverlayService.ACTION_UPDATE_RESULT
                putExtra(EXTRA_RECOGNITION_RESULT, result)
                putExtra(EXTRA_ERROR_MESSAGE, error?.message)
                putExtra(EXTRA_IS_SUCCESS, error == null)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Для Android 8.0+ используем foreground service
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
            
            logger.logInfo(TAG, "Result sent to OverlayService")
        } catch (e: Exception) {
            logger.logError(TAG, "Failed to send result to OverlayService", e)
            // Если не удалось отправить в overlay, используем fallback
            sendToFallbackActivity(result, error)
        }
    }
    
    /**
     * Отправляет результат через TransparentActivity (fallback)
     * @param result текст распознавания или null если ошибка
     * @param error ошибка при распознавании или null если успех
     */
    private fun sendToFallbackActivity(result: String?, error: DomainError?) {
        try {
            val intent = Intent(context, TransparentActivity::class.java).apply {
                action = TransparentActivity.ACTION_SHOW_RESULT
                putExtra(EXTRA_RECOGNITION_RESULT, result)
                putExtra(EXTRA_ERROR_MESSAGE, error?.message)
                putExtra(EXTRA_IS_SUCCESS, error == null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            
            context.startActivity(intent)
            logger.logInfo(TAG, "Result sent to TransparentActivity")
        } catch (e: Exception) {
            logger.logError(TAG, "Failed to send result to TransparentActivity", e)
            // Если и fallback не работает, логгируем ошибку
            logger.logError(TAG, "All dispatch methods failed")
        }
    }
    
    /**
     * Проверяет, доступен ли OverlayService для отправки данных
     * @return true если сервис доступен, false в противном случае
     */
    private fun isOverlayServiceAvailable(): Boolean {
        try {
            val intent = Intent(context, OverlayService::class.java)
            val serviceInfo = context.packageManager.resolveService(
                intent,
                0
            )
            return serviceInfo != null
        } catch (e: Exception) {
            logger.logError(TAG, "Failed to check overlay service availability", e)
            return false
        }
    }
    
    /**
     * Проверяет, запущен ли OverlayService в данный момент
     * @return true если сервис запущен, false в противном случае
     */
    private fun isOverlayServiceRunning(): Boolean {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningServices = activityManager.getRunningServices(Int.MAX_VALUE)
            
            return runningServices.any { service ->
                service.service.className == OverlayService::class.java.name
            }
        } catch (e: Exception) {
            logger.logError(TAG, "Failed to check if service is running", e)
            return false
        }
    }
    
    /**
     * Проверяет наличие разрешения на отображение overlay
     * @return true если разрешение есть, false в противном случае
     */
    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            // Для старых версий разрешение не требуется
            true
        }
    }
}
