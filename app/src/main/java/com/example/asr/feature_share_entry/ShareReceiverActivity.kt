package com.example.asr.feature_share_entry

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.asr.feature_overlay.RecognitionService
import com.example.asr.core.error.DomainError
import com.example.asr.core.utils.Logger
import java.io.File

/**
 * Activity для обработки входящих аудиофайлов через Share (Intent.ACTION_SEND)
 * Запускает RecognitionService для дальнейшей обработки аудио
 */
class ShareReceiverActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AUDIO_URI = "com.example.asr.EXTRA_AUDIO_URI"
        private const val TAG = "ShareReceiverActivity"
        private const val MAX_FILE_SIZE = 50L * 1024 * 1024 // 50MB
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Обработка входящего интента
        handleIncomingShare()
        
        // Завершение Activity после обработки
        finish()
    }

    /**
     * Основной метод обработки входящего Share-интента
     */
    private fun handleIncomingShare() {
        try {
            val intent = intent ?: run {
                Logger.e(TAG, "Intent is null")
                Toast.makeText(this, "Ошибка обработки файла", Toast.LENGTH_SHORT).show()
                return
            }

            // Проверяем, что система не занята
            if (!tryLockBusy()) {
                Toast.makeText(this, "Система занята", Toast.LENGTH_SHORT).show()
                Logger.w(TAG, "Service busy, reject new request")
                return
            }

            when (intent.action) {
                Intent.ACTION_SEND -> {
                    handleSingleShare(intent)
                }
                
                Intent.ACTION_SEND_MULTIPLE -> {
                    Logger.i(TAG, "ACTION_SEND_MULTIPLE received - processing first file")
                    Toast.makeText(
                        this,
                        "Пока поддерживается только один аудиофайл",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Обрабатываем только первый файл из списка
                    val uri = if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    } else if (intent.clipData != null && intent.clipData?.itemCount ?: 0 > 0) {
                        intent.clipData?.getItemAt(0)?.uri
                    } else {
                        null
                    }
                    
                    if (uri != null) {
                        handleAudioUri(uri)
                    } else {
                        Logger.e(TAG, "No audio files found in intent")
                        Toast.makeText(this, "Не найдено аудиофайлов", Toast.LENGTH_SHORT).show()
                    }
                }
                
                else -> {
                    Logger.w(TAG, "Unsupported action: ${intent.action}")
                    Toast.makeText(this, "Неподдерживаемое действие", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error handling share", e)
            Toast.makeText(this, "Ошибка обработки файла", Toast.LENGTH_SHORT).show()
            // Освобождаем блокировку в случае ошибки
            releaseLock()
        }
    }

    /**
     * Обработка одного аудиофайла из Intent
     */
    private fun handleSingleShare(intent: Intent) {
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            ?: intent.clipData?.getItemAt(0)?.uri

        if (uri == null) {
            Logger.e(TAG, "Uri is null from intent")
            Toast.makeText(this, "Не удалось получить URI файла", Toast.LENGTH_SHORT).show()
            releaseLock() // Освобождаем блокировку при ошибке
            return
        }

        // Проверяем корректность URI
        if (!isValidUri(uri)) {
            Logger.e(TAG, "Invalid URI: $uri")
            Toast.makeText(this, "Некорректный файл", Toast.LENGTH_SHORT).show()
            releaseLock() // Освобождаем блокировку при ошибке
            return
        }

        // MIME type check
        val type =
            intent.type
                ?: contentResolver.getType(uri)

        if (type?.startsWith("audio/") != true) {
            if (!looksLikeAudio(uri)) {
                Logger.e(TAG, "Not audio mime type: $type")
                Toast.makeText(this, "Выбранный файл не является аудио", Toast.LENGTH_SHORT).show()
                releaseLock() // Освобождаем блокировку при ошибке
                return
            }
        }

        // Permission check
        if (!canReadUri(uri)) {
            Logger.e(TAG, "No permission to read uri")
            Toast.makeText(this, "Нет прав на чтение файла", Toast.LENGTH_SHORT).show()
            releaseLock() // Освобождаем блокировку при ошибке
            return
        }

        // Проверяем размер файла (только если все предыдущие проверки пройдены)
        if (!checkFileSize(uri)) {
            Logger.e(TAG, "File size exceeds limit: $uri")
            Toast.makeText(this, "Размер файла превышает допустимый лимит", Toast.LENGTH_SHORT).show()
            releaseLock() // Освобождаем блокировку при ошибке
            return
        }

        handleAudioUri(uri)
    }

    /**
     * Обработка аудио URI
     */
    private fun handleAudioUri(uri: Uri) {
        Logger.i(TAG, "Handling audio URI: $uri")
        
        startRecognitionService(uri)
    }

    /**
     * Проверяет корректность URI
     */
    private fun isValidUri(uri: Uri): Boolean {
        return try {
            uri.toString().isNotEmpty() && 
            uri.scheme != null && 
            uri.authority != null
        } catch (e: Exception) {
            Logger.e(TAG, "Error validating URI", e)
            false
        }
    }

    /**
     * Запуск RecognitionService для обработки аудио
     */
    private fun startRecognitionService(audioUri: Uri) {
        val intent = Intent(this, RecognitionService::class.java)
        intent.putExtra(RecognitionService.EXTRA_AUDIO_URI, audioUri.toString())
        intent.action = RecognitionService.ACTION_START_RECOGNITION
        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start recognition service", e)
            Toast.makeText(this, "Не удалось запустить обработку", Toast.LENGTH_SHORT).show()
            releaseLock() // Освобождаем блокировку при ошибке
        }
    }

    /**
     * Проверяет размер файла
     */
    private fun checkFileSize(uri: Uri): Boolean {
        return try {
            val parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")
            val fileSize = parcelFileDescriptor?.statSize ?: 0
            // Безопасное закрытие файла дескриптора
            parcelFileDescriptor?.close()
            fileSize > 0 && fileSize < MAX_FILE_SIZE
        } catch (e: Exception) {
            Logger.e(TAG, "Error checking file size", e)
            false
        }
    }

    // --------- helpers ----------

    private fun canReadUri(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                // Используем постраничное чтение для предотвращения OutOfMemoryError
                val buffer = ByteArray(1024)
                val bytesRead = inputStream.read(buffer)
                bytesRead > 0 // Простая проверка наличия данных
            } ?: false
        } catch (e: Exception) {
            Logger.e(TAG, "Error accessing URI", e)
            false
        }
    }

    private fun looksLikeAudio(uri: Uri): Boolean {
        val name = uri.lastPathSegment ?: return false
        
        // Проверка по расширению и MIME с улучшенной обработкой edge-кейсов
        val extensions = setOf(".mp3", ".wav", ".ogg", ".m4a", ".aac", ".flac")
        val mimeType = contentResolver.getType(uri)
        
        return try {
            // Оптимизированная проверка: сначала проверяем расширение, затем MIME
            if (name.isNotEmpty()) {
                extensions.any { name.endsWith(it, true) }
            } else {
                false
            } || 
            mimeType?.startsWith("audio/") == true
        } catch (e: Exception) {
            Logger.e(TAG, "Error checking audio format", e)
            false
        }
    }

    // -------- busy protection --------

    @Synchronized
    private fun tryLockBusy(): Boolean {
        val prefs = getPrefs()
        val busy = prefs.getBoolean(KEY_BUSY, false)

        if (busy) return false

        // Используем apply() вместо commit() для асинхронного сохранения
        prefs.edit()
            .putBoolean(KEY_BUSY, true)
            .apply()

        return true
    }

    /**
     * Освобождение блокировки системы
     */
    private fun releaseLock() {
        getPrefs().edit()
            .putBoolean(KEY_BUSY, false)
            .apply()
    }

    private fun getPrefs() =
        getSharedPreferences(
            PREFS_NAME,
            MODE_PRIVATE
        )

    companion object {
        private const val PREFS_NAME = "recognition"
        private const val KEY_BUSY = "recognition_busy"
    }
}
