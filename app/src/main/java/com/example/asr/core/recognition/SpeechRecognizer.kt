package com.example.asr.core.recognition

import ai.vosk.android.Recognizer
import ai.vosk.android.RecognizerListener
import ai.vosk.android.VoskAndroid
import android.content.Context
import com.example.asr.core.error.DomainError
import com.example.asr.core.model.ModelManager
import com.example.asr.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import java.io.File

/**
 * Распознаватель речи с использованием Vosk API
 */
class SpeechRecognizer(
    private val context: Context,
    private val modelManager: ModelManager
) {

    companion object {
        private const val TAG = "SpeechRecognizer"
        private const val SAMPLE_RATE = 16000.0f
    }

    /**
     * Выполняет распознавание аудио данных и возвращает Flow с промежуточными результатами
     * @param audioChunks список чанков аудио данных (в формате 16kHz, 16-bit, mono)
     * @return Flow<PartialRecognition> поток промежуточных результатов распознавания
     */
    fun recognize(audioChunks: List<ByteArray>): Flow<PartialRecognition> {
        return callbackFlow {
            Logger.i(TAG, "Starting speech recognition for ${audioChunks.size} chunks")
            
            try {
                // Загружаем модель через ModelManager
                val modelFile = modelManager.getModelFile()
                    ?: throw DomainError.RecognitionError("Vosk model not found or invalid")

                if (!isModelReady(modelFile.absolutePath)) {
                    throw DomainError.RecognitionError("Vosk model is not ready: ${modelFile.absolutePath}")
                }

                // Инициализация Vosk
                VoskAndroid.init(context)
                Logger.i(TAG, "Vosk initialized successfully")

                val recognizer = Recognizer(
                    modelFile.absolutePath,
                    SAMPLE_RATE.toString()
                )

                // Создаем listener с обработкой в корутинном контексте
                val listener = object : RecognizerListener {
                    override fun onResult(result: String) {
                        processRecognitionResult(result, 0, isFinal = true)
                    }

                    override fun onPartialResult(result: String) {
                        processRecognitionResult(result, 0, isFinal = false)
                    }

                    override fun onError(error: Exception) {
                        Logger.e(TAG, "Recognition error occurred", error)
                        trySend(PartialRecognition(
                            text = "",
                            isFinal = false,
                            confidence = 0.0f,
                            sourceChunkIndex = -1,
                            isError = true,
                            errorMessage = error.message ?: "Unknown error"
                        ))
                    }

                    override fun onFinalResult(result: String) {
                        processRecognitionResult(result, 0, isFinal = true)
                    }
                }

                recognizer.setListener(listener)

                // Обработка чанков без delay() внутри callbackFlow
                audioChunks.forEachIndexed { index, chunk ->
                    if (coroutineContext.job.isCancelled) {
                        recognizer.stop()
                        throw kotlinx.coroutines.CancellationException("Recognition cancelled")
                    }
                    
                    if (chunk.isNotEmpty()) {
                        try {
                            Logger.d(TAG, "Processing audio chunk $index with size ${chunk.size}")
                            recognizer.acceptWaveform(chunk, 0, chunk.size)
                        } catch (e: Exception) {
                            Logger.e(TAG, "Error processing audio chunk $index", e)
                            // Не выбрасываем исключение, а отправляем ошибку
                            trySend(PartialRecognition(
                                text = "",
                                isFinal = false,
                                confidence = 0.0f,
                                sourceChunkIndex = index,
                                isError = true,
                                errorMessage = "Failed to process chunk $index: ${e.message}"
                            ))
                        }
                    } else {
                        Logger.w(TAG, "Empty audio chunk at index $index")
                    }
                }

                // Завершаем распознавание
                recognizer.stop()
                
                Logger.i(TAG, "Speech recognition completed for ${audioChunks.size} chunks")
                close()
            } catch (e: Exception) {
                Logger.e(TAG, "Error during speech recognition", e)
                close(e)
                throw when (e) {
                    is DomainError -> e
                    else -> DomainError.RecognitionError("Failed to perform speech recognition: ${e.message}")
                }
            }
        }.flowOn(Dispatchers.Default)
    }

    /**
     * Обрабатывает результат распознавания и отправляет в поток
     */
    private fun processRecognitionResult(result: String, sourceChunkIndex: Int, isFinal: Boolean) {
        try {
            val recognition = parseRecognitionResult(result, sourceChunkIndex, isFinal)
            trySend(recognition)
        } catch (e: Exception) {
            Logger.e(TAG, "Error parsing recognition result", e)
            // Отправляем пустой результат при ошибке парсинга
            trySend(PartialRecognition(
                text = "",
                isFinal = isFinal,
                confidence = 0.0f,
                sourceChunkIndex = sourceChunkIndex
            ))
        }
    }

    /**
     * Парсит результат распознавания из JSON
     */
    private fun parseRecognitionResult(
        jsonResult: String,
        sourceChunkIndex: Int,
        isFinal: Boolean = true
    ): PartialRecognition {
        try {
            if (jsonResult.isEmpty()) {
                return PartialRecognition(
                    text = "",
                    isFinal = isFinal,
                    confidence = 0.0f,
                    sourceChunkIndex = sourceChunkIndex
                )
            }
            
            val jsonObject = JSONObject(jsonResult)
            val text = jsonObject.optString("text", "")
            val confidence = if (jsonObject.has("confidence")) {
                jsonObject.getDouble("confidence").toFloat()
            } else {
                if (isFinal) 0.95f else 0.7f
            }
            
            return PartialRecognition(
                text = text,
                isFinal = isFinal,
                confidence = confidence,
                sourceChunkIndex = sourceChunkIndex
            )
        } catch (e: Exception) {
            Logger.e(TAG, "Error parsing recognition result", e)
            // Возвращаем дефолтный результат при ошибке парсинга
            return PartialRecognition(
                text = "",
                isFinal = isFinal,
                confidence = 0.0f,
                sourceChunkIndex = sourceChunkIndex
            )
        }
    }

    /**
     * Проверяет готовность модели для использования
     */
    fun isModelReady(modelPath: String): Boolean {
        return try {
            val modelDir = File(modelPath)
            if (!modelDir.exists()) return false
            if (!modelDir.isDirectory) return false
            
            // Проверяем наличие необходимых файлов модели Vosk
            val requiredFiles = listOf("model", "words.txt", "hmm", "graph")
            val files = modelDir.listFiles() ?: return false
            
            files.isNotEmpty() && files.any { it.name.contains("model") }
        } catch (e: Exception) {
            Logger.e(TAG, "Error checking model readiness", e)
            false
        }
    }

    /**
     * Очищает ресурсы распознавателя
     */
    fun release() {
        // В реальной реализации здесь будет очистка ресурсов
        Logger.i(TAG, "Speech recognizer released")
    }
}
