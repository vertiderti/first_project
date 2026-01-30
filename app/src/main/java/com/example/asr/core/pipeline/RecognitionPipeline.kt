package com.example.asr.core.pipeline

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.example.asr.core.audio.AudioLoader
import com.example.asr.core.audio.Chunker
import com.example.asr.core.recognition.SpeechRecognizer
import com.example.asr.core.postprocess.PostProcessor
import com.example.asr.core.error.DomainError
import com.example.asr.core.utils.Logger
import kotlin.time.Duration.Companion.seconds

/**
 * Пайплайн распознавания аудио, реализующий поток обработки с событиями жизненного цикла
 */
class RecognitionPipeline(
    private val audioLoader: AudioLoader,
    private val chunker: Chunker,
    private val speechRecognizer: SpeechRecognizer,
    private val postProcessor: PostProcessor,
    private val timeoutSeconds: Long = 30L
) {

    companion object {
        private const val TAG = "RecognitionPipeline"
    }

    /**
     * Состояния жизненного цикла пайплайна
     */
    sealed class PipelineState {
        object Started : PipelineState()
        data class Progress(val percentage: Int) : PipelineState()
        data class ChunkRecognized(
            val chunkIndex: Int,
            val text: String,
            val isFinal: Boolean
        ) : PipelineState()
        
        object Completed : PipelineState()
        object Cancelled : PipelineState()
        data class Failed(val error: DomainError) : PipelineState()
    }

    /**
     * Запускает процесс распознавания аудио и возвращает Flow с событиями
     */
    fun process(audioUri: String): Flow<PipelineState> {
        return flow {
            try {
                emit(PipelineState.Started)
                
                // Загружаем аудио
                val audioData = audioLoader.loadAudio(audioUri)
                emit(PipelineState.Progress(10))
                
                // Разбиваем на чанки
                val chunks = chunker.chunkAudio(audioData)
                emit(PipelineState.Progress(20))
                
                val results = mutableListOf<String>()
                
                // Обрабатываем каждый чанк
                chunks.forEachIndexed { index, chunk ->
                    // Проверяем, не отменена ли операция
                    if (currentCoroutineContext().job.isCancelled) {
                        emit(PipelineState.Cancelled)
                        return@flow
                    }
                    
                    try {
                        // Распознаем чанк
                        val recognizedText = speechRecognizer.recognize(chunk)
                        
                        // Обрабатываем результат
                        val processedText = postProcessor.process(recognizedText)
                        
                        results.add(processedText)
                        
                        // Отправляем событие о распознанном чанке
                        emit(PipelineState.ChunkRecognized(
                            chunkIndex = index,
                            text = processedText,
                            isFinal = false
                        ))
                        
                        // Обновляем прогресс (примерная реализация)
                        val progressPercentage = 20 + (index * 80) / chunks.size
                        emit(PipelineState.Progress(progressPercentage))
                        
                    } catch (e: Exception) {
                        val error = when (e) {
                            is DomainError -> e
                            else -> DomainError.RecognitionError("Recognition failed for chunk $index: ${e.message}")
                        }
                        emit(PipelineState.Failed(error))
                        return@flow
                    }
                }
                
                // Финальная обработка и объединение результатов
                val finalResult = results.joinToString(" ")
                emit(PipelineState.ChunkRecognized(
                    chunkIndex = -1,
                    text = finalResult,
                    isFinal = true
                ))
                
                emit(PipelineState.Completed)
                
            } catch (e: CancellationException) {
                // Обработка отмены операции
                Logger.i(TAG, "Recognition process was cancelled")
                emit(PipelineState.Cancelled)
                throw e // Передаем исключение дальше для корректной обработки
            } catch (e: Exception) {
                // Обработка других ошибок
                val error = when (e) {
                    is DomainError -> e
                    else -> DomainError.RecognitionError("Recognition process failed: ${e.message}")
                }
                Logger.e(TAG, "Recognition process failed", e)
                emit(PipelineState.Failed(error))
                throw e // Передаем исключение дальше для корректной обработки
            }
        }
    }

    /**
     * Асинхронная версия обработки с таймаутом
     */
    suspend fun processWithTimeout(audioUri: String): String {
        return withTimeout(timeoutSeconds.seconds) {
            val results = mutableListOf<String>()
            var finalResultText = ""
            
            // Создаем Flow и собираем все состояния
            val pipelineFlow = process(audioUri)
            
            // Собираем все состояния и извлекаем финальный результат
            pipelineFlow.collect { state ->
                when (state) {
                    is PipelineState.ChunkRecognized -> {
                        if (state.isFinal) {
                            finalResultText = state.text
                        } else {
                            results.add(state.text)
                        }
                    }
                    is PipelineState.Failed -> {
                        throw state.error
                    }
                    is PipelineState.Cancelled -> {
                        throw CancellationException("Recognition was cancelled")
                    }
                    else -> {
                        // Игнорируем другие состояния
                    }
                }
            }
            
            // Возвращаем финальный результат или объединенные промежуточные результаты
            return@withTimeout finalResultText.ifEmpty { results.joinToString(" ") }
        }
    }

    /**
     * Асинхронная обработка с отслеживанием прогресса и возможностью отмены
     */
    suspend fun processWithProgress(
        audioUri: String,
        onProgress: (Int) -> Unit = {},
        onComplete: (String) -> Unit = {},
        onError: (DomainError) -> Unit = {}
    ): String {
        val results = mutableListOf<String>()
        var finalResultText = ""
        
        return try {
            // Создаем Flow с событиями
            val pipelineFlow = process(audioUri)
            
            // Собираем все состояния
            pipelineFlow.collect { state ->
                when (state) {
                    is PipelineState.Progress -> {
                        onProgress(state.percentage)
                    }
                    is PipelineState.ChunkRecognized -> {
                        if (state.isFinal) {
                            finalResultText = state.text
                            onComplete(state.text)
                        } else {
                            results.add(state.text)
                        }
                    }
                    is PipelineState.Failed -> {
                        onError(state.error)
                        throw state.error
                    }
                    is PipelineState.Cancelled -> {
                        val cancellationException = CancellationException("Recognition was cancelled")
                        onError(DomainError.CancelledError("Recognition was cancelled"))
                        throw cancellationException
                    }
                    else -> {
                        // Другие состояния игнорируются
                    }
                }
            }
            
            // Возвращаем финальный результат
            return finalResultText.ifEmpty { results.joinToString(" ") }
        } catch (e: CancellationException) {
            Logger.i(TAG, "Recognition process was cancelled")
            throw e
        } catch (e: Exception) {
            val error = when (e) {
                is DomainError -> e
                else -> DomainError.RecognitionError("Recognition process failed: ${e.message}")
            }
            onError(error)
            throw e
        }
    }
}
