package com.example.asr.feature_overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.asr.R
import com.example.asr.core.utils.Logger

/**
 * Сервис для отображения оверлея с результатами распознавания
 */
class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1001
        const val ACTION_SHOW_OVERLAY = "com.example.asr.ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.example.asr.ACTION_HIDE_OVERLAY"
        const val ACTION_UPDATE_RESULT = "com.example.asr.ACTION_UPDATE_RESULT"
        const val EXTRA_RESULT_TEXT = "com.example.asr.EXTRA_RESULT_TEXT"
        const val EXTRA_IS_FINAL = "com.example.asr.EXTRA_IS_FINAL"
        private const val AUTO_HIDE_DELAY_MS = 30000 // 30 секунд
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var resultTextView: TextView? = null
    private var copyButton: Button? = null
    private var handler: Handler? = null
    private var isOverlayShown = false
    private var autoHideRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "OverlayService created")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        handler = Handler()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> showOverlay()
            ACTION_HIDE_OVERLAY -> hideOverlay()
            ACTION_UPDATE_RESULT -> updateResult(intent)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Показывает оверлей с результатами распознавания
     */
    private fun showOverlay() {
        // Проверяем разрешение SYSTEM_ALERT_WINDOW
        if (!checkOverlayPermission()) {
            Logger.w(TAG, "Missing SYSTEM_ALERT_WINDOW permission")
            return
        }

        if (isOverlayShown) {
            Logger.w(TAG, "Overlay is already shown - updating existing overlay")
            // Если оверлей уже показан, обновляем его содержимое
            updateExistingOverlay()
            return
        }

        try {
            // Создаем view для оверлея
            val inflater = LayoutInflater.from(this)
            overlayView = inflater.inflate(R.layout.overlay_popup, null)

            resultTextView = overlayView?.findViewById(R.id.result_text_view)
            copyButton = overlayView?.findViewById(R.id.copy_button)

            // Настройка кнопки копирования
            copyButton?.setOnClickListener {
                overlayView?.let { view ->
                    val text = resultTextView?.text.toString()
                    copyToClipboard(text)
                }
            }

            // Настройка параметров оверлея
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                PixelFormat.TRANSLUCENT
            )

            params.gravity = Gravity.TOP or Gravity.START
            params.x = 100
            params.y = 100

            // Добавляем view в окно
            windowManager?.addView(overlayView, params)
            isOverlayShown = true
            Logger.i(TAG, "Overlay shown successfully")
            
            // Запускаем таймер автоматического скрытия
            startAutoHideTimer()
        } catch (e: Exception) {
            Logger.e(TAG, "Error showing overlay", e)
            isOverlayShown = false
        }
    }

    /**
     * Проверяет наличие разрешения SYSTEM_ALERT_WINDOW
     */
    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            // Для Android < 6.0 разрешение не требуется
            true
        }
    }

    /**
     * Обновляет существующий оверлей вместо его повторного создания
     */
    private fun updateExistingOverlay() {
        try {
            // Можно обновить текст или другие элементы оверлея
            Logger.i(TAG, "Updating existing overlay content")
            // Например: обновить содержимое TextView если нужно
            // В данном случае оверлей уже отображен, так что ничего не меняем
            // Но можно добавить логику обновления содержимого
        } catch (e: Exception) {
            Logger.e(TAG, "Error updating existing overlay", e)
        }
    }

    /**
     * Скрывает оверлей
     */
    private fun hideOverlay() {
        if (!isOverlayShown) {
            Logger.w(TAG, "Overlay is not shown")
            return
        }

        try {
            overlayView?.let { view ->
                windowManager?.removeView(view)
                overlayView = null
                resultTextView = null
                copyButton = null
                isOverlayShown = false
                Logger.i(TAG, "Overlay hidden successfully")
                
                // Останавливаем таймер автоматического скрытия
                stopAutoHideTimer()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error hiding overlay", e)
        }
    }

    /**
     * Обновляет текст результата в оверlays
     */
    private fun updateResult(intent: Intent) {
        val resultText = intent.getStringExtra(EXTRA_RESULT_TEXT) ?: return
        val isFinal = intent.getBooleanExtra(EXTRA_IS_FINAL, false)

        try {
            resultTextView?.let { textView ->
                // Обновляем текст с учетом финальности результата
                if (isFinal) {
                    textView.text = resultText
                } else {
                    // Для промежуточных результатов добавляем точку в конце
                    textView.text = "$resultText..."
                }
                
                // Перезапускаем таймер автоматического скрытия при обновлении результата
                restartAutoHideTimer()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error updating result text", e)
        }
    }

    /**
     * Копирует текст в буфер обмена
     */
    private fun copyToClipboard(text: String) {
        try {
            val clipboardManager = ContextCompat.getSystemService(this, android.content.ClipboardManager::class.java)
            if (clipboardManager != null) {
                val clipData = android.content.ClipData.newPlainText("speech_result", text)
                clipboardManager.setPrimaryClip(clipData)
                Logger.i(TAG, "Text copied to clipboard")
            } else {
                Logger.w(TAG, "Clipboard manager is null")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error copying to clipboard", e)
        }
    }

    /**
     * Проверяет, отображается ли оверлей
     */
    private fun isOverlayDisplayed(): Boolean {
        // Вместо windowManager.views используем флаг состояния
        return isOverlayShown
    }

    /**
     * Запускает таймер автоматического скрытия оверлея
     */
    private fun startAutoHideTimer() {
        stopAutoHideTimer()
        
        autoHideRunnable = Runnable {
            hideOverlay()
        }
        
        handler?.let { 
            it.postDelayed(autoHideRunnable!!, AUTO_HIDE_DELAY_MS)
        } ?: Logger.w(TAG, "Handler is null, cannot start auto hide timer")
    }

    /**
     * Останавливает таймер автоматического скрытия оверлея
     */
    private fun stopAutoHideTimer() {
        autoHideRunnable?.let { runnable ->
            handler?.removeCallbacks(runnable)
        }
        autoHideRunnable = null
    }

    /**
     * Перезапускает таймер автоматического скрытия оверлея
     */
    private fun restartAutoHideTimer() {
        startAutoHideTimer()
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.i(TAG, "OverlayService destroyed")
        
        // Очищаем ресурсы
        hideOverlay()
        handler?.removeCallbacksAndMessages(null)
        handler = null
        windowManager = null
    }
}
