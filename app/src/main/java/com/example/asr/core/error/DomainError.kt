package com.example.asr.core.error

import android.util.Log
import com.example.asr.core.utils.Logger

/**
 * Доменная модель ошибок для ASR приложения
 * Предоставляет унифицированный способ работы с ошибками в доменной области
 */
sealed class DomainError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    companion object {
        const val TAG = "DomainError"
    }

    data class AudioError(val msg: String, val err: Throwable? = null) : DomainError(msg, err) {
        fun log(logger: Logger) {
            logger.error("Audio processing error: $msg", err)
        }
    }

    data class RecognitionError(val msg: String, val err: Throwable? = null) : DomainError(msg, err) {
        fun log(logger: Logger) {
            logger.error("Recognition error: $msg", err)
        }
    }

    data class PermissionError(val msg: String, val err: Throwable? = null) : DomainError(msg, err) {
        fun log(logger: Logger) {
            logger.error("Permission error: $msg", err)
        }
    }

    data class ModelError(val msg: String, val err: Throwable? = null) : DomainError(msg, err) {
        fun log(logger: Logger) {
            logger.error("Model error: $msg", err)
        }
    }

    data class ServiceError(val msg: String, val err: Throwable? = null) : DomainError(msg, err) {
        fun log(logger: Logger) {
            logger.error("Service error: $msg", err)
        }
    }

    data class NetworkError(val msg: String, val err: Throwable? = null) : DomainError(msg, err) {
        fun log(logger: Logger) {
            logger.error("Network error: $msg", err)
        }
    }

    fun log(logger: Logger) {
        when (this) {
            is AudioError -> this.log(logger)
            is RecognitionError -> this.log(logger)
            is PermissionError -> this.log(logger)
            is ModelError -> this.log(logger)
            is ServiceError -> this.log(logger)
            is NetworkError -> this.log(logger)
        }
    }
}
