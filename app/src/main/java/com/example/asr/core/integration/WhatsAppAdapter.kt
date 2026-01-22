package com.example.asr.core.integration

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import java.io.InputStream

/**
 * Адаптер для интеграции с WhatsApp
 * Обрабатывает интенты от WhatsApp для получения аудио данных
 */
class WhatsAppAdapter : MessengerAdapter, ContextAware {
    
    companion object {
        private const val TAG = "WhatsAppAdapter"
        private const val WHATSAPP_PACKAGE_NAME = "com.whatsapp"
        
        // Константы для WhatsApp
        const val WHATSAPP_ACTION_SEND = "android.intent.action.SEND"
        
        // MIME типы для WhatsApp
        private const val MIME_TYPE_AUDIO_PREFIX = "audio/"
        private const val MIME_TYPE_VOICE_PREFIX = "voice/"
    }

    private var context: Context? = null
    
    override fun setContext(context: Context) {
        this.context = context
    }

    override fun getAudioStream(intent: Intent): InputStream? {
        try {
            // Проверяем, поддерживает ли этот адаптер интент
            if (!supportsIntent(intent)) {
                return null
            }
            
            val ctx = intent.context ?: run {
                android.util.Log.e(TAG, "Context is null in intent")
                return null
            }
            
            var uri: Uri? = null
            
            // Попробуем извлечь из разных источников
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM)
            
            // Если не нашли в extras, проверим clipData
            if (uri == null) {
                val clipData = intent.clipData
                if (clipData != null && clipData.itemCount > 0) {
                    uri = clipData.getItemAt(0).uri
                }
            }
            
            // Если всё ещё не нашли, попробуем из extras с более широкой проверкой
            if (uri == null) {
                val extras = intent.extras
                if (extras != null) {
                    for (key in extras.keySet()) {
                        if (key.contains("stream", ignoreCase = true) || 
                            key.contains("uri", ignoreCase = true)) {
                            uri = extras.getParcelable(key)
                            break
                        }
                    }
                }
            }
            
            return if (uri != null) {
                // Добавляем проверку на существование URI
                if (!isUriValid(ctx, uri)) {
                    android.util.Log.w(TAG, "Invalid URI: $uri")
                    return null
                }
                
                ctx.contentResolver.openInputStream(uri)
            } else {
                null
            }
            
        } catch (e: Exception) {
            // Логируем ошибку и возвращаем null
            android.util.Log.e(TAG, "Error getting audio stream from WhatsApp", e)
            return null
        }
    }

    override fun supportsIntent(intent: Intent): Boolean {
        try {
            val packageName = intent.`package`?.toString()
            val action = intent.action
            
            // Проверка по имени пакета с более гибкой логикой
            if (!packageName.isNullOrEmpty()) {
                when {
                    packageName.contains("whatsapp", ignoreCase = true) -> return true
                }
            }
            
            // Проверка по действию
            if (action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE) {
                return true
            }
            
            // Проверка MIME типов
            val type = intent.type
            if (type != null && (type.startsWith(MIME_TYPE_AUDIO_PREFIX) || 
                                 type.startsWith(MIME_TYPE_VOICE_PREFIX))) {
                return true
            }
            
            // Дополнительная проверка через extras
            val extras = intent.extras
            if (extras != null) {
                for (key in extras.keySet()) {
                    if (key.contains("stream", ignoreCase = true) ||
                        key.contains("uri", ignoreCase = true) ||
                        key.contains("message", ignoreCase = true)) {
                        return true
                    }
                }
            }
            
            return false
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error checking WhatsApp intent support", e)
            return false
        }
    }

    override fun isAvailable(): Boolean {
        return try {
            val ctx = context ?: return false
            val packageManager = ctx.packageManager
            packageManager.getPackageInfo(WHATSAPP_PACKAGE_NAME, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error checking WhatsApp availability", e)
            false
        }
    }

    /**
     * Проверяет валидность URI перед использованием
     */
    private fun isUriValid(context: Context, uri: Uri): Boolean {
        return try {
            val parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            val isValid = parcelFileDescriptor != null
            parcelFileDescriptor?.close()
            isValid
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error validating URI: $uri", e)
            false
        }
    }
}
