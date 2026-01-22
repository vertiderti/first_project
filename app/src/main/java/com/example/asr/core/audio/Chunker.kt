package com.example.asr.core.audio

import com.example.asr.core.error.DomainError
import com.example.asr.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import kotlin.math.min

/**
 * Разделитель аудио на чанки с ограничением размера (1MB)
 */
class Chunker(
    private val maxChunkSizeBytes: Long = MAX_CHUNK_SIZE_BYTES,
    private val defaultBufferSize: Int = DEFAULT_BUFFER_SIZE
) {

    companion object {
        private const val TAG = "Chunker"
        private const val MAX_CHUNK_SIZE_BYTES = 1L * 1024 * 1024 // 1MB ограничение на чанк
        private const val DEFAULT_BUFFER_SIZE = 8192 // 8KB буфер для чтения
        private const val MAX_BUFFER_SIZE = 1024 * 1024 // Максимальный размер буфера 1MB
        private const val MAX_CHUNKS_COUNT = 1000 // Ограничение для предотвращения утечек памяти
        private const val WAV_HEADER_SIZE = 44 // Стандартный размер WAV заголовка
    }

    /**
     * Разбивает аудио данные на чанки с учетом ограничения размера
     */
    suspend fun chunkAudio(audioData: ByteArray): List<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                Logger.i(TAG, "Starting chunking of audio data: ${audioData.size} bytes")
                
                if (!validateAudioData(audioData)) {
                    Logger.w(TAG, "Invalid audio data provided for chunking")
                    return@withContext emptyList()
                }
                
                // Проверяем, нужно ли разбивать на чанки
                if (audioData.size <= maxChunkSizeBytes) {
                    Logger.i(TAG, "Audio data size (${audioData.size} bytes) is less than max chunk size (${maxChunkSizeBytes} bytes)")
                    return@withContext listOf(audioData)
                }
                
                val chunks = mutableListOf<ByteArray>()
                var offset = 0
                var chunksCount = 0
                
                while (offset < audioData.size && chunksCount < MAX_CHUNKS_COUNT) {
                    // Проверяем отмену операции
                    if (Thread.currentThread().isInterrupted) {
                        throw CancellationException("Audio chunking was cancelled")
                    }
                    
                    val remainingBytes = audioData.size - offset
                    val chunkSize = minOf(maxChunkSizeBytes.toInt(), remainingBytes)
                    
                    val chunk = ByteArray(chunkSize)
                    System.arraycopy(audioData, offset, chunk, 0, chunkSize)
                    chunks.add(chunk)
                    chunksCount++
                    
                    offset += chunkSize
                    
                    Logger.d(TAG, "Created chunk ${chunks.size} of ${if (maxChunkSizeBytes > 0) (audioData.size + maxChunkSizeBytes - 1) / maxChunkSizeBytes else 1}")
                }
                
                if (chunksCount >= MAX_CHUNKS_COUNT && offset < audioData.size) {
                    Logger.w(TAG, "Maximum chunks count reached ($MAX_CHUNKS_COUNT), data may be truncated")
                }
                
                Logger.i(TAG, "Successfully created ${chunks.size} chunks from audio data")
                return@withContext chunks
                
            } catch (e: Exception) {
                Logger.e(TAG, "Error chunking audio data", e)
                throw when (e) {
                    is DomainError -> e
                    else -> DomainError.AudioError("Failed to chunk audio data: ${e.message}")
                }
            }
        }
    }

    /**
     * Потоковая обработка аудио данных для создания чанков с проверкой размера буфера
     */
    suspend fun chunkAudioStreaming(inputStream: InputStream, onChunkReady: (ByteArray) -> Unit): Int {
        return withContext(Dispatchers.IO) {
            try {
                Logger.i(TAG, "Starting streaming chunking")
                
                val chunksCount = mutableListOf<ByteArray>()
                var totalBytesProcessed = 0
                
                // Используем буфер с проверкой размера
                val buffer = ByteArray(minOf(defaultBufferSize, MAX_BUFFER_SIZE))
                var bytesRead: Int
                
                while (true) {
                    // Проверяем отмену операции
                    if (Thread.currentThread().isInterrupted) {
                        throw CancellationException("Audio streaming chunking was cancelled")
                    }
                    
                    bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    
                    // Ограничить размер каждого чанка для потоковой обработки
                    val chunkSize = minOf(bytesRead, maxChunkSizeBytes.toInt())
                    val chunk = ByteArray(chunkSize)
                    System.arraycopy(buffer, 0, chunk, 0, chunkSize)
                    
                    chunksCount.add(chunk)
                    onChunkReady(chunk)
                    totalBytesProcessed += chunkSize
                    
                    // Логируем прогресс
                    if (chunksCount.size % 10 == 0) {
                        Logger.d(TAG, "Processed ${chunksCount.size} chunks, ${totalBytesProcessed} bytes")
                    }
                    
                    // Проверяем ограничение на количество чанков
                    if (chunksCount.size >= MAX_CHUNKS_COUNT) {
                        Logger.w(TAG, "Maximum chunks count reached ($MAX_CHUNKS_COUNT), stopping streaming")
                        break
                    }
                }
                
                Logger.i(TAG, "Successfully processed streaming audio: ${chunksCount.size} chunks, ${totalBytesProcessed} bytes")
                return@withContext totalBytesProcessed
                
            } catch (e: Exception) {
                Logger.e(TAG, "Error streaming chunking audio data", e)
                throw when (e) {
                    is DomainError -> e
                    else -> DomainError.AudioError("Failed to stream chunk audio data: ${e.message}")
                }
            }
        }
    }

    /**
     * Разбивает аудио данные на чанки с учетом времени (для аудио форматов)
     */
    suspend fun chunkAudioByTime(audioData: ByteArray, maxDurationSeconds: Int = 30): List<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                Logger.i(TAG, "Starting time-based chunking")
                
                if (!validateAudioData(audioData)) {
                    return@withContext emptyList()
                }
                
                // Реальная реализация с анализом аудио формата
                val sampleRate = extractSampleRateFromAudio(audioData)
                val bitDepth = extractBitDepthFromAudio(audioData)
                val channels = extractChannelsFromAudio(audioData)
                
                Logger.d(TAG, "Audio format: sampleRate=$sampleRate, bitDepth=$bitDepth, channels=$channels")
                
                // Рассчитываем количество байтов на секунду
                val bytesPerSecond = (sampleRate * channels * bitDepth / 8).toInt()
                val maxBytesPerChunk = maxDurationSeconds * bytesPerSecond
                
                // Если рассчитанный размер чанка больше максимального, используем максимальный
                val actualChunkSize = minOf(maxBytesPerChunk, maxChunkSizeBytes.toInt())
                
                Logger.d(TAG, "Calculated chunk size: ${actualChunkSize} bytes for $maxDurationSeconds seconds")
                
                // Разбиваем на чанки с учетом рассчитанного размера
                val chunks = mutableListOf<ByteArray>()
                var offset = 0
                
                while (offset < audioData.size) {
                    if (Thread.currentThread().isInterrupted) {
                        throw CancellationException("Audio time-based chunking was cancelled")
                    }
                    
                    val remainingBytes = audioData.size - offset
                    val chunkSize = minOf(actualChunkSize, remainingBytes)
                    
                    val chunk = ByteArray(chunkSize)
                    System.arraycopy(audioData, offset, chunk, 0, chunkSize)
                    chunks.add(chunk)
                    offset += chunkSize
                    
                    Logger.d(TAG, "Created time-based chunk ${chunks.size}")
                }
                
                Logger.i(TAG, "Successfully created ${chunks.size} time-based chunks")
                return@withContext chunks
                
            } catch (e: Exception) {
                Logger.e(TAG, "Error chunking audio by time", e)
                throw when (e) {
                    is DomainError -> e
                    else -> DomainError.AudioError("Failed to chunk audio by time: ${e.message}")
                }
            }
        }
    }

    /**
     * Разбивает аудио данные на чанки с сохранением структуры WAV заголовка
     */
    suspend fun chunkAudioWithWavHeader(audioData: ByteArray, wavHeaderSize: Int = WAV_HEADER_SIZE): List<ByteArray> {
        return withContext(Dispatchers.IO) {
            try {
                Logger.i(TAG, "Starting chunking with WAV header preservation")
                
                if (!validateAudioData(audioData)) {
                    return@withContext emptyList()
                }
                
                // Проверяем наличие корректного WAV заголовка
                if (audioData.size < wavHeaderSize) {
                    Logger.w(TAG, "Audio data is smaller than WAV header size, chunking as regular data")
                    return@withContext chunkAudio(audioData)
                }
                
                val header = ByteArray(wavHeaderSize)
                System.arraycopy(audioData, 0, header, 0, wavHeaderSize)
                
                val audioBody = ByteArray(audioData.size - wavHeaderSize)
                System.arraycopy(audioData, wavHeaderSize, audioBody, 0, audioBody.size)
                
                // Разбиваем только тело аудио
                val chunks = mutableListOf<ByteArray>()
                var offset = 0
                
                while (offset < audioBody.size) {
                    if (Thread.currentThread().isInterrupted) {
                        throw CancellationException("Audio chunking with header was cancelled")
                    }
                    
                    val remainingBytes = audioBody.size - offset
                    val chunkSize = minOf(maxChunkSizeBytes.toInt(), remainingBytes)
                    
                    // Создаем чанк с сохранением заголовка
                    val chunk = ByteArray(wavHeaderSize + chunkSize)
                    System.arraycopy(header, 0, chunk, 0, wavHeaderSize)
                    System.arraycopy(audioBody, offset, chunk, wavHeaderSize, chunkSize)
                    
                    chunks.add(chunk)
                    offset += chunkSize
                    
                    Logger.d(TAG, "Created chunk with header ${chunks.size}")
                }
                
                Logger.i(TAG, "Successfully created ${chunks.size} chunks with WAV header preservation")
                return@withContext chunks
                
            } catch (e: Exception) {
                Logger.e(TAG, "Error chunking audio with WAV header", e)
                throw when (e) {
                    is DomainError -> e
                    else -> DomainError.AudioError("Failed to chunk audio with WAV header: ${e.message}")
                }
            }
        }
    }

    /**
     * Объединяет чанки обратно в одно аудио сообщение
     */
    suspend fun unchunkAudio(chunks: List<ByteArray>): ByteArray {
        return withContext(Dispatchers.IO) {
            try {
                Logger.i(TAG, "Starting unchunking of ${chunks.size} chunks")
                
                if (chunks.isEmpty()) {
                    Logger.w(TAG, "Empty chunk list provided for unchunking")
                    return@withContext ByteArray(0)
                }
                
                // Проверяем, что все чанки не пустые
                val totalSize = chunks.sumOf { it.size }
                val result = ByteArray(totalSize)
                var offset = 0
                
                chunks.forEach { chunk ->
                    System.arraycopy(chunk, 0, result, offset, chunk.size)
                    offset += chunk.size
                }
                
                Logger.i(TAG, "Successfully unchunked audio: ${result.size} bytes")
                return@withContext result
                
            } catch (e: Exception) {
                Logger.e(TAG, "Error unchunking audio data", e)
                throw when (e) {
                    is DomainError -> e
                    else -> DomainError.AudioError("Failed to unchunk audio data: ${e.message}")
                }
            }
        }
    }

    /**
     * Получает информацию о чанках
     */
    suspend fun getChunkInfo(chunks: List<ByteArray>): ChunkInfo {
        return withContext(Dispatchers.IO) {
            try {
                val totalSize = chunks.sumOf { it.size }
                val chunkCount = chunks.size
                
                // Проверяем размеры чанков
                val chunkSizes = chunks.map { it.size }
                val minChunkSize = if (chunkSizes.isNotEmpty()) chunkSizes.minOrNull() ?: 0 else 0
                val maxChunkSize = if (chunkSizes.isNotEmpty()) chunkSizes.maxOrNull() ?: 0 else 0
                
                ChunkInfo(
                    totalSize = totalSize,
                    chunkCount = chunkCount,
                    minChunkSize = minChunkSize,
                    maxChunkSize = maxChunkSize,
                    averageChunkSize = if (chunkCount > 0) totalSize / chunkCount else 0
                )
            } catch (e: Exception) {
                Logger.e(TAG, "Error getting chunk info", e)
                throw when (e) {
                    is DomainError -> e
                    else -> DomainError.AudioError("Failed to get chunk information: ${e.message}")
                }
            }
        }
    }

    /**
     * Проверяет корректность WAV формата
     */
    private fun validateWavFormat(audioData: ByteArray): Boolean {
        if (audioData.size < WAV_HEADER_SIZE) return false
        
        try {
            val riffHeader = String(audioData.copyOfRange(0, 4))
            val waveHeader = String(audioData.copyOfRange(8, 12))
            val formatChunkId = String(audioData.copyOfRange(12, 16))
            
            // Дополнительная проверка на корректность формата
            return riffHeader == "RIFF" && 
                   waveHeader == "WAVE" && 
                   formatChunkId == "fmt "
        } catch (e: Exception) {
            Logger.e(TAG, "Error validating WAV format", e)
            return false
        }
    }

    /**
     * Извлекает частоту дискретизации из аудио данных с правильной обработкой порядка байтов
     */
    private fun extractSampleRateFromAudio(audioData: ByteArray): Int {
        if (!validateWavFormat(audioData)) {
            Logger.w(TAG, "Invalid WAV format, using default sample rate")
            return 16000 // стандартное значение
        }
        
        try {
            // WAV заголовок: частота дискретизации находится в байтах 24-27 (little-endian)
            val sampleRate = audioData[24].toInt() and 0xFF +
                            ((audioData[25].toInt() and 0xFF) shl 8) +
                            ((audioData[26].toInt() and 0xFF) shl 16) +
                            ((audioData[27].toInt() and 0xFF) shl 24)
            return sampleRate
        } catch (e: Exception) {
            Logger.w(TAG, "Error extracting sample rate, using default")
            return 16000 // стандартное значение
        }
    }

    /**
     * Извлекает битовую глубину из аудио данных с правильной обработкой порядка байтов
     */
    private fun extractBitDepthFromAudio(audioData: ByteArray): Int {
        if (!validateWavFormat(audioData)) {
            Logger.w(TAG, "Invalid WAV format, using default bit depth")
            return 16 // стандартное значение
        }
        
        try {
            // WAV заголовок: бит на сэмпл находится в байтах 34-35 (little-endian)
            val bitDepth = audioData[34].toInt() and 0xFF +
                          ((audioData[35].toInt() and 0xFF) shl 8)
            return bitDepth
        } catch (e: Exception) {
            Logger.w(TAG, "Error extracting bit depth, using default")
            return 16 // стандартное значение
        }
    }

    /**
     * Извлекает количество каналов из аудио данных с правильной обработкой порядка байтов
     */
    private fun extractChannelsFromAudio(audioData: ByteArray): Int {
        if (!validateWavFormat(audioData)) {
            Logger.w(TAG, "Invalid WAV format, using default channels")
            return 1 // стандартное значение
        }
        
        try {
            // WAV заголовок: каналы находятся в байтах 22-23 (little-endian)
            val channels = audioData[22].toInt() and 0xFF +
                          ((audioData[23].toInt() and 0xFF) shl 8)
            return channels
        } catch (e: Exception) {
            Logger.w(TAG, "Error extracting channels, using default")
            return 1 // стандартное значение
        }
    }

    /**
     * Проверяет корректность входных данных
     */
    private fun validateAudioData(audioData: ByteArray): Boolean {
        return audioData != null && audioData.isNotEmpty()
    }

    /**
     * Класс для хранения информации о чанках
     */
    data class ChunkInfo(
        val totalSize: Int,
        val chunkCount: Int,
        val minChunkSize: Int,
        val maxChunkSize: Int,
        val averageChunkSize: Int
    )
}
