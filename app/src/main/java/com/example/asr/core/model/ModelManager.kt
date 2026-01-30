package com.example.asr.core.model

import android.content.Context
import com.example.asr.core.error.DomainError
import com.example.asr.core.utils.Logger
import java.io.File
import java.security.MessageDigest

/**
 * Управляет ASR-моделями: кеширование, проверка целостности через SHA256
 */
class ModelManager(
    private val context: Context,
    private val logger: Logger
) {
    
    companion object {
        private const val MODEL_CACHE_DIR = "asr_models"
        private const val MODEL_FILE_NAME = "asr_model.bin"
        private const val MODEL_SHA_FILE_NAME = "asr_model.sha256"
    }
    
    private val modelCacheDir: File by lazy {
        File(context.cacheDir, MODEL_CACHE_DIR)
    }
    
    private val modelFile: File by lazy {
        File(modelCacheDir, MODEL_FILE_NAME)
    }
    
    private val shaFile: File by lazy {
        File(modelCacheDir, MODEL_SHA_FILE_NAME)
    }
    
    /**
     * Загружает модель из кэша
     *
     * @return File объект модели или null если модель не существует или некорректна
     */
    fun loadModel(): File? {
        return try {
            // Проверяем существование модели и целостность
            if (modelFile.exists() && isModelValid()) {
                logger.info("Model loaded successfully from cache")
                modelFile
            } else {
                logger.warn("Model not found or invalid in cache")
                null
            }
        } catch (e: Exception) {
            logger.error("Error loading model", e)
            null
        }
    }
    
    /**
     * Проверяет, существует ли модель и целостна ли она
     *
     * @return true если модель существует и прошла проверку целостности
     */
    fun isModelValid(): Boolean {
        return try {
            // Проверяем существование модели
            if (!modelFile.exists()) {
                logger.warn("ASR model file not found")
                return false
            }
            
            // Проверяем существование SHA файла
            if (!shaFile.exists()) {
                logger.warn("ASR model SHA file not found")
                return false
            }
            
            // Читаем сохраненный хеш
            val savedHash = shaFile.readText().trim()
            if (savedHash.isEmpty()) {
                logger.warn("Saved hash is empty")
                return false
            }
            
            // Вычисляем текущий хеш модели
            val currentHash = calculateSHA256(modelFile)
            
            // Сравниваем хеши
            currentHash.equals(savedHash, ignoreCase = true)
        } catch (e: Exception) {
            logger.error("Error checking model validity", e)
            false
        }
    }
    
    /**
     * Сохраняет модель с проверкой целостности
     *
     * @param modelData байты модели
     * @param expectedSha256 ожидаемый SHA256 хеш модели
     * @return true если модель успешно сохранена и прошла проверку
     */
    fun saveModel(modelData: ByteArray, expectedSha256: String): Boolean {
        return try {
            // Создаем директорию кэша если она не существует
            if (!modelCacheDir.exists()) {
                modelCacheDir.mkdirs()
            }
            
            // Проверяем, что ожидаемый хеш не пустой
            if (expectedSha256.isBlank()) {
                logger.error("Expected SHA256 hash is empty")
                return false
            }
            
            // Сохраняем модель в временное место, затем перемещаем
            val tempFile = File(modelCacheDir, "$MODEL_FILE_NAME.tmp")
            tempFile.writeBytes(modelData)
            
            val calculatedHash = calculateSHA256(tempFile)
            
            // Проверяем соответствие ожидаемого и вычисленного хеша
            if (calculatedHash.equals(expectedSha256, ignoreCase = true)) {
                // Перемещаем временный файл в основной
                tempFile.renameTo(modelFile)
                shaFile.writeText(calculatedHash)
                logger.info("Model successfully saved and verified")
                true
            } else {
                logger.error("Model SHA256 verification failed. Expected: $expectedSha256, Actual: $calculatedHash")
                tempFile.delete()
                false
            }
        } catch (e: Exception) {
            logger.error("Error saving model", e)
            // Удаляем частично сохраненную модель в случае ошибки
            try {
                if (modelFile.exists()) modelFile.delete()
                if (shaFile.exists()) shaFile.delete()
            } catch (deleteException: Exception) {
                logger.error("Error deleting partially saved model", deleteException)
            }
            false
        }
    }
    
    /**
     * Возвращает путь к файлу модели
     *
     * @return File объект модели или null если модель не существует
     */
    fun getModelFile(): File? {
        return try {
            if (modelFile.exists() && isModelValid()) {
                modelFile
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error("Error getting model file", e)
            null
        }
    }
    
    /**
     * Удаляет модель из кэша
     */
    fun clearModel() {
        try {
            if (modelFile.exists()) {
                modelFile.delete()
            }
            if (shaFile.exists()) {
                shaFile.delete()
            }
            logger.info("Model cache cleared")
        } catch (e: Exception) {
            logger.error("Error clearing model cache", e)
        }
    }
    
    /**
     * Вычисляет SHA256 хеш файла
     *
     * @param file файл для вычисления хеша
     * @return SHA256 хеш в виде строки
     */
    private fun calculateSHA256(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val hashBytes = digest.digest()
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            logger.error("Error calculating SHA256", e)
            throw DomainError.ModelError("Failed to calculate SHA256 hash: ${e.message}")
        }
    }
    
    /**
     * Получает размер модели в байтах
     *
     * @return размер модели или 0 если модель не существует
     */
    fun getModelSize(): Long {
        return try {
            if (modelFile.exists() && isModelValid()) {
                modelFile.length()
            } else {
                0L
            }
        } catch (e: Exception) {
            logger.error("Error getting model size", e)
            0L
        }
    }
    
    /**
     * Проверяет, есть ли кэшированная модель
     *
     * @return true если модель существует и доступна
     */
    fun hasCachedModel(): Boolean {
        return try {
            modelFile.exists() && isModelValid()
        } catch (e: Exception) {
            logger.error("Error checking cached model", e)
            false
        }
    }
}
