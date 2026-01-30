package com.example.asr.core.recognition

/**
 * Промежуточный результат распознавания речи
 * @param text текст распознанный в текущем чанке
 * @param isFinal флаг, указывающий является ли результат финальным
 * @param confidence уровень уверенности модели (от 0.0 до 1.0), может быть null
 * @param sourceChunkIndex индекс чанка, из которого получен результат
 * @param isError флаг ошибки (если true, то text пустой и есть errorMessage)
 * @param errorMessage текст ошибки (если isError=true)
 */
data class PartialRecognition(
    val text: String,
    val isFinal: Boolean,
    val confidence: Float?,
    val sourceChunkIndex: Int,
    val isError: Boolean = false,
    val errorMessage: String? = null
) {
    /**
     * Возвращает уровень уверенности в виде процента (0-100)
     * Если confidence == null, возвращает 0%
     */
    fun getConfidencePercentage(): Int {
        return (confidence ?: 0f).times(100).toInt()
    }
    
    /**
     * Проверяет, является ли результат пустым
     */
    fun isEmpty(): Boolean {
        return text.isBlank() && !isError
    }
    
    /**
     * Проверяет, является ли результат ошибкой
     */
    fun isErrorResult(): Boolean {
        return isError
    }
}
