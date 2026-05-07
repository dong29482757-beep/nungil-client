package com.example.myapplication

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

class VoskWakeWordManager(
    private val context: Context,
    private val keywords: String, // 예: "[\"똘똘\", \"똘똘아\"]"
    private val listener: WakeWordListener
) : RecognitionListener {

    interface WakeWordListener {
        fun onKeywordDetected()
        fun onModelLoaded()
        fun onModelLoadFail()
    }

    private var speechService: SpeechService? = null
    private var model: Model? = null

    fun initModel() {
        StorageService.unpack(context, "model-ko", "model", { m ->
            model = m
            listener.onModelLoaded()
        }, {
            Log.e("VoskManager", "Unpack Fail")
            listener.onModelLoadFail()
        })
    }

    fun startListening() {
        if (speechService != null || model == null) return
        try {
            val rec = Recognizer(model, 16000.0f, keywords)
            speechService = SpeechService(rec, 16000.0f)
            speechService?.startListening(this)
        } catch (e: Exception) {
            Log.e("VoskManager", "Start Fail: ${e.message}")
        }
    }

    fun stopListening() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
    }

    override fun onResult(hypothesis: String) {
        val text = JSONObject(hypothesis).optString("text", "").trim()
        if (text.isNotEmpty()) { // 키워드가 감지되면(Recognizer에 등록된 단어만 반환됨)
            listener.onKeywordDetected()
        }
    }

    override fun onPartialResult(p0: String?) {}
    override fun onFinalResult(p0: String?) {}
    override fun onError(e: Exception?) { stopListening() }
    override fun onTimeout() { stopListening() }
}