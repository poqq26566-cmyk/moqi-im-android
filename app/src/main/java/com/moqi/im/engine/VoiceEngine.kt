package com.moqi.im.engine

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.moqi.im.dict.Dictionary

class VoiceEngine : InputEngine {

    private var speechRecognizer: SpeechRecognizer? = null
    private var resultCallback: ((String) -> Unit)? = null
    private var errorCallback: (() -> Unit)? = null

    fun initialize(androidContext: android.content.Context, onResult: (String) -> Unit, onError: () -> Unit) {
        resultCallback = onResult
        errorCallback = onError

        if (SpeechRecognizer.isRecognitionAvailable(androidContext)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(androidContext)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    errorCallback?.invoke()
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        resultCallback?.invoke(matches[0])
                    } else {
                        errorCallback?.invoke()
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                @Suppress("DEPRECATION")
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    fun startListening(androidContext: android.content.Context) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    override fun processInput(input: String): List<String> = emptyList()
    override fun reset() {}
    override fun setDictionary(dict: Dictionary) {}
}