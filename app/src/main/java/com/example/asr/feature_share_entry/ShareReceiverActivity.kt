package com.example.asr.feature_share_entry

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.asr.core.error.DomainError
import com.example.asr.core.utils.Logger
import kotlinx.coroutines.*

class ShareReceiverActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Получаем Uri из Intent
        val audioUri = getAudioUri(intent)
        if (audioUri == null) {
            Log.e("ShareReceiver", "No audio URI found")
            finish()
            return
        }
        
        // Запускаем RecognitionService
        startRecognitionService(audioUri)
        
        // Закрываем активити
        finish()
    }
    
    private fun getAudioUri(intent: Intent): Uri? {
        return if (intent.clipData != null && intent.clipData.itemCount > 0) {
            intent.clipData.getItemAt(0).uri
        } else {
            intent.data
        }
    }
    
    private fun startRecognitionService(audioUri: Uri) {
        val intent = Intent(this, RecognitionService::class.java)
        intent.putExtra("audio_uri", audioUri.toString())
        startForegroundService(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
