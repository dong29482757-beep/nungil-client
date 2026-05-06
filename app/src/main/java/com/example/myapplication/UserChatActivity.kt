package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class UserChatActivity : AppCompatActivity(), ChatAdapter.OnSuggestionClickListener {

    private val SERVER_URL = "http://10.100.0.52:8080/nungil-server/api/v1/question/analyze"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val chatList: MutableList<ChatMessage> = mutableListOf()
    private val conversationHistory: MutableList<ChatLog> = mutableListOf()
    private val gson = Gson()

    private lateinit var adapter: ChatAdapter
    private lateinit var rvChat: RecyclerView
    private lateinit var loadingBar: ProgressBar
    private lateinit var btnMic: View

    private lateinit var voiceRecorder: VoiceRecorderManager
    private lateinit var voskManager: VoskWakeWordManager
    private lateinit var ttsManager: TTSManager

    private var isRecording = false
    private var voiceMsgIndex = -1
    private var shouldLaunchCamera = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            bitmap?.let {
                addMsg(null, ChatMessage.TYPE_MINE, true, it, null)
                uploadToServer(it, null, null)
            }
        }
        resetToIdleState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_chat)

        setupUI()
        setupManagers()
        checkPermissions()

        val welcomeMsg = "반가워요! '똘똘아'라고 불러보세요."
        addMsg(welcomeMsg, ChatMessage.TYPE_OTHER, false, null, mutableListOf("오늘 날씨 어때?", "넌 누구니?"))
        conversationHistory.add(ChatLog("AI", welcomeMsg))

        mainHandler.postDelayed({ ttsManager.speak(welcomeMsg) }, 1000)
    }

    // --- 화면 가림 시 인식 중단 로직 추가 ---

    override fun onStart() {
        super.onStart()
        // 사용자가 앱으로 돌아오면 호출어 인식 시작
        if (::voskManager.isInitialized) {
            voskManager.startListening()
        }
    }

    override fun onStop() {
        super.onStop()
        // 화면이 꺼지거나 백그라운드로 가면 호출어 인식 중단
        if (::voskManager.isInitialized) {
            voskManager.stopListening()
        }
        // TTS나 녹음도 안전하게 중단시키고 싶다면 아래 주석 해제
        // if (ttsManager.isSpeaking()) ttsManager.stop()
    }

    // ------------------------------------

    private fun setupUI() {
        rvChat = findViewById(R.id.rvChat)
        loadingBar = findViewById(R.id.loadingBar)
        btnMic = findViewById(R.id.btnNext)

        adapter = ChatAdapter(chatList, this)
        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = adapter

        btnMic.setOnClickListener {
            if (loadingBar.visibility == View.VISIBLE) return@setOnClickListener

            if (ttsManager.isSpeaking()) {
                ttsManager.stop()
                updateMicButtonUI(forceMic = true)
                handleTtsEndFlow()
                return@setOnClickListener
            }

            if (isRecording) {
                voiceRecorder.cancel()
                resetToIdleState()
                return@setOnClickListener
            }

            startRecordingFlow(manual = true)
        }
    }

    private fun handleTtsEndFlow() {
        runOnUiThread {
            updateMicButtonUI(forceMic = true)
            if (shouldLaunchCamera) {
                shouldLaunchCamera = false
                mainHandler.postDelayed({ openBackCamera() }, 300)
            } else {
                // 현재 화면이 떠 있는 상태(Foreground)일 때만 인식을 시작하도록 체크
                if (!isRecording && loadingBar.visibility != View.VISIBLE) {
                    voskManager.startListening()
                }
            }
        }
    }

    private fun updateMicButtonUI(forceMic: Boolean = false) {
        runOnUiThread {
            val micImageView = btnMic as? ImageView
            when {
                forceMic -> {
                    micImageView?.setImageResource(android.R.drawable.ic_btn_speak_now)
                    btnMic.setBackgroundColor(Color.TRANSPARENT)
                }
                ttsManager.isSpeaking() -> {
                    micImageView?.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    btnMic.setBackgroundColor(Color.TRANSPARENT)
                }
                isRecording -> {
                    micImageView?.setImageResource(android.R.drawable.ic_btn_speak_now)
                    btnMic.setBackgroundColor(Color.parseColor("#E91E63"))
                }
                else -> {
                    micImageView?.setImageResource(android.R.drawable.ic_btn_speak_now)
                    btnMic.setBackgroundColor(Color.TRANSPARENT)
                }
            }
        }
    }

    private fun setupManagers() {
        ttsManager = TTSManager(this)
        ttsManager.onStatusListener = { isSpeaking ->
            if (!isSpeaking) {
                handleTtsEndFlow()
            } else {
                updateMicButtonUI(forceMic = false)
            }
        }

        voiceRecorder = VoiceRecorderManager(this, object : VoiceRecorderManager.RecorderListener {
            override fun onRecordingStart() {
                isRecording = true
                runOnUiThread {
                    updateMicButtonUI()
                    addMsg("🎤 듣고 있습니다...", ChatMessage.TYPE_MINE, false, null, null)
                    voiceMsgIndex = chatList.size - 1
                }
            }
            override fun onRecordingSuccess(file: File) {
                isRecording = false
                updateMicButtonUI()
                updateVoiceStatus("🔍 분석 중...")
                uploadToServer(null, file, null)
            }
            override fun onRecordingCancel() { resetToIdleState() }
            override fun onError(message: String) { resetToIdleState() }
        })

        voskManager = VoskWakeWordManager(this, "[\"똘똘\", \"똘똘아\"]", object : VoskWakeWordManager.WakeWordListener {
            override fun onKeywordDetected() {
                // 이 안에서도 한 번 더 체크: 앱이 현재 전면에 활성화된 상태인가?
                if (loadingBar.visibility != View.VISIBLE && !isRecording && !ttsManager.isSpeaking()) {
                    startRecordingFlow(manual = false)
                }
            }
            override fun onModelLoaded() {
                // 초기 로딩 후 화면이 켜진 상태면 시작
                voskManager.startListening()
            }
            override fun onModelLoadFail() { Log.e("VOSK", "Load Fail") }
        })
    }

    private fun resetToIdleState() {
        isRecording = false
        shouldLaunchCamera = false
        updateMicButtonUI(forceMic = true)
        runOnUiThread {
            // Foreground 상태일 때만 인식 재개
            voskManager.startListening()
        }
    }

    private fun openBackCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra("android.intent.extras.CAMERA_FACING", 0)
            putExtra("android.intent.extras.LENS_FACING_FRONT", 0)
            putExtra("android.intent.extra.USE_FRONT_CAMERA", false)
        }
        try {
            takePictureLauncher.launch(intent)
        } catch (e: Exception) {
            resetToIdleState()
        }
    }

    private fun startRecordingFlow(manual: Boolean) {
        voskManager.stopListening()
        if (!manual) {
            ttsManager.speak("네, 말씀하세요.")
            mainHandler.postDelayed({
                if (!isRecording && loadingBar.visibility != View.VISIBLE) voiceRecorder.start(5000)
            }, 1200)
        } else {
            voiceRecorder.start(5000)
        }
    }

    private fun uploadToServer(newBitmap: Bitmap?, voice: File?, text: String?) {
        runOnUiThread {
            loadingBar.visibility = View.VISIBLE
            voskManager.stopListening()
        }

        if (text != null) conversationHistory.add(ChatLog("user", text))

        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        val textMediaType = "text/plain; charset=utf-8".toMediaTypeOrNull()

        builder.addFormDataPart("userId", null, RequestBody.create(textMediaType, "user_123"))
        builder.addFormDataPart("historyJson", null, RequestBody.create(textMediaType, gson.toJson(conversationHistory)))
        text?.let { builder.addFormDataPart("textPrompt", null, RequestBody.create(textMediaType, it)) }

        newBitmap?.let { bitmap ->
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            val byteArray = stream.toByteArray()
            builder.addFormDataPart("imageFile", "capture.jpg", RequestBody.create("image/jpeg".toMediaTypeOrNull(), byteArray))
        }

        voice?.let { if (it.exists()) builder.addFormDataPart("voiceFile", it.name, it.asRequestBody("audio/m4a".toMediaTypeOrNull())) }

        val request = Request.Builder().url(SERVER_URL).post(builder.build()).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { loadingBar.visibility = View.GONE; resetToIdleState() }
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                runOnUiThread {
                    loadingBar.visibility = View.GONE
                    parseServerResponse(body)
                }
            }
        })
    }

    private fun parseServerResponse(json: String) {
        try {
            val root = JSONObject(json)
            if (root.optString("status") == "SUCCESS") {
                val result = root.getJSONObject("result")
                shouldLaunchCamera = result.optBoolean("photoRequest", false)

                val transcribed = result.optString("transcribedText", "")
                if (transcribed.isNotEmpty()) {
                    updateVoiceStatus(transcribed)
                    conversationHistory.add(ChatLog("user", transcribed))
                }

                val answer = result.optString("answer", "")
                conversationHistory.add(ChatLog("AI", answer))

                val suggestArray = result.optJSONArray("suggestedQuestions")
                val suggests = mutableListOf<String?>()
                if (suggestArray != null) {
                    for (i in 0 until suggestArray.length()) suggests.add(suggestArray.getString(i))
                }

                addMsg(answer, ChatMessage.TYPE_OTHER, false, null, suggests)
                ttsManager.speak(answer)
            } else { resetToIdleState() }
        } catch (e: Exception) { resetToIdleState() }
    }

    private fun updateVoiceStatus(newText: String?) {
        if (voiceMsgIndex != -1 && voiceMsgIndex < chatList.size) {
            chatList[voiceMsgIndex].content = newText
            adapter.notifyItemChanged(voiceMsgIndex)
        }
    }

    private fun addMsg(c: String?, t: Int, i: Boolean, b: Bitmap?, s: MutableList<String?>?) {
        chatList.add(ChatMessage(c, t, i, b, s))
        adapter.notifyItemInserted(chatList.size - 1)
        rvChat.smoothScrollToPosition(chatList.size - 1)
    }

    private fun checkPermissions() {
        val perms = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        val needed = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        else voskManager.initModel()
    }

    override fun onSuggestionClick(text: String?) {
        if (loadingBar.visibility == View.VISIBLE || isRecording || ttsManager.isSpeaking()) return
        voskManager.stopListening()
        voiceMsgIndex = -1
        addMsg(text, ChatMessage.TYPE_MINE, false, null, null)
        uploadToServer(null, null, text)
    }

    override fun onDestroy() {
        super.onDestroy()
        voskManager.stopListening()
        voiceRecorder.release()
        ttsManager.release()
    }
}