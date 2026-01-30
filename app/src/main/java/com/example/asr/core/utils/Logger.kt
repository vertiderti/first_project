package com.example.asr.core.utils

import android.util.Log

/**
 * Универсальный логгер для приложения
 * Предоставляет стандартные методы логирования с поддержкой различных уровней
 */
object Logger {
    
    private const val DEFAULT_TAG = "ASR_APP"
    private const val MAX_LOG_LENGTH = 4000
    private const val MAX_STACK_TRACE_ELEMENTS = 100
    
    /**
     * Логирование отладочной информации
     * @param tag тег для идентификации источника лога
     * @param message текст сообщения
     */
    fun d(tag: String, message: String) {
        if (message.isBlank()) {
            Log.d(tag, "Empty message")
            return
        }
        
        if (message.length > MAX_LOG_LENGTH) {
            // Разбиваем длинное сообщение на части
            var i = 0
            while (i < message.length) {
                val end = minOf(i + MAX_LOG_LENGTH, message.length)
                Log.d(tag, message.substring(i, end))
                i = end
            }
        } else {
            Log.d(tag, message)
        }
    }
    
    /**
     * Логирование отладочной информации с форматированием
     * @param tag тег для идентификации источника лога
     * @param message текст сообщения с форматными спецификаторами
     * @param args аргументы для форматирования
     */
    fun d(tag: String, message: String, vararg args: Any?) {
        val formattedMessage = if (args.isNotEmpty()) {
            try {
                String.format(message, *args)
            } catch (e: Exception) {
                "Failed to format message: $message with args: ${args.contentToString()}"
            }
        } else {
            message
        }
        d(tag, formattedMessage)
    }
    
    /**
     * Логирование информации
     * @param tag тег для идентификации источника лога
     * @param message текст сообщения
     */
    fun i(tag: String, message: String) {
        if (message.isBlank()) {
            Log.i(tag, "Empty message")
            return
        }
        
        if (message.length > MAX_LOG_LENGTH) {
            // Разбиваем длинное сообщение на части
            var i = 0
            while (i < message.length) {
                val end = minOf(i + MAX_LOG_LENGTH, message.length)
                Log.i(tag, message.substring(i, end))
                i = end
            }
        } else {
            Log.i(tag, message)
        }
    }
    
    /**
     * Логирование информации с форматированием
     * @param tag тег для идентификации источника лога
     * @param message текст сообщения с форматными спецификаторами
     * @param args аргументы для форматирования
     */
    fun i(tag: String, message: String, vararg args: Any?) {
        val formattedMessage = if (args.isNotEmpty()) {
            try {
                String.format(message, *args)
            } catch (e: Exception) {
                "Failed to format message: $message with args: ${args.contentToString()}"
            }
        } else {
            message
        }
        i(tag, formattedMessage)
    }
    
    /**
     * Логирование ошибок
     * @param tag тег для идентификации источника лога
     * @param message текст сообщения об ошибке
     * @param throwable исключение (опционально)
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (message.isBlank()) {
            Log.e(tag, "Empty error message", throwable)
            return
        }
        
        if (message.length > MAX_LOG_LENGTH) {
            // Разбиваем длинное сообщение на части
            var i = 0
            while (i < message.length) {
                val end = minOf(i + MAX_LOG_LENGTH, message.length)
                Log.e(tag, message.substring(i, end), throwable)
                i = end
            }
        } else {
            Log.e(tag, message, throwable)
        }
    }
    
    /**
     * Логирование ошибок без сообщения (только исключение)
     * @param tag тег для идентификации источника лога
     * @param throwable исключение
     */
    fun e(tag: String, throwable: Throwable) {
        Log.e(tag, throwable.message ?: "Unknown error", throwable)
    }
    
    /**
     * Логирование предупреждений
     * @param tag тег для идентификации источника лога
     * @param message текст сообщения предупреждения
     */
    fun w(tag: String, message: String, fallbackException: Exception) {
        if (message.isBlank()) {
            Log.w(tag, "Empty warning message")
            return
        }
        
        if (message.length > MAX_LOG_LENGTH) {
            // Разбиваем длинное сообщение на части
            var i = 0
            while (i < message.length) {
                val end = minOf(i + MAX_LOG_LENGTH, message.length)
                Log.w(tag, message.substring(i, end))
                i = end
            }
        } else {
            Log.w(tag, message)
        }
    }
    
    /**
     * Логирование информации с использованием тега по умолчанию
     * @param message текст сообщения
     */
    fun logInfo(message: String) {
        i(DEFAULT_TAG, message)
    }
    fun info(message: String) {
        i(DEFAULT_TAG, message)
    }
    /**
     * Логирование ошибок с использованием тега по умолчанию
     * @param message текст сообщения об ошибке
     * @param throwable исключение (опционально)
     */
    fun logError(message: String, throwable: Throwable? = null) {
        e(DEFAULT_TAG, message, throwable)
    }
    fun error(message: String, throwable: Throwable? = null) {
        e(DEFAULT_TAG, message, throwable)
    }
    /**
     * Логирование отладочной информации с использованием тега по умолчанию
     * @param message текст сообщения
     */
    fun logDebug(message: String) {
        d(DEFAULT_TAG, message)
    }
    
    /**
     * Логирование предупреждений с использованием тега по умолчанию
     * @param message текст сообщения предупреждения
     */
    fun logWarning(message: String) {
        w(DEFAULT_TAG, message, fallbackException)
    }
    fun warn(message: String) {
        w(DEFAULT_TAG, message, fallbackException)
    }
    /**
     * Логирование Throwable с полным стек-трейсом
     * @param tag тег для идентификации источника лога
     * @param throwable исключение
     * @param message дополнительное сообщение (опционально)
     */
    fun logThrowable(tag: String, throwable: Throwable, message: String = "") {
        val fullMessage = if (message.isNotEmpty()) {
            "$message: ${throwable.message}"
        } else {
            throwable.message ?: "Unknown error"
        }
        
        Log.e(tag, fullMessage, throwable)
    }
    
    /**
     * Логирование стек-трейса вручную
     * @param tag тег для идентификации источника лога
     * @param throwable исключение
     */
    fun logStackTrace(tag: String, throwable: Throwable) {
        val stackTrace = StringBuilder()
        stackTrace.append(throwable.toString()).append("\n")
        
        val elements = throwable.stackTrace.take(MAX_STACK_TRACE_ELEMENTS)
        for (element in elements) {
            stackTrace.append("  at ").append(element.toString()).append("\n")
        }
        
        Log.e(tag, stackTrace.toString(), throwable)
    }
    
    /**
     * Логирование JSON данных
     * @param tag тег для идентификации источника лога
     * @param json строка JSON
     */
    fun logJson(tag: String, json: String) {
        if (json.isBlank()) {
            Log.d(tag, "Empty JSON")
            return
        }
        
        val lines = json.chunked(MAX_LOG_LENGTH)
        lines.forEach { line ->
            Log.d(tag, line)
        }
    }
    
    /**
     * Логирование объекта в формате JSON (если возможно)
     * @param tag тег для идентификации источника лога
     * @param obj объект для логирования
     */
    fun logObject(tag: String, obj: Any?) {
        val message = if (obj == null) {
            "null object"
        } else {
            obj.toString()
        }
        d(tag, message)
    }
}
