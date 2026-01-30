package com.example.asr.core.integration

import android.content.Intent
import java.io.InputStream

/**
 * Интерфейс адаптера для интеграции с различными мессенджерами
 * Позволяет извлекать аудио поток из разных источников
 */
interface MessengerAdapter {
    
    /**
     * Извлекает аудио поток из интента мессенджера
     * @param intent интент, содержащий данные о сообщении
     * @return InputStream с аудио данными или null если не удалось извлечь
     */
    fun getAudioStream(intent: Intent): InputStream?
    
    /**
     * Проверяет, поддерживает ли адаптер данный тип интента
     * @param intent интент для проверки
     * @return true если адаптер может обработать этот интент
     */
    fun supportsIntent(intent: Intent): Boolean
    
    /**
     * Проверяет, доступен ли мессенджер (установлен ли он на устройстве)
     * @return true если мессенджер установлен и доступен
     */
    fun isAvailable(): Boolean
}
