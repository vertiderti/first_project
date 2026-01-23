package com.example.asr.core.error

import android.util.Log
import com.example.asr.core.utils.Logger

/**
 * Доменная модель ошибок для ASR приложения
 * Предоставляет унифицированный способ работы с ошибками в доменной области
 */
sealed class DomainError {
    companion object {
        private const val TAG = "DomainError"
    }
    
    /**
     * Ошибка обработки аудио данных
     */
    data class AudioProcessingError(val message: String, val cause: Throwable? = null) : DomainError() {
        fun log(logger: Logger) {
            logger.error("Audio processing error: $message", cause)
        }
    }
    
    /**
     * Ошибка распознавания речи
     */
    data class RecognitionError(val message: String, val cause: Throwable? = null) : DomainError() {
        fun log(logger: Logger) {
            logger.error("Recognition error: $message", cause)
        }
    }
    
    /**
     * Ошибка разрешений
     */
    data class PermissionError(val message: String, val cause: Throwable? = null) : DomainError() {
        fun log(logger: Logger) {
            logger.error("Permission error: $message", cause)
        }
    }
    
    /**
     * Ошибка модели
     */
    data class ModelError(val message: String, val cause: Throwable? = null) : DomainError() {
        fun log(logger: Logger) {
            logger.error("Model error: $message", cause)
        }
    }
    
    /**
     * Ошибка сервиса
     */
    data class ServiceError(val message: String, val cause: Throwable? = null) : DomainError() {
        fun log(logger: Logger) {
            logger.error("Service error: $message", cause)
        }
    }
    
    /**
     * Ошибка сети
     */
    data class NetworkError(val message: String, val cause: Throwable? = null) : DomainError() {
        fun log(logger: Logger) {
            logger.error("Network error: $message", cause)
        }
    }
    
    /**
     * Базовый метод для логирования ошибки
     */
    fun log(logger: Logger) {
        when (this) {
            is AudioProcessingError -> this.log(logger)
            is RecognitionError -> this.log(logger)
            is PermissionError -> this.log(logger)
            is ModelError -> this.log(logger)
            is ServiceError -> this.log(logger)
            is NetworkError -> this.log(logger)
        }
    }
}

/**
 * Расширение для удобного логирования ошибок
 */
fun DomainError.logWithMetadata(logger: Logger, metadata: Map<String, Any> = emptyMap()) {
    val fullMessage = buildString {
        append(this@logWithMetadata::class.simpleName)
        append(": ")
        append(this@logWithMetadata.toString())
        if (metadata.isNotEmpty()) {
            append(" | Metadata: $metadata")
        }
    }
    
    Log.e(DomainError.TAG, fullMessage)
    this.log(logger)
}
