package com.example.asr.core.output

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.example.asr.core.error.DomainError
import com.example.asr.core.utils.Logger
import com.example.asr.feature_overlay.OverlayService
import com.example.asr.feature_overlay.TransparentActivity

/**
 * Отправляет результаты распознавания в OverlayService или fallback TransparentActivity
 * Если overlay недоступен, используется прозрачное активити для отображения результата.
 */
class ResultDispatcher(
    private val context: Context,
    private val logger: Logger
) {
    
    companion object {
        const val EXTRA_RESULT_TEXT = "extra_result_text"
        const val EXTRA_RESULT_TIMESTAMP = "extra_result_timestamp"
        const val EXTRA_ERROR_MESSAGE = "extra_error_message"
        const val EXTRA_IS_FINAL = "extra_is_final"
    }
    
    /**
     * Отправляет результат распознавания в OverlayService.
     * Если OverlayService недоступен или не запущен, использует TransparentActivity как fallback.
     *
     * @param text распознанный текст
     * @param isFinal флаг окончательного результата (true = конечный результат)
     * @param error ошибка распознавания (если есть)
     */
    suspend fun dispatchResult(
        text: String,
        isFinal: Boolean = false,
        error: DomainError? = null
    ) {
        try {
            // Проверяем, доступен ли overlay
            if (isOverlayAvailable()) {
                sendToOverlay(text, isFinal, error)
            } else {
                logger.warn("Overlay not available, using fallback activity")
                sendToFallbackActivity(text, isFinal, error)
            }
        } catch (e: Exception) {
            logger.error("Failed to dispatch result", e)
            // В случае ошибки используем fallback
            try {
                sendToFallbackActivity(text, isFinal, error)
            } catch (fallbackException: Exception) {
                logger.error("Failed to send result to fallback activity", fallbackException)
            }
        }
    }
    
    /**
     * Проверяет, доступен ли overlay для отображения
     */
    private fun isOverlayAvailable(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        } catch (e: Exception) {
            logger.error("Error checking overlay availability", e)
            false
        }
    }
    
    /**
     * Отправляет результат в OverlayService через Intent
     */
    private fun sendToOverlay(
        text: String,
        isFinal: Boolean,
        error: DomainError?
    ) {
        val intent = Intent(context, OverlayService::class.java).apply {
            action = OverlayService.ACTION_UPDATE_RESULT
            putExtra(EXTRA_RESULT_TEXT, text)
            putExtra(EXTRA_IS_FINAL, isFinal)
            if (error != null) {
                putExtra(EXTRA_ERROR_MESSAGE, error.toString())
            }
        }

        try {
            // Используем startForegroundService для Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Проверяем, запущен ли сервис
                val isRunning = isOverlayServiceRunning()
                
                if (!isRunning) {
                    context.startForegroundService(intent)
                } else {
                    // Если сервис уже запущен, отправляем broadcast для обновления
                    context.sendBroadcast(intent)
                }
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            logger.error("Failed to send result to overlay service", e)
            // Не выбрасываем ошибку - продолжаем с fallback
        }
    }
    
    /**
     * Проверяет, запущен ли OverlayService
     */
    private fun isOverlayServiceRunning(): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.getRunningServices(Int.MAX_VALUE)
                .any { it.service.className == OverlayService::class.java.name }
        } catch (e: Exception) {
            logger.error("Error checking overlay service status", e)
            false
        }
    }
    
    /**
     * Отправляет результат через TransparentActivity как fallback
     */
    private fun sendToFallbackActivity(
        text: String,
        isFinal: Boolean,
        error: DomainError?
    ) {
        val intent = Intent(context, TransparentActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_RESULT_TEXT, text)
            putExtra(EXTRA_IS_FINAL, isFinal)
            if (error != null) {
                putExtra(EXTRA_ERROR_MESSAGE, error.toString())
            }
        }
        
        try {
            // Проверяем, существует ли активити перед запуском
            val resolveInfo = context.packageManager.resolveActivity(
                intent, 
                PackageManager.MATCH_DEFAULT_ONLY
            )
            
            if (resolveInfo != null) {
                context.startActivity(intent)
            } else {
                logger.warn("TransparentActivity not found")
            }
        } catch (e: Exception) {
            logger.error("Failed to start fallback activity", e)
            // Не выбрасываем ошибку - это не критично
        }
    }
}
