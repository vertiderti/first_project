package com.example.asr.core.postprocess

import com.example.asr.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Постобработчик текста после распознавания речи
 * Выполняет чистку, нормализацию и восстановление знаков препинания
 */
class PostProcessor {

    companion object {
        private const val TAG = "PostProcessor"
        
        // Регулярные выражения для очистки текста
        private val CLEANUP_PATTERN = Regex("[^\\p{L}\\p{N}\\p{P}\\p{S}\\p{Zs}]+")
        private val EXTRA_WHITESPACE_PATTERN = Regex("\\s+")
        private val LEADING_TRAILING_SPACE_PATTERN = Regex("^[\\s]+|[\\s]+$")
        private val SENTENCE_ENDINGS = Regex("[.!?]+")
    }

    /**
     * Очищает и нормализует текст после распознавания
     * @param text исходный текст
     * @return очищенный и нормализованный текст
     */
    suspend fun processText(text: String): String {
        return withContext(Dispatchers.Default) {
            try {
                Logger.i(TAG, "Processing text for post-processing")
                
                if (text.isBlank()) {
                    return@withContext ""
                }
                
                // 1. Очистка от лишних символов
                var processedText = cleanupText(text)
                
                // 2. Нормализация текста
                processedText = normalizeText(processedText)
                
                // 3. Восстановление знаков препинания
                processedText = restorePunctuation(processedText)
                
                // 4. Удаление лишних пробелов
                processedText = removeExtraWhitespace(processedText)
                
                Logger.i(TAG, "Text post-processing completed: \"$processedText\"")
                return@withContext processedText
                
            } catch (e: Exception) {
                Logger.e(TAG, "Error during text post-processing", e)
                // Возвращаем исходный текст в случае ошибки
                return@withContext text
            }
        }
    }

    /**
     * Очищает текст от лишних символов и специальных символов
     */
    private fun cleanupText(text: String): String {
        try {
            // Удаляем все символы, кроме букв, цифр, знаков препинания, символов и пробелов
            var cleaned = CLEANUP_PATTERN.replace(text, " ")
            
            // Заменяем несколько пробелов на один
            cleaned = EXTRA_WHITESPACE_PATTERN.replace(cleaned, " ")
            
            // Удаляем ведущие и завершающие пробелы
            cleaned = LEADING_TRAILING_SPACE_PATTERN.replace(cleaned, "")
            
            return cleaned.trim()
        } catch (e: Exception) {
            Logger.e(TAG, "Error in text cleanup", e)
            return text
        }
    }

    /**
     * Нормализует текст (приводит к единообразному формату)
     */
    private fun normalizeText(text: String): String {
        try {
            if (text.isBlank()) return text
            
            // Приводим к нижнему регистру
            var normalized = text.lowercase()
            
            // Восстанавливаем стандартные сокращения и имена
            normalized = capitalizeWords(normalized)
            
            return normalized
        } catch (e: Exception) {
            Logger.e(TAG, "Error in text normalization", e)
            return text
        }
    }

    /**
     * Приводит первые буквы слов к заглавным
     */
    private fun capitalizeWords(text: String): String {
        try {
            if (text.isBlank()) return text
            
            val words = text.split(Regex("\\s+"))
            val capitalizedWords = words.map { word ->
                if (word.isNotEmpty()) {
                    word.substring(0, 1).uppercase() + word.substring(1).lowercase()
                } else {
                    word
                }
            }
            
            return capitalizedWords.joinToString(" ")
        } catch (e: Exception) {
            Logger.e(TAG, "Error in capitalize words", e)
            return text
        }
    }

    /**
     * Восстанавливает знаки препинания в тексте
     */
    private fun restorePunctuation(text: String): String {
        try {
            if (text.isBlank()) return text
            
            var punctuated = text
            
            // Добавляем точку в конце, если её нет
            if (!text.endsWith(".") && !text.endsWith("!") && !text.endsWith("?")) {
                punctuated += "."
            }
            
            // Восстанавливаем пробелы после знаков препинания
            punctuated = punctuated.replace(Regex("(\\p{P})(\\p{L})"), "$1 $2")
            
            // Убираем пробелы перед запятыми и точками с запятой
            punctuated = punctuated.replace(Regex("\\s+([,.:])"), "$1")
            
            // Добавляем пробелы после точек, вопросительных и восклицательных знаков
            punctuated = punctuated.replace(Regex("([.!?])(\\p{L})"), "$1 $2")
            
            return punctuated
        } catch (e: Exception) {
            Logger.e(TAG, "Error in punctuation restoration", e)
            return text
        }
    }

    /**
     * Удаляет лишние пробелы из текста
     */
    private fun removeExtraWhitespace(text: String): String {
        try {
            // Заменяем несколько пробелов на один
            return EXTRA_WHITESPACE_PATTERN.replace(text, " ").trim()
        } catch (e: Exception) {
            Logger.e(TAG, "Error removing extra whitespace", e)
            return text
        }
    }
}
