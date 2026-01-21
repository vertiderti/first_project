package com.example.asr.core.coordinator

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.example.asr.core.error.DomainError
import com.example.asr.core.pipeline.RecognitionPipeline
import com.example.asr.core.utils.Logger
import java.util.concurrent.Semaphore
import kotlin.time.Duration.Companion.seconds

/**
 * Координатор распознавания, управляющий выполнением задач и ограничивающий concurrency
 */
class RecognitionCoordinator(
    private val recognitionPipelines: List<RecognitionPipeline>,
    private val maxConcurrentTasks: Int = 3,
    private val retryAttempts: Int = 3,
    private val timeoutSeconds: Long = 30L
) {

    companion object {
        private const val TAG = "RecognitionCoordinator"
        private const val DEFAULT_TIMEOUT_SECONDS = 30L
        private const val DEFAULT_RETRY_ATTEMPTS = 3
    }

    private val semaphore = Semaphore(maxConcurrentTasks)
    private val handler = Handler(Looper.getMainLooper())
    private val _events = MutableSharedFlow<CoordinatorEvent>()
    val events: SharedFlow<CoordinatorEvent> = _events.asSharedFlow()
    
    // Для отслеживания активных задач
    private val activeTasks = mutableSetOf<Job>()
    private val taskLock = Any() // Для синхронизации доступа к activeTasks
    
    init {
        // Проверяем, что список pipeline не пустой
        if (recognitionPipelines.isEmpty()) {
            Logger.w(TAG, "Warning: recognitionPipelines is empty")
        }
    }

    /**
     * Запускает обработку аудиофайла с координацией
     */
    fun startRecognition(
        audioUri: String,
        onSuccess: (String) -> Unit = {},
        onError: (DomainError) -> Unit = {}
    ) {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                // Проверяем, что есть доступные pipeline
                if (recognitionPipelines.isEmpty()) {
                    val error = DomainError.CoordinatorError("No recognition pipelines available")
                    onError(error)
                    _events.emit(CoordinatorEvent.Error(audioUri, error))
                    return@launch
                }

                // Ожидаем освобождения семафора
                semaphore.acquire()
                
                // Создаем задачу для обработки
                val taskJob = GlobalScope.launch(Dispatchers.IO) {
                    executeRecognitionWithRetry(
                        audioUri,
                        onSuccess,
                        onError
                    )
                }
                
                // Добавляем задачу в список активных с синхронизацией
                synchronized(taskLock) {
                    activeTasks.add(taskJob)
                }
                
                // Регистрируем завершение задачи
                taskJob.invokeOnCompletion {
                    try {
                        semaphore.release()
                        synchronized(taskLock) {
                            activeTasks.remove(taskJob)
                        }
                    } catch (e: Exception) {
                        Logger.e(TAG, "Error releasing semaphore", e)
                    }
                }
                
            } catch (e: Exception) {
                Logger.e(TAG, "Error starting recognition task", e)
                val error = DomainError.CoordinatorError("Failed to start recognition task")
                onError(error)
                _events.emit(CoordinatorEvent.Error(audioUri, error))
                // Если не удалось запустить задачу, освобождаем семафор
                try {
                    semaphore.release()
                } catch (releaseException: Exception) {
                    Logger.e(TAG, "Error releasing semaphore after failure", releaseException)
                }
            }
        }
    }

    /**
     * Выполняет обработку с механизмом повторных попыток
     */
    private suspend fun executeRecognitionWithRetry(
        audioUri: String,
        onSuccess: (String) -> Unit,
        onError: (DomainError) -> Unit
    ) {
        var lastError: DomainError? = null
        
        repeat(retryAttempts) { attempt ->
            try {
                Logger.i(TAG, "Starting recognition attempt $attempt for $audioUri")
                
                // Запускаем таймер ожидания
                val timeoutJob = GlobalScope.launch(Dispatchers.IO) {
                    delay(timeoutSeconds.seconds)
                    throw DomainError.TimeoutError("Recognition timed out after ${timeoutSeconds} seconds")
                }
                
                // Выполняем обработку с выбором pipeline
                val result = executeRecognition(audioUri)
                
                // Отменяем таймер
                timeoutJob.cancel()
                
                // Успешное завершение
                Logger.i(TAG, "Recognition completed successfully for $audioUri")
                onSuccess(result)
                _events.emit(CoordinatorEvent.Success(audioUri))
                return@repeat
                
            } catch (e: Exception) {
                lastError = when (e) {
                    is DomainError -> e
                    else -> DomainError.UnknownError(e.message ?: "Unknown error occurred")
                }
                
                Logger.w(TAG, "Recognition attempt $attempt failed for $audioUri: ${e.message}")
                
                // Если это последняя попытка, передаем ошибку
                if (attempt == retryAttempts - 1) {
                    Logger.e(TAG, "All retry attempts failed for $audioUri")
                    onError(lastError)
                    _events.emit(CoordinatorEvent.Error(audioUri, lastError))
                } else {
                    // Ждем перед следующей попыткой
                    delay(1.seconds)
                }
            }
        }
    }

    /**
     * Выполняет одну итерацию обработки с оптимальным выбором pipeline
     */
    private suspend fun executeRecognition(audioUri: String): String {
        // Оптимизированный выбор pipeline
        val pipeline = selectOptimalPipeline()
        
        return withTimeout(timeoutSeconds.seconds) {
            pipeline.process(audioUri)
        }
    }

    /**
     * Выбирает оптимальный pipeline для обработки
     */
    private fun selectOptimalPipeline(): RecognitionPipeline {
        // Проверяем, что список не пустой
        if (recognitionPipelines.isEmpty()) {
            throw DomainError.CoordinatorError("No recognition pipelines available")
        }
        
        // Простая стратегия: возвращаем первый доступный pipeline
        // Можно улучшить до более сложной логики (например, по производительности, нагрузке и т.д.)
        return recognitionPipelines.firstOrNull() ?: recognitionPipelines[0]
    }

    /**
     * Отменяет все активные задачи
     */
    fun cancelAllTasks() {
        GlobalScope.launch(Dispatchers.Main) {
            synchronized(taskLock) {
                activeTasks.forEach { job ->
                    if (!job.isCancelled) {
                        job.cancel()
                    }
                }
                activeTasks.clear()
            }
            
            // Освобождаем семафор для всех заблокированных задач
            repeat(maxConcurrentTasks) {
                try {
                    semaphore.release()
                } catch (e: Exception) {
                    Logger.e(TAG, "Error releasing semaphore during cancel", e)
                }
            }
            
            _events.emit(CoordinatorEvent.CancelledAll)
        }
    }

    /**
     * Проверяет, есть ли активные задачи
     */
    fun hasActiveTasks(): Boolean {
        synchronized(taskLock) {
            return activeTasks.any { !it.isCancelled && !it.isCompleted }
        }
    }

    /**
     * Ожидает завершения всех активных задач
     */
    suspend fun waitForAllTasks() {
        synchronized(taskLock) {
            val jobs = activeTasks.toList()
            jobs.forEach { job ->
                if (!job.isCompleted) {
                    try {
                        job.join()
                    } catch (e: Exception) {
                        Logger.e(TAG, "Error waiting for task completion", e)
                    }
                }
            }
        }
    }

    /**
     * События координатора
     */
    sealed class CoordinatorEvent {
        data class Success(val audioUri: String) : CoordinatorEvent()
        data class Error(val audioUri: String, val error: DomainError) : CoordinatorEvent()
        object CancelledAll : CoordinatorEvent()
    }
}
