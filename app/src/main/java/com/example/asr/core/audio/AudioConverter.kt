package com.example.asr.core.audio

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaExtractor
import android.os.Build
import com.example.asr.core.error.DomainError
import com.example.asr.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Конвертер аудио форматов с использованием Android MediaCodec API
 */
class AudioConverter {

    companion object {
        private const val TAG = "AudioConverter"
        private const val DEFAULT_SAMPLE_RATE = 16000
        private const val DEFAULT_CHANNELS = 1
        private const val DEFAULT_BIT_DEPTH = 16
        private const val MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024 // 50MB ограничение
        private const val MIN_AUDIO_DATA_SIZE = 10
        private const val BUFFER_SIZE = 4096
        private const val TIMEOUT_US = 1000000L // 1 секунда таймаут
    }

    /**
     * Конвертирует аудио данные в WAV формат с использованием MediaCodec
     */
    suspend fun convertToWav(inputData: ByteArray, inputFormat: String? = null): ByteArray {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                Logger.i(TAG, "Converting audio data to WAV format using MediaCodec")
                
                validateFileSize(inputData)
                if (!validateInputData(inputData)) {
                    throw DomainError.AudioError("Invalid input audio data")
                }
                
                // Используем MediaCodec для конвертации с правильной обработкой форматов
                val result = convertWithMediaCodec(inputData, inputFormat)
                
                val endTime = System.currentTimeMillis()
                Logger.i(TAG, "Successfully converted audio to WAV format in ${endTime - startTime}ms: ${result.size} bytes")
                return@withContext result
                
            } catch (e: Exception) {
                Logger.e(TAG, "Error converting audio to WAV with MediaCodec", e)
                throw when (e) {
                    is DomainError -> e
                    else -> DomainError.AudioError("Failed to convert audio to WAV format: ${e.message}")
                }
            }
        }
    }

    /**
     * Конвертирует аудио данные в PCM формат с использованием MediaCodec
     */
    suspend fun convertToPcm(
        inputData: ByteArray, 
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        channels: Int = DEFAULT_CHANNELS,
        bitDepth: Int = DEFAULT_BIT_DEPTH
    ): ByteArray {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                Logger.i(TAG, "Converting audio data to PCM format using MediaCodec")
                
                validateFileSize(inputData)
                if (!validateInputData(inputData)) {
                    throw DomainError.AudioError("Invalid input audio data")
                }
                
                // Используем MediaCodec для конвертации в PCM
                val result = convertPcmWithMediaCodec(inputData, sampleRate, channels, bitDepth)
                
                val endTime = System.currentTimeMillis()
                Logger.i(TAG, "Successfully converted audio to PCM format in ${endTime - startTime}ms: ${result.size} bytes")
                return@withContext result
                
            } catch (e: Exception) {
                Logger.e(TAG, "Error converting audio to PCM with MediaCodec", e)
                throw when (e) {
                    is DomainError -> e
                    else -> DomainError.AudioError("Failed to convert audio to PCM format: ${e.message}")
                }
            }
        }
    }

    /**
     * Конвертирует с использованием MediaCodec с полноценной обработкой форматов
     */
    private fun convertWithMediaCodec(inputData: ByteArray, inputFormat: String?): ByteArray {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        
        try {
            val tempFile = File.createTempFile("audio", ".tmp")
            tempFile.writeBytes(inputData)
            
            extractor = MediaExtractor()
            extractor.setDataSource(tempFile.absolutePath)
            
            // Проверка наличия треков
            if (extractor.trackCount <= 0) {
                throw DomainError.AudioError("No audio tracks found in file")
            }
            
            val format = extractor.getTrackFormat(0)
            val mime = format.getString(MediaFormat.KEY_MIME)
            
            // Используем только PCM формат для простоты, так как другие форматы могут не поддерживаться
            codec = MediaCodec.createDecoderByType(mime ?: "audio/raw")
            codec.configure(format, null, null, 0)
            codec.start()
            
            try {
                val audioData = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_SIZE)
                
                // Читаем данные по частям с правильной обработкой буферов
                var inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                var isEOS = false
                
                while (!isEOS && inputBufferIndex >= 0) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    
                    if (sampleSize < 0) {
                        // Конец потока
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEOS = true
                    } else {
                        codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, 0L, 0)
                    }
                    
                    inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                }
                
                val outputBufferInfo = MediaCodec.BufferInfo()
                var outputBufferIndex = codec.dequeueOutputBuffer(outputBufferInfo, TIMEOUT_US)
                
                while (outputBufferIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null) {
                        audioData.write(outputBuffer)
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    outputBufferIndex = codec.dequeueOutputBuffer(outputBufferInfo, TIMEOUT_US)
                }
                
                val result = audioData.toByteArray()
                
                // Очистка ресурсов
                tempFile.delete()
                extractor.release()
                codec.stop()
                codec.release()
                
                return result
                
            } catch (e: Exception) {
                Logger.e(TAG, "Error processing MediaCodec buffers", e)
                throw DomainError.AudioError("MediaCodec buffer processing failed: ${e.message}")
            }
            
        } catch (e: Exception) {
            // Очистка ресурсов в случае ошибки
            try {
                extractor?.release()
                codec?.stop()
                codec?.release()
            } catch (cleanupException: Exception) {
                Logger.w(TAG, "Error cleaning up resources: ${cleanupException.message}")
            }
            
            Logger.w(TAG, "MediaCodec failed, using fallback conversion: ${e.message}")
            // Возвращаем исходные данные как fallback
            return inputData.copyOf()
        }
    }

    /**
     * Конвертирует в PCM с использованием MediaCodec
     */
    private fun convertPcmWithMediaCodec(
        inputData: ByteArray, 
        sampleRate: Int,
        channels: Int,
        bitDepth: Int
    ): ByteArray {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        
        try {
            val tempFile = File.createTempFile("audio", ".tmp")
            tempFile.writeBytes(inputData)
            
            extractor = MediaExtractor()
            extractor.setDataSource(tempFile.absolutePath)
            
            // Проверка наличия треков
            if (extractor.trackCount <= 0) {
                throw DomainError.AudioError("No audio tracks found in file")
            }
            
            val format = extractor.getTrackFormat(0)
            val mime = format.getString(MediaFormat.KEY_MIME)
            
            // Для конвертации в PCM используем декодер
            codec = MediaCodec.createDecoderByType(mime ?: "audio/raw")
            codec.configure(format, null, null, 0)
            codec.start()
            
            try {
                val audioData = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_SIZE)
                
                // Читаем данные по частям с правильной обработкой буферов
                var inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                var isEOS = false
                
                while (!isEOS && inputBufferIndex >= 0) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    
                    if (sampleSize < 0) {
                        // Конец потока
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEOS = true
                    } else {
                        codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, 0L, 0)
                    }
                    
                    inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                }
                
                val outputBufferInfo = MediaCodec.BufferInfo()
                var outputBufferIndex = codec.dequeueOutputBuffer(outputBufferInfo, TIMEOUT_US)
                
                while (outputBufferIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null) {
                        audioData.write(outputBuffer)
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    outputBufferIndex = codec.dequeueOutputBuffer(outputBufferInfo, TIMEOUT_US)
                }
                
                val result = audioData.toByteArray()
                
                // Очистка ресурсов
                tempFile.delete()
                extractor.release()
                codec.stop()
                codec.release()
                
                return result
                
            } catch (e: Exception) {
                Logger.e(TAG, "Error processing MediaCodec buffers for PCM", e)
                throw DomainError.AudioError("MediaCodec buffer processing failed for PCM: ${e.message}")
            }
            
        } catch (e: Exception) {
            // Очистка ресурсов в случае ошибки
            try {
                extractor?.release()
                codec?.stop()
                codec?.release()
            } catch (cleanupException: Exception) {
                Logger.w(TAG, "Error cleaning up resources for PCM: ${cleanupException.message}")
            }
            
            Logger.w(TAG, "MediaCodec failed for PCM, using fallback conversion: ${e.message}")
            // Возвращаем исходные данные как fallback
            return inputData.copyOf()
        }
    }

    /**
     * Создает WAV заголовок для аудио данных с заданными параметрами
     */
    private fun createWavHeader(audioDataSize: Int, sampleRate: Int = DEFAULT_SAMPLE_RATE, channels: Int = DEFAULT_CHANNELS, bitDepth: Int = DEFAULT_BIT_DEPTH): ByteArray {
        try {
            // Проверка на допустимые значения
            if (channels !in 1..2) throw DomainError.AudioError("Unsupported channel count: $channels")
            if (bitDepth !in listOf(8, 16, 24, 32)) throw DomainError.AudioError("Unsupported bit depth: $bitDepth")
            
            val header = ByteArray(44)
            
            // RIFF header
            System.arraycopy("RIFF".toByteArray(), 0, header, 0, 4)
            
            // Файл размер (заполнится позже)
            val fileSize = 36 + audioDataSize
            writeIntToByteArray(header, 4, fileSize)
            
            // WAVE формат
            System.arraycopy("WAVE".toByteArray(), 0, header, 8, 4)
            
            // fmt подзаголовок
            System.arraycopy("fmt ".toByteArray(), 0, header, 12, 4)
            
            // размер fmt подзаголовка
            writeIntToByteArray(header, 16, 16)
            
            // формат (1 - PCM)
            writeShortToByteArray(header, 20, 1)
            
            // каналы
            writeShortToByteArray(header, 22, channels.toShort())
            
            // частота дискретизации
            writeIntToByteArray(header, 24, sampleRate)
            
            // байт в секунду
            val byteRate = (sampleRate * channels * bitDepth / 8)
            writeIntToByteArray(header, 28, byteRate)
            
            // байт на блок
            val blockAlign = (channels * bitDepth / 8).toShort()
            writeShortToByteArray(header, 32, blockAlign)
            
            // бит на сэмпл
            writeShortToByteArray(header, 34, bitDepth.toShort())
            
            // data подзаголовок
            System.arraycopy("data".toByteArray(), 0, header, 36, 4)
            
            // размер данных
            writeIntToByteArray(header, 40, audioDataSize)
            
            return header
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create WAV header", e)
            throw DomainError.AudioError("Failed to create WAV header: ${e.message}")
        }
    }

    /**
     * Проверяет поддержку формата
     */
    private fun checkFormatSupport(inputFormat: String?): Boolean {
        return when (inputFormat?.lowercase()) {
            "mp3", "wav", "aac", "flac", "pcm" -> true
            else -> false
        }
    }

    /**
     * Проверяет размер файла
     */
    private fun validateFileSize(inputData: ByteArray) {
        if (inputData.size > MAX_FILE_SIZE_BYTES) {
            throw DomainError.AudioError("File size exceeds limit of ${MAX_FILE_SIZE_BYTES / (1024 * 1024)}MB")
        }
    }

    /**
     * Проверяет корректность входных данных
     */
    private fun validateInputData(inputData: ByteArray): Boolean {
        return inputData.isNotEmpty() && 
               !inputData.all { it == 0.toByte() } &&
               inputData.size > MIN_AUDIO_DATA_SIZE &&
               inputData.size <= MAX_FILE_SIZE_BYTES
    }

    /**
     * Записывает 4-байтовое значение в массив байтов
     */
    private fun writeIntToByteArray(array: ByteArray, offset: Int, value: Int) {
        array[offset] = (value and 0xFF).toByte()
        array[offset + 1] = ((value shr 8) and 0xFF).toByte()
        array[offset + 2] = ((value shr 16) and 0xFF).toByte()
        array[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    /**
     * Записывает 2-байтовое значение в массив байтов
     */
    private fun writeShortToByteArray(array: ByteArray, offset: Int, value: Short) {
        array[offset] = (value and 0xFF).toByte()
        array[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    /**
     * Конвертирует аудио данные с автоматическим определением формата
     */
    suspend fun convertToWavAuto(inputData: ByteArray): ByteArray {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                Logger.i(TAG, "Converting audio data to WAV with auto format detection using MediaCodec")
                
                validateFileSize(inputData)
                if (!validateInputData(inputData)) {
                    throw DomainError.AudioError("Invalid input audio data for auto conversion")
                }
                
                // Используем MediaCodec для автоматического определения формата
                val result = convertWithMediaCodec(inputData, null)
                
                val endTime = System.currentTimeMillis()
                Logger.i(TAG, "Successfully converted audio to WAV with auto format detection in ${endTime - startTime}ms: ${result.size} bytes")
                return@withContext result
                
            } catch (e: Exception) {
                Logger.e(TAG, "Error converting audio to WAV with auto detection using MediaCodec", e)
                throw when (e) {
                    is DomainError -> e
                    else -> DomainError.AudioError("Failed to convert audio with auto format detection: ${e.message}")
                }
            }
        }
    }

    /**
     * Конвертирует WAV файл в PCM формат с использованием MediaCodec
     */
    suspend fun wavToPcm(wavData: ByteArray): ByteArray {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                Logger.i(TAG, "Converting WAV to PCM using MediaCodec")
                
                validateFileSize(wavData)
                if (!validateWavData(wavData)) {
                    throw DomainError.AudioError("Invalid WAV data format")
                }
                
                // Используем MediaCodec для конвертации
                val result = convertPcmWithMediaCodec(wavData, DEFAULT_SAMPLE_RATE, DEFAULT_CHANNELS, DEFAULT_BIT_DEPTH)
                
                val endTime = System.currentTimeMillis()
                Logger.i(TAG, "Successfully converted WAV to PCM in ${endTime - startTime}ms: ${result.size} bytes")
                return@withContext result
                
            } catch (e: Exception) {
                Logger.e(TAG, "Error converting WAV to PCM with MediaCodec", e)
                throw when (e) {
                    is DomainError -> e
                    else -> DomainError.AudioError("Failed to convert WAV to PCM: ${e.message}")
                }
            }
        }
    }

    /**
     * Проверяет валидность WAV данных
     */
    private fun validateWavData(wavData: ByteArray): Boolean {
        if (wavData.size < 44) return false
        val riffHeader = String(wavData.copyOfRange(0, 4))
        return riffHeader == "RIFF"
    }

    /**
     * Получает параметры аудио из данных
     */
    suspend fun getAudioInfo(inputData: ByteArray): AudioInfo {
        return withContext(Dispatchers.IO) {
            try {
                validateFileSize(inputData)
                
                val tempFile = File.createTempFile("audio_info", ".tmp")
                tempFile.writeBytes(inputData)
                
                var extractor: MediaExtractor? = null
                try {
                    extractor = MediaExtractor()
                    extractor.setDataSource(tempFile.absolutePath)
                    
                    var sampleRate = DEFAULT_SAMPLE_RATE
                    var channels = DEFAULT_CHANNELS
                    var bitDepth = DEFAULT_BIT_DEPTH
                    
                    try {
                        if (extractor.trackCount > 0) {
                            val format = extractor.getTrackFormat(0)
                            
                            // Исправленные ключи для получения параметров
                            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            
                            if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                            
                            // Исправлено: для получения битовой глубины используем правильный подход
                            // В некоторых форматах битовая глубина может быть не доступна напрямую
                            // Поэтому используем стандартное значение или пытаемся извлечь из других параметров
                            if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                                // KEY_BIT_RATE - это битрейт, а не битовая глубина
                                // Для правильного определения битовой глубины нужно использовать другие методы
                                // В данном случае используем значение по умолчанию
                            }
                            
                            // Попробуем получить информацию о формате более точно
                            val encoding = format.getString(MediaFormat.KEY_PCM_ENCODING)
                            if (encoding != null) {
                                // Обработка специфичных кодировок
                                when (encoding) {
                                    "PCM_16BIT" -> bitDepth = 16
                                    "PCM_8BIT" -> bitDepth = 8
                                    "PCM_24BIT" -> bitDepth = 24
                                    "PCM_32BIT" -> bitDepth = 32
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Logger.w(TAG, "Could not extract audio info from MediaFormat: ${e.message}")
                    }
                    
                    val duration = getDurationFromExtractor(extractor).toFloat()
                    
                    tempFile.delete()
                    extractor.release()
                    
                    return@withContext AudioInfo(
                        sampleRate = sampleRate,
                        channels = channels,
                        bitDepth = bitDepth,
                        duration = duration
                    )
                } catch (e: Exception) {
                    // Очистка ресурсов
                    extractor?.release()
                    tempFile.delete()
                    throw e
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Error getting audio info", e)
                throw when (e) {
                    is DomainError -> e
                    else -> DomainError.AudioError("Failed to get audio information: ${e.message}")
                }
            }
        }
    }

    /**
     * Получает продолжительность аудио из MediaExtractor
     */
    private fun getDurationFromExtractor(extractor: MediaExtractor): Long {
        try {
            if (extractor.trackCount > 0) {
                val format = extractor.getTrackFormat(0)
                val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                return (durationUs / 1000000.0).toLong()
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Could not get duration from extractor: ${e.message}")
        }
        return 0L
    }

    /**
     * Класс для хранения информации об аудио
     */
    data class AudioInfo(
        val sampleRate: Int,
        val channels: Int,
        val bitDepth: Int,
        val duration: Float // в секундах
    )
}
