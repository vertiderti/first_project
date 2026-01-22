package com.example.asr.core.recognition

import ai.vosk.android.Recognizer
import ai.vosk.android.RecognizerListener
import ai.vosk.android.VoskAndroid
import android.content.Context
import com.example.asr.core.error.DomainError
import com.example.asr.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private val modelPath: String
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
                // Проверка готовности модели
                if (!isModelReady()) {
                    throw DomainError.RecognitionError("Vosk model is not ready: $modelPath")
                }

                // Инициализация Vosk
                VoskAndroid.init(context)
                Logger.i(TAG, "Vosk initialized successfully")

                val modelDir = File(modelPath)
                val recognizer = Recognizer(
                    modelDir.absolutePath,
                    SAMPLE_RATE.toString()
                )

                // Устанавливаем слушатель для получения результатов
                recognizer.setListener(object : RecognizerListener {
                    override fun onResult(result: String) {
                        Logger.d(TAG, "Received result: $result")
                        
                        try {
                            val recognition = parseRecognitionResult(result, 0)
                            trySend(recognition)
                        } catch (e: Exception) {
                            Logger.e(TAG, "Error parsing recognition result", e)
                        }
                    }

                    override fun onPartialResult(result: String) {
                        Logger.d(TAG, "Received partial result: $result")
                        
                        try {
                            val recognition = parseRecognitionResult(result, 0, isFinal = false)
                            trySend(recognition)
                        } catch (e: Exception) {
                            Logger.e(TAG, "Error parsing partial recognition result", e)
                        }
                    }

                    override fun onError(error: Exception) {
                        Logger.e(TAG, "Recognition error occurred", error)
                        // Отправляем ошибку в поток
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
                        Logger.d(TAG, "Received final result: $result")
                        
                        try {
                            val recognition = parseRecognitionResult(result, 0, isFinal = true)
                            trySend(recognition)
                        } catch (e: Exception) {
                            Logger.e(TAG, "Error parsing final recognition result", e)
                        }
                    }
                })

                // Обработка каждого чанка с проверкой отмены
                audioChunks.forEachIndexed { index, chunk ->
                    // Проверка отмены перед каждым чанком
                    if (coroutineContext.job.isCancelled) {
                        recognizer.stop()
                        throw kotlinx.coroutines.CancellationException("Recognition cancelled")
                    }
                    
                    // Пропуск пустых чанков
                    if (chunk.isEmpty()) {
                        Logger.w(TAG, "Empty audio chunk at index $index")
                        return@forEachIndexed
                    }

                    try {
                        // Передаем чанк в распознаватель
                        recognizer.acceptWaveform(chunk, 0, chunk.size)
                        
                        // Асинхронная задержка вместо блокирующей Thread.sleep()
                        delay(100)
                    } catch (e: Exception) {
                        Logger.e(TAG, "Error processing audio chunk $index", e)
                        throw DomainError.RecognitionError("Failed to process audio chunk $index: ${e.message}")
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
    fun isModelReady(): Boolean {
        val modelDir = File(modelPath)
        return modelDir.exists() && 
               modelDir.isDirectory && 
               modelDir.listFiles()?.isNotEmpty() == true
    }

    /**
     * Очищает ресурсы распознавателя
     */
    fun release() {
        // В реальной реализации здесь будет очистка ресурсов
        Logger.i(TAG, "Speech recognizer released")
    }
}
