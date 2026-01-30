package com.example.asr.feature_overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.asr.R
import com.example.asr.core.utils.Logger

/**
 * Прозрачное Activity для отображения оверлея с результатами распознавания
 * Используется как резервный вариант при недоступности системного оверлея
 */
class TransparentActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TransparentActivity"
        private const val OVERLAY_PERMISSION_REQUEST_CODE = 1001
        const val EXTRA_RESULT_TEXT = "com.example.asr.EXTRA_RESULT_TEXT"
        const val EXTRA_IS_FINAL = "com.example.asr.EXTRA_IS_FINAL"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var resultTextView: TextView? = null
    private var copyButton: Button? = null
    private var isOverlayShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Инициализация WindowManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (windowManager == null) {
            Logger.e(TAG, "Failed to get WindowManager")
            finish()
            return
        }

        // Проверяем и запрашиваем разрешение на отображение оверлея
        if (!checkOverlayPermission()) {
            Logger.w(TAG, "Missing SYSTEM_ALERT_WINDOW permission", fallbackException)
            finish()
            return
        }

        // Создаем и показываем оверлей
        showOverlay()
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
     * Показывает оверлей с результатами распознавания
     */
    private fun showOverlay() {
        if (isOverlayShown) {
            Logger.w(TAG, "Overlay is already shown", fallbackException)
            return
        }

        try {
            // Создаем view для оверлея
            val inflater = LayoutInflater.from(this)
            overlayView = inflater.inflate(R.layout.overlay_popup, null)

            resultTextView = overlayView?.findViewById(R.id.result_text)
            copyButton = overlayView?.findViewById(R.id.btn_copy)

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
                // Исправленные флаги для оверлея
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
        } catch (e: Exception) {
            Logger.e(TAG, "Error showing overlay", e)
            isOverlayShown = false
            finish() // Закрываем activity в случае ошибки
        }
    }

    /**
     * Копирует текст в буфер обмена
     */
    private fun copyToClipboard(text: String) {
        try {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            if (clipboardManager != null) {
                val clipData = android.content.ClipData.newPlainText("speech_result", text)
                clipboardManager.setPrimaryClip(clipData)
                Logger.i(TAG, "Text copied to clipboard")
            } else {
                Logger.w(TAG, "Clipboard manager is null", fallbackException)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error copying to clipboard", e)
        }
    }

    /**
     * Обновляет текст результата в оверlays
     */
    private fun updateResult(resultText: String, isFinal: Boolean) {
        try {
            resultTextView?.let { textView ->
                // Обновляем текст с учетом финальности результата
                if (isFinal) {
                    textView.text = resultText
                } else {
                    // Для промежуточных результатов добавляем точку в конце
                    textView.text = "$resultText..."
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error updating result text", e)
        }
    }

    /**
     * Скрывает оверлей и завершает activity
     */
    private fun hideOverlay() {
        if (!isOverlayShown) {
            Logger.w(TAG, "Overlay is not shown", fallbackException)
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
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error hiding overlay", e)
        }
    }

    /**
     * Проверяет, отображается ли оверлей
     */
    private fun isOverlayDisplayed(): Boolean {
        // Используем флаг состояния вместо windowManager.views
        return isOverlayShown
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.i(TAG, "TransparentActivity destroyed")
        
        // Очищаем ресурсы
        hideOverlay()
        windowManager = null
    }

    override fun onBackPressed() {
        // Закрываем оверлей и завершаем activity при нажатии назад
        hideOverlay()
        super.onBackPressed()
    }
}
