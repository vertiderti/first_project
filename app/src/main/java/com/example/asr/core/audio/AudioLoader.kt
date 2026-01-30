package com.example.asr.core.audio

import android.content.ContentResolver
import android.net.Uri
import com.example.asr.core.error.DomainError
import com.example.asr.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import kotlin.coroutines.cancellation.CancellationException
/**
 * Загрузчик аудио файлов с поддержкой потоковой обработки и ограничением размера
 */
class AudioLoader(
    private val contentResolver: ContentResolver
) {

    companion object {
        private const val TAG = "AudioLoader"
        private const val MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024 // 50MB ограничение на файл
    }

    /**
     * Потоковая загрузка аудио файла с проверкой размера
     */
    suspend fun loadAudio(uri: String): ByteArray {
        return withContext(Dispatchers.IO) {
            try {
                val audioUri = Uri.parse(uri)
                
                // Проверяем MIME тип
                if (!validateMimeType(audioUri)) {
                    throw DomainError.AudioError("Invalid MIME type for URI: $uri")
                }
                
                // Проверяем размер файла
                val fileSize = getFileSize(audioUri)
                if (fileSize > MAX_FILE_SIZE_BYTES) {
                    throw DomainError.AudioError("File size exceeds limit of ${MAX_FILE_SIZE_BYTES / (1024 * 1024)}MB")
                }
                
                // Загружаем данные в память
                val inputStream = contentResolver.openInputStream(audioUri)
                    ?: throw DomainError.AudioError("Failed to open input stream for URI: $uri")
                
                // Используем use для автоматического закрытия потока
                inputStream.use { stream ->
                    val buffer = ByteArray(8192) // 8KB буфер
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    
                    try {
                        var bytesRead: Int
                        while (stream.read(buffer).also { bytesRead = it } != -1) {
                            byteArrayOutputStream.write(buffer, 0, bytesRead)
                            
                            // Проверяем отмену операции
                            if (Thread.currentThread().isInterrupted) {
                                throw CancellationException("Audio loading was cancelled")
                            }
                        }
                        
                        val audioData = byteArrayOutputStream.toByteArray()
                        
                        // Проверка на пустые данные
                        if (audioData.isEmpty() || audioData.all { it == 0.toByte() }) {
                            throw DomainError.AudioError("Loaded audio data is empty or contains only zeros")
                        }
                        
                        Logger.i(TAG, "Successfully loaded audio file: ${audioData.size} bytes")
                        return@withContext audioData
                        
                    } catch (e: Exception) {
                        throw DomainError.AudioError("Failed to load audio data: ${e.message}")
                    }
                }
                
            } catch (e: Exception) {
                Logger.e(TAG, "Error loading audio file", e)
                throw when (e) {
                    is DomainError -> e
                    else -> DomainError.AudioError("Failed to load audio file: ${e.message}")
                }
            }
        }
    }

    /**
     * Получает размер файла по URI с использованием ParcelFileDescriptor для лучшей надежности
     */
    private fun getFileSize(uri: Uri): Long {
        return try {
            val parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")
            val fileSize = parcelFileDescriptor?.statSize ?: 0
            parcelFileDescriptor?.close()
            fileSize
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to get file size for URI: $uri", e)
            // fallback to ContentResolver query
            try {
                val cursor = contentResolver.query(
                    uri,
                    arrayOf(ContentResolver.EXTRA_SIZE),
                    null,
                    null,
                    null
                )
                
                cursor?.use {
                    if (it.moveToFirst()) {
                        it.getLong(it.getColumnIndexOrThrow(ContentResolver.EXTRA_SIZE))
                    } else {
                        0L
                    }
                } ?: 0L
            } catch (fallbackException: Exception) {
                Logger.w(TAG, "Failed to get file size using fallback method for URI: $uri", fallbackException)
                0L
            }
        }
    }

    /**
     * Проверяет MIME тип файла
     */
    private fun validateMimeType(uri: Uri): Boolean {
        return try {
            val mimeType = contentResolver.getType(uri)
            mimeType?.startsWith("audio/") == true
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to check MIME type for URI: $uri", e)
            false
        }
    }
}
