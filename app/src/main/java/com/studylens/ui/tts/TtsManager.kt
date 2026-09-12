package com.studylens.ui.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        Log.d(TAG, "Initializing TextToSpeech engine...")
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                _isReady.value = true
                Log.d(TAG, "TTS engine initialized successfully and set to Locale.US.")
            } else {
                Log.w(TAG, "TTS language Locale.US is missing or not supported ($result).")
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "TTS started speaking: utteranceId=$utteranceId")
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS finished speaking: utteranceId=$utteranceId")
                    _isSpeaking.value = false
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS error speaking: utteranceId=$utteranceId")
                    _isSpeaking.value = false
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.e(TAG, "TTS error speaking: utteranceId=$utteranceId, errorCode=$errorCode")
                    _isSpeaking.value = false
                }
            })
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
        }
    }

    fun speak(text: String) {
        if (_isReady.value && text.isNotBlank()) {
            Log.d(TAG, "Requesting TTS speech for text length: ${text.length}")
            _isSpeaking.value = true
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "StudyLensTTS_${System.currentTimeMillis()}")
        } else {
            Log.w(TAG, "Cannot speak text: isReady=${_isReady.value}, isNotBlank=${text.isNotBlank()}")
        }
    }

    fun stop() {
        Log.d(TAG, "Stopping TTS speech.")
        tts?.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        Log.d(TAG, "Shutting down TTS engine.")
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isReady.value = false
        _isSpeaking.value = false
    }

    companion object {
        private const val TAG = "TtsManager"
    }
}
