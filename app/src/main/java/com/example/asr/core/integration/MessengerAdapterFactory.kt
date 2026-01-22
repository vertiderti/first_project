package com.example.asr.core.integration

import android.content.Context
import android.content.Intent

/**
 * Фабричный класс для создания адаптеров мессенджеров
 * Позволяет регистрировать новые адаптеры и выбирать подходящий
 */
object MessengerAdapterFactory {
    
    private val adapters = mutableMapOf<String, MessengerAdapter>()
    
    init {
        // Регистрируем стандартные адаптеры без контекста
        registerAdapter("telegram", TelegramAdapter())
        registerAdapter("whatsapp", WhatsAppAdapter())
        registerAdapter("vk", VKAdapter())
    }
    
    /**
     * Регистрирует новый адаптер для мессенджера
     * @param name имя мессенджера
     * @param adapter экземпляр адаптера
     */
    fun registerAdapter(name: String, adapter: MessengerAdapter) {
        adapters[name.lowercase()] = adapter
    }
    
    /**
     * Получает подходящий адаптер для интента с переданным контекстом
     */
    fun getAdapterForIntent(intent: Intent, context: Context): MessengerAdapter? {
        // Сначала пробуем найти по имени пакета
        val packageName = intent.`package`?.toString()
        if (!packageName.isNullOrEmpty()) {
            for ((name, adapter) in adapters) {
                if (packageName.contains(name, ignoreCase = true)) {
                    // Устанавливаем контекст для адаптера если он поддерживает это
                    if (adapter is ContextAware) {
                        (adapter as ContextAware).setContext(context)
                    }
                    return adapter
                }
            }
        }
        
        // Если не нашли по пакету, пробуем через проверку интента
        for ((_, adapter) in adapters) {
            if (adapter.supportsIntent(intent)) {
                if (adapter is ContextAware) {
                    (adapter as ContextAware).setContext(context)
                }
                return adapter
            }
        }
        
        return null
    }
    
    /**
     * Получает адаптер по имени
     * @param name имя мессенджера
     * @return адаптер или null если не найден
     */
    fun getAdapter(name: String): MessengerAdapter? {
        return adapters[name.lowercase()]
    }
    
    /**
     * Возвращает список всех зарегистрированных адаптеров
     */
    fun getAllAdapters(): List<MessengerAdapter> {
        return adapters.values.toList()
    }
    
    /**
     * Получает доступный адаптер для интента с переданным контекстом
     */
    fun getAvailableAdapterForIntent(intent: Intent, context: Context): MessengerAdapter? {
        val adapter = getAdapterForIntent(intent, context)
        return if (adapter?.isAvailable() == true) adapter else null
    }
}
