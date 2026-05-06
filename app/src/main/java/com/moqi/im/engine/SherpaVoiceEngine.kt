package com.moqi.im.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.moqi.im.dict.Dictionary

/**
 * 语音输入引擎
 * 
 * 当前实现使用 Android 原生 SpeechRecognizer（需要设备支持）
 * TODO: 集成 Sherpa-onnx 实现完全本地离线识别
 * 
 * Sherpa-onnx 集成步骤：
 * 1. 在 build.gradle 添加依赖: implementation("com.github.k2-fsa:sherpa-onnx:v1.12.1")
 * 2. 下载中文模型文件（约 50-100MB）到应用私有目录
 * 3. 修改此类使用 com.k2fsa.sherpa.onnx.OnlineRecognizer
 */
class SherpaVoiceEngine(private val context: Context) : InputEngine {

    private var speechRecognizer: SpeechRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var onResultCallback: ((String) -> Unit)? = null
    private var onErrorCallback: (() -> Unit)? = null
    
    private val sampleRate = 16000
    private val bufferSize = (0.1 * sampleRate).toInt()

    /**
     * 开始语音识别
     */
    fun startListening(onResult: (String) -> Unit, onError: () -> Unit) {
        onResultCallback = onResult
        onErrorCallback = onError

        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError()
            return
        }

        // 检查设备是否支持语音识别
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            startSystemSpeechRecognition()
        } else {
            // 设备不支持，提示用户
            onError()
        }
    }

    /**
     * 使用 Android 原生 SpeechRecognizer
     */
    private fun startSystemSpeechRecognition() {
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    isRecording = false
                    onErrorCallback?.invoke()
                }
                override fun onResults(results: android.os.Bundle?) {
                    isRecording = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onResultCallback?.invoke(matches[0])
                    } else {
                        onErrorCallback?.invoke()
                    }
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onResultCallback?.invoke(matches[0])
                    }
                }
                @Suppress("DEPRECATION")
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })

            val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
            isRecording = true
        } catch (e: Exception) {
            e.printStackTrace()
            onErrorCallback?.invoke()
        }
    }

    /**
     * TODO: 使用 Sherpa-onnx 进行本地识别
     * 
     * 实现步骤：
     * 1. 初始化 OnlineRecognizer
     * 2. 创建 OnlineStream
     * 3. 使用 AudioRecord 录制音频
     * 4. 将音频数据送入 stream.acceptWaveform()
     * 5. 调用 recognizer.decode(stream) 解码
     * 6. 获取识别结果 recognizer.getResult(stream)
     */
    private fun startSherpaOnnxRecognition() {
        // 模型文件路径
        // val modelDir = File(context.getExternalFilesDir(null), "sherpa-models")
        // val config = OnlineRecognizerConfig(...)
        // val recognizer = OnlineRecognizer(config)
        // val stream = recognizer.createStream()
        // ...
    }

    fun stopListening() {
        isRecording = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    fun destroy() {
        stopListening()
    }

    override fun processInput(input: String): List<String> = emptyList()
    override fun reset() {}
    override fun setDictionary(dict: Dictionary) {}
}