package com.example.asr.core.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.asr.core.error.DomainError
import com.example.asr.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

class AudioConverter {

    companion object {
        private const val TAG = "AudioConverter"
        private const val DEFAULT_SAMPLE_RATE = 16000
        private const val DEFAULT_CHANNELS = 1
        private const val DEFAULT_BIT_DEPTH = 16
        private const val MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024
        private const val MIN_AUDIO_DATA_SIZE = 10
        private const val BUFFER_SIZE = 4096
        private const val TIMEOUT_US = 1_000_000L
    }

    suspend fun convertToWav(inputData: ByteArray, inputFormat: String? = null): ByteArray =
        withContext(Dispatchers.IO) {
            validateInput(inputData)
            try {
                Logger.i(TAG, "Converting audio to WAV")
                convertWithMediaCodec(inputData, inputFormat)
            } catch (e: Exception) {
                Logger.e(TAG, "Conversion to WAV failed", e)
                throw when (e) {
                    is DomainError -> e
                    else -> DomainError.AudioError("Failed to convert audio to WAV: ${e.message}", e)
                }
            }
        }

    suspend fun convertToPcm(
        inputData: ByteArray,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        channels: Int = DEFAULT_CHANNELS,
        bitDepth: Int = DEFAULT_BIT_DEPTH
    ): ByteArray = withContext(Dispatchers.IO) {
        validateInput(inputData)
        try {
            Logger.i(TAG, "Converting audio to PCM")
            convertPcmWithMediaCodec(inputData, sampleRate, channels, bitDepth)
        } catch (e: Exception) {
            Logger.e(TAG, "Conversion to PCM failed", e)
            throw when (e) {
                is DomainError -> e
                else -> DomainError.AudioError("Failed to convert audio to PCM: ${e.message}", e)
            }
        }
    }

    private fun validateInput(inputData: ByteArray) {
        if (inputData.isEmpty() || inputData.size < MIN_AUDIO_DATA_SIZE || inputData.size > MAX_FILE_SIZE_BYTES) {
            throw DomainError.AudioError("Invalid input audio data")
        }
    }

    private fun convertWithMediaCodec(inputData: ByteArray, inputFormat: String?): ByteArray {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        val tempFile = File.createTempFile("audio", ".tmp")
        tempFile.writeBytes(inputData)

        try {
            extractor = MediaExtractor().apply { setDataSource(tempFile.absolutePath) }
            if (extractor.trackCount == 0) throw DomainError.AudioError("No audio tracks found")

            val format = extractor.getTrackFormat(0)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/raw"
            codec = MediaCodec.createDecoderByType(mime).apply { configure(format, null, null, 0); start() }

            val audioData = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            var isEOS = false

            while (!isEOS) {
                val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferIndex >= 0) {
                    val sampleSize = extractor.readSampleData(ByteBuffer.wrap(buffer), 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEOS = true
                    } else {
                        codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, 0L, 0)
                        extractor.advance()
                    }
                }
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            while (outputBufferIndex >= 0) {
                codec.getOutputBuffer(outputBufferIndex)?.let { buf ->
                    val outBytes = ByteArray(buf.remaining())
                    buf.get(outBytes)
                    audioData.write(outBytes)
                }
                codec.releaseOutputBuffer(outputBufferIndex, false)
                outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            }

            return audioData.toByteArray()

        } catch (e: Exception) {
            Logger.w(TAG, "MediaCodec conversion failed: ${e.message}", e)
            return inputData.copyOf()
        } finally {
            extractor?.release()
            try { codec?.stop(); codec?.release() } catch (_: Exception) {}
            tempFile.delete()
        }
    }

    private fun convertPcmWithMediaCodec(
        inputData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitDepth: Int
    ): ByteArray {
        // Для PCM можно использовать тот же метод convertWithMediaCodec
        return convertWithMediaCodec(inputData, null)
    }

    fun validateWavData(wavData: ByteArray): Boolean =
        wavData.size >= 44 &&
                String(wavData.copyOfRange(0, 4)) == "RIFF" &&
                String(wavData.copyOfRange(8, 12)) == "WAVE"

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun getAudioInfo(inputData: ByteArray): AudioInfo = withContext(Dispatchers.IO) {
        validateInput(inputData)
        val tempFile = File.createTempFile("audio_info", ".tmp")
        tempFile.writeBytes(inputData)
        var extractor: MediaExtractor? = null

        try {
            extractor = MediaExtractor().apply { setDataSource(tempFile.absolutePath) }
            var sampleRate = DEFAULT_SAMPLE_RATE
            var channels = DEFAULT_CHANNELS
            var bitDepth = DEFAULT_BIT_DEPTH
            var durationSeconds = 0f

            if (extractor.trackCount > 0) {
                val format = extractor.getTrackFormat(0)
                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, DEFAULT_SAMPLE_RATE)
                channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, DEFAULT_CHANNELS)
                durationSeconds = (format.getLong(MediaFormat.KEY_DURATION, 0L) / 1_000_000f)
            }

            return@withContext AudioInfo(sampleRate, channels, bitDepth, durationSeconds)

        } finally {
            extractor?.release()
            tempFile.delete()
        }
    }

    data class AudioInfo(
        val sampleRate: Int,
        val channels: Int,
        val bitDepth: Int,
        val duration: Float
    )
}
