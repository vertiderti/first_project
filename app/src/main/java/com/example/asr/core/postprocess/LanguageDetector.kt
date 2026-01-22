package com.example.asr.core.postprocess

import com.example.asr.core.utils.Logger

/**
 * Определитель языка текста
 * Готов к мультиязычности и расширению
 */
class LanguageDetector {

    companion object {
        private const val TAG = "LanguageDetector"
        
        // Коды языков
        const val LANGUAGE_RUSSIAN = "ru"
        const val LANGUAGE_ENGLISH = "en"
        const val LANGUAGE_CHINESE = "zh"
        const val LANGUAGE_JAPANESE = "ja"
        const val LANGUAGE_KOREAN = "ko"
        const val LANGUAGE_UNKNOWN = "unknown"
        
        // Паттерны для определения языка
        private val RUSSIAN_CHARS = Regex("[а-яА-ЯёЁ]")
        private val ENGLISH_CHARS = Regex("[a-zA-Z]")
        private val CHINESE_CHARS = Regex("[\\u4e00-\\u9fff]")
        private val JAPANESE_HIRAGANA = Regex("[\\u3040-\\u309f]")
        private val JAPANESE_KATAKANA = Regex("[\\u30a0-\\u30ff]")
        private val KOREAN_CHARS = Regex("[\\uac00-\\ud7af]")
    }

    /**
     * Определяет язык текста
     * @param text текст для анализа
     * @return код языка (ru, en, zh, ja, ko, unknown)
     */
    fun detectLanguage(text: String): String {
        try {
            Logger.i(TAG, "Detecting language for text: \"$text\"")
            
            if (text.isBlank()) {
                return LANGUAGE_UNKNOWN
            }
            
            // Подсчитываем количество символов каждого языка
            val russianCount = text.count { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
            val englishCount = text.count { it in 'a'..'z' || it in 'A'..'Z' }
            val chineseCount = text.count { it in '\u4e00'..'\u9fff' }
            val japaneseHiraganaCount = text.count { it in '\u3040'..'\u309f' }
            val japaneseKatakanaCount = text.count { it in '\u30a0'..'\u30ff' }
            val koreanCount = text.count { it in '\uac00'..'\ud7af' }
            
            // Если текст содержит только пробелы и специальные символы
            val totalChars = text.length
            if (totalChars == 0) {
                return LANGUAGE_UNKNOWN
            }
            
            val totalAlphabeticChars = russianCount + englishCount + chineseCount + 
                                      japaneseHiraganaCount + japaneseKatakanaCount + koreanCount
            
            // Если нет буквенных символов
            if (totalAlphabeticChars == 0) {
                return LANGUAGE_UNKNOWN
            }
            
            // Рассчитываем доли каждого языка
            val russianRatio = russianCount.toDouble() / totalAlphabeticChars
            val englishRatio = englishCount.toDouble() / totalAlphabeticChars
            val chineseRatio = chineseCount.toDouble() / totalAlphabeticChars
            val japaneseRatio = (japaneseHiraganaCount + japaneseKatakanaCount).toDouble() / totalAlphabeticChars
            val koreanRatio = koreanCount.toDouble() / totalAlphabeticChars
            
            Logger.d(TAG, "Language detection ratios - Russian: ${russianRatio}, English: ${englishRatio}, " +
                "Chinese: ${chineseRatio}, Japanese: ${japaneseRatio}, Korean: ${koreanRatio}")
            
            // Определяем язык на основе соотношения символов
            val maxRatio = listOf(
                Pair(LANGUAGE_RUSSIAN, russianRatio),
                Pair(LANGUAGE_ENGLISH, englishRatio),
                Pair(LANGUAGE_CHINESE, chineseRatio),
                Pair(LANGUAGE_JAPANESE, japaneseRatio),
                Pair(LANGUAGE_KOREAN, koreanRatio)
            ).maxByOrNull { it.second }?.first ?: LANGUAGE_UNKNOWN
            
            // Если соотношение максимального языка меньше 30%, считаем как неопределенный
            val maxRatioValue = listOf(
                russianRatio,
                englishRatio,
                chineseRatio,
                japaneseRatio,
                koreanRatio
            ).maxOrNull() ?: 0.0
            
            return if (maxRatioValue > 0.3) {
                maxRatio
            } else {
                LANGUAGE_UNKNOWN
            }
            
        } catch (e: Exception) {
            Logger.e(TAG, "Error detecting language", e)
            return LANGUAGE_UNKNOWN
        }
    }

    /**
     * Проверяет, является ли текст русским
     * @param text текст для проверки
     * @return true если текст на русском языке
     */
    fun isRussianText(text: String): Boolean {
        return detectLanguage(text) == LANGUAGE_RUSSIAN
    }

    /**
     * Проверяет, является ли текст английским
     * @param text текст для проверки
     * @return true если текст на английском языке
     */
    fun isEnglishText(text: String): Boolean {
        return detectLanguage(text) == LANGUAGE_ENGLISH
    }

    /**
     * Проверяет, является ли текст китайским
     * @param text текст для проверки
     * @return true если текст на китайском языке
     */
    fun isChineseText(text: String): Boolean {
        return detectLanguage(text) == LANGUAGE_CHINESE
    }

    /**
     * Проверяет, является ли текст японским
     * @param text текст для проверки
     * @return true если текст на японском языке
     */
    fun isJapaneseText(text: String): Boolean {
        return detectLanguage(text) == LANGUAGE_JAPANESE
    }

    /**
     * Проверяет, является ли текст корейским
     * @param text текст для проверки
     * @return true если текст на корейском языке
     */
    fun isKoreanText(text: String): Boolean {
        return detectLanguage(text) == LANGUAGE_KOREAN
    }
}
