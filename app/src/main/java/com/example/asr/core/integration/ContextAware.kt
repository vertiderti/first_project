package com.example.asr.core.integration

import android.content.Context

/**
 * Интерфейс для адаптеров, которые требуют контекста
 */
interface ContextAware {
    /**
     * Устанавливает контекст для адаптера
     * @param context контекст приложения
     */
    fun setContext(context: Context)
}
