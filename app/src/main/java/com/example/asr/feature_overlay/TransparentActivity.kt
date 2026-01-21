package com.example.asr.feature_overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.asr.R
import com.example.asr.core.utils.Logger

/**
 * Прозрачное Activity для fallback на OEM устройствах
 * Отображает результаты распознавания поверх других приложений
 */
class TransparentActivity : Activity() {

    companion object {
        private const val TAG = "TransparentActivity"
        
        fun start(context: Context) {
            val intent = Intent(context, TransparentActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            context.startActivity(intent)
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.i(TAG, "TransparentActivity created")
        
        // Проверяем, не отображен ли уже оверлей
        if (isOverlayDisplayed()) {
            Logger.i(TAG, "Overlay already displayed in activity, finishing")
            finish()
            return
        }
        
        // Создаем прозрачный оверлей
        createTransparentOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.i(TAG, "TransparentActivity destroyed")
        removeOverlay()
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
     * Создает прозрачный оверлей для fallback
     */
    private fun createTransparentOverlay() {
        try {
            // Создаем View для оверлея
            val layoutInflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            overlayView = layoutInflater.inflate(R.layout.overlay_popup, null)
            
            // Проверяем, что view успешно создано
            if (overlayView == null) {
                Logger.e(TAG, "Failed to inflate overlay layout")
                finish()
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
                finish()
            }
            
            // Настройка параметров WindowManager
            val layoutParams = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                format = PixelFormat.TRANSLUCENT
                
                // Для прозрачного Activity используем тип TYPE_APPLICATION_OVERLAY
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    type = WindowManager.LayoutParams.TYPE_PHONE
                }
                
                // Оставляем только необходимые флаги для взаимодействия с элементами
                flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                
                // Устанавливаем цвет фона прозрачным
                windowAnimations = android.R.style.Animation_Dialog
                
                // Устанавливаем margin для отступа от краев
                x = 0
                y = 100
            }
            
            // Добавляем оверлей в WindowManager
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            windowManager?.addView(overlayView, layoutParams)
            
            Logger.i(TAG, "Transparent overlay created and displayed successfully")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create transparent overlay", e)
            Toast.makeText(this, "Ошибка отображения оверлея", Toast.LENGTH_SHORT).show()
            finish()
        }
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
            // Проверяем, что Activity не завершается и view существует
            if (!isFinishing && overlayView != null) {
                windowManager?.removeView(overlayView)
                Logger.i(TAG, "Transparent overlay removed successfully")
                overlayView = null // Сбрасываем ссылку для предотвращения утечек
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to remove transparent overlay", e)
        }
    }

    /**
     * Завершает Activity
     */
    override fun finish() {
        super.finish()
        // Убедимся, что оверлей удален при завершении
        removeOverlay()
    }
}
