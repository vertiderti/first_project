package com.example.asr.feature_overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.asr.R
import com.example.asr.core.utils.Logger

/**
 * Сервис для отображения оверлея поверх других приложений
 * Отображает результаты распознавания с возможностью копирования и закрытия
 */
class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        private const val AUTO_CLOSE_DELAY_MS = 10000L // 10 секунд
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1001
        const val EXTRA_RESULT_TEXT = "com.example.asr.EXTRA_RESULT_TEXT"
        
        fun startService(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
        
        /**
         * Обновляет текст в оверлее
         */
        fun updateResultText(context: Context, text: String) {
            val intent = Intent(context, OverlayService::class.java)
            intent.action = "UPDATE_TEXT"
            intent.putExtra(EXTRA_RESULT_TEXT, text)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var handler: Handler? = null
    private var autoCloseRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "OverlayService created")
        handler = Handler()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i(TAG, "OverlayService started")
        
        // Проверяем разрешение SYSTEM_ALERT_WINDOW
        if (!checkOverlayPermission()) {
            Logger.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted")
            stopSelf()
            return START_NOT_STICKY
        }

        // Обрабатываем обновление текста
        if (intent?.action == "UPDATE_TEXT") {
            val newText = intent.getStringExtra(EXTRA_RESULT_TEXT)
            if (!newText.isNullOrEmpty()) {
                updateResultText(newText)
            }
            return START_NOT_STICKY
        }

        // Проверяем, не отображен ли уже оверлей
        if (isOverlayDisplayed()) {
            Logger.i(TAG, "Overlay already displayed, ignoring new request")
            return START_STICKY
        }

        // Создаем и отображаем оверлей
        createAndShowOverlay()
        
        // Запускаем таймер автоматического закрытия
        startAutoCloseTimer()
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.i(TAG, "OverlayService destroyed")
        removeOverlay()
        handler?.removeCallbacksAndMessages(null)
    }

    /**
     * Проверяет наличие разрешения SYSTEM_ALERT_WINDOW
     */
    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(this)
        } else {
            // Для Android < 6.0 разрешение не требуется
            true
        }
    }

    /**
     * Проверяет, отображен ли уже оверлей
     */
    private fun isOverlayDisplayed(): Boolean {
        try {
            // Проверяем, существует ли view в WindowManager
            windowManager?.let { wm ->
                val views = wm.views
                for (view in views) {
                    if (view === overlayView) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error checking overlay display status", e)
        }
        return false
    }

    /**
     * Создает и отображает оверлей
     */
    private fun createAndShowOverlay() {
        try {
            // Создаем View для оверлея
            val layoutInflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            overlayView = layoutInflater.inflate(R.layout.overlay_popup, null)
            
            // Проверяем, что view успешно создано
            if (overlayView == null) {
                Logger.e(TAG, "Failed to inflate overlay layout")
                stopSelf()
                return
            }
            
            // Настраиваем текст
            val resultTextView = overlayView?.findViewById<TextView>(R.id.textViewResult)
            resultTextView?.text = "Результат распознавания будет отображаться здесь"
            
            // Настраиваем кнопки
            val copyButton = overlayView?.findViewById<Button>(R.id.buttonCopy)
            val closeButton = overlayView?.findViewById<Button>(R.id.buttonClose)
            
            copyButton?.setOnClickListener {
                copyToClipboard()
            }
            
            closeButton?.setOnClickListener {
                stopSelf()
            }
            
            // Настройка параметров WindowManager
            val layoutParams = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                format = PixelFormat.TRANSLUCENT
                
                // Используем правильный тип для разных версий Android
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    type = WindowManager.LayoutParams.TYPE_PHONE
                }
                
                // Оставляем только необходимые флаги для взаимодействия с элементами
                flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                
                // Устанавливаем margin для отступа от краев
                x = 0
                y = 100
            }
            
            // Добавляем оверлей в WindowManager
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            windowManager?.addView(overlayView, layoutParams)
            
            Logger.i(TAG, "Overlay created and displayed successfully")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create overlay", e)
            Toast.makeText(this, "Ошибка отображения оверлея", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    /**
     * Обновляет текст в оверлее
     */
    private fun updateResultText(newText: String) {
        try {
            val resultTextView = overlayView?.findViewById<TextView>(R.id.textViewResult)
            resultTextView?.text = newText
            Logger.i(TAG, "Overlay text updated to: $newText")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to update overlay text", e)
        }
    }

    /**
     * Запускает таймер автоматического закрытия
     */
    private fun startAutoCloseTimer() {
        autoCloseRunnable = Runnable {
            Logger.i(TAG, "Auto closing overlay after $AUTO_CLOSE_DELAY_MS ms")
            stopSelf()
        }
        
        handler?.postDelayed(autoCloseRunnable!!, AUTO_CLOSE_DELAY_MS)
    }

    /**
     * Копирует текст в буфер обмена
     */
    private fun copyToClipboard() {
        try {
            val resultTextView = overlayView?.findViewById<TextView>(R.id.textViewResult)
            val text = resultTextView?.text.toString()
            
            if (text.isNotEmpty()) {
                // Используем современный способ копирования для Android 10+
                val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                
                val clip = android.content.ClipData.newPlainText("Recognition Result", text)
                
                clipboardManager.setPrimaryClip(clip)
                
                Toast.makeText(this, "Результат скопирован в буфер обмена", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Нет текста для копирования", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to copy text to clipboard", e)
            Toast.makeText(this, "Ошибка копирования в буфер обмена", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Удаляет оверлей из WindowManager
     */
    private fun removeOverlay() {
        try {
            overlayView?.let { view ->
                windowManager?.removeView(view)
                Logger.i(TAG, "Overlay removed successfully")
                overlayView = null // Сбрасываем ссылку для предотвращения утечек
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to remove overlay", e)
        }
    }

    /**
     * Обработка удаления задачи
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Logger.i(TAG, "OverlayService task removed")
        stopSelf()
    }
}