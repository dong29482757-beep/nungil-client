package com.example.myapplication

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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

    private val SERVER_URL = "https://reflector-carrousel-overstock.ngrok-free.dev/nungil-server/api/v1/question/analyze"
    private val USER_ID  = "gadian01"
    private val USER_IDX = 1

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
    private lateinit var ivBear: ImageView

    private enum class BearMood { BASIC, WORRY, PRAISE }

    private fun setBearMood(mood: BearMood) {
        runOnUiThread {
            val res = when (mood) {
                BearMood.BASIC  -> R.drawable.ddolddol_basic
                BearMood.WORRY  -> R.drawable.ddolddol_worry
                BearMood.PRAISE -> R.drawable.ddolddol_praise
            }
            ivBear.setImageResource(res)
            ivBear.alpha = if (mood == BearMood.PRAISE) 0.25f else 0.13f
        }
    }

    private lateinit var voiceRecorder: VoiceRecorderManager
    private lateinit var voskManager: VoskWakeWordManager
    private lateinit var ttsManager: TTSManager
    private lateinit var scheduleManager: ScheduleManager

    private var isRecording = false
    private var voiceMsgIndex = -1
    private var shouldLaunchCamera = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // 사용자 정보 (DB에서 로드)
    private var userSpecialNote   = ""
    private var whitelistTaskNames = listOf<String>()

    // 일정 수행 상태
    private var scheduleTitle    = ""
    private var scheduleSteps    = listOf<String>()
    private var scheduleNote     = ""   // SCHEDULE.special_note + location
    private var currentScheduleId = -1
    private var currentStepIndex  = -1
    private val isScheduleMode get() = currentStepIndex >= 0

    // 포그라운드 알람 수신
    private val scheduleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val scheduleId = intent.getStringExtra("schedule_id") ?: return
            val title      = intent.getStringExtra("schedule_title") ?: "일정"
            showScheduleDialog(scheduleId, title)
        }
    }

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

        scheduleManager = ScheduleManager(this)
        ScheduleManager.createNotificationChannel(this)

        setupUI()
        setupManagers()
        checkPermissions()

        val welcomeMsg = "반가워요! '똘똘아'라고 불러보세요."
        addMsg(welcomeMsg, ChatMessage.TYPE_OTHER, false, null, mutableListOf("오늘 날씨 어때?", "넌 누구니?"))
        conversationHistory.add(ChatLog("AI", welcomeMsg))
        mainHandler.postDelayed({ ttsManager.speak(welcomeMsg) }, 1000)

        // DB에서 사용자 정보 + 일정 로드
        loadUserDataFromDB()

        handleScheduleIntent(intent)

        // 테스트용: 1분 후 임시 일정 실행
        mainHandler.postDelayed({ triggerTestSchedule() }, 15_000)
    }

    private fun triggerTestSchedule() {
        scheduleTitle     = "약 복용하기"
        scheduleSteps     = listOf("약통에서 오늘 약을 꺼내요", "물 한 컵을 준비해요", "약을 물과 함께 드세요")
        scheduleNote      = "장소: 주방 / 아침 식사 후 복용"
        currentScheduleId = -1
        currentStepIndex  = 0
        showScheduleDialog("-1", scheduleTitle)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleScheduleIntent(intent)
    }

    // ──────────────────────────────────────────────
    // DB 초기 로드
    // ──────────────────────────────────────────────
    private fun loadUserDataFromDB() {
        Thread {
            // 사용자 특이사항 + 화이트리스트
            val userInfo = ScheduleRepository.fetchUserInfo(USER_ID, USER_IDX)
            runOnUiThread {
                userSpecialNote    = userInfo.specialNote
                whitelistTaskNames = userInfo.whitelistTaskNames
            }
        }.start()

        // 오늘 일정 알람 등록
        scheduleManager.syncSchedulesFromDB(userId = USER_ID, userIdx = USER_IDX)
    }

    // ──────────────────────────────────────────────
    // 일정 알람 처리
    // ──────────────────────────────────────────────
    private fun handleScheduleIntent(intent: Intent) {
        if (intent.getBooleanExtra("schedule_auto_execute", false)) {
            val scheduleId = intent.getStringExtra("schedule_id") ?: return
            val title      = intent.getStringExtra("schedule_title") ?: return
            mainHandler.postDelayed({ startScheduleExecution(scheduleId, title) }, 1500)
        }
    }

    private fun showScheduleDialog(scheduleId: String, title: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("📅 일정 시간이 됐어요!")
                .setMessage(title)
                .setPositiveButton("하기") { _, _ -> startScheduleExecution(scheduleId, title) }
                .setNegativeButton("나중에", null)
                .setCancelable(true)
                .show()
        }
    }

    private fun startScheduleExecution(scheduleId: String, title: String) {
        val id = scheduleId.toIntOrNull() ?: -1

        // 테스트 일정은 이미 데이터가 세팅돼 있으므로 바로 실행
        if (id == -1 && scheduleSteps.isNotEmpty()) {
            currentStepIndex = 0
            sendSchedulePromptToAI()
            return
        }

        scheduleTitle     = title
        scheduleSteps     = emptyList()
        scheduleNote      = ""
        currentScheduleId = id
        currentStepIndex  = 0

        Thread {
            val (steps, note) = ScheduleRepository.fetchStepsByScheduleId(currentScheduleId, USER_ID, USER_IDX)
            runOnUiThread {
                scheduleSteps = steps
                scheduleNote  = note
                sendSchedulePromptToAI()
            }
        }.start()
    }

    private fun sendSchedulePromptToAI() {
        if (loadingBar.visibility == View.VISIBLE || isRecording) return
        voskManager.stopListening()
        voiceMsgIndex = -1

        val prompt = buildSchedulePrompt()
        addMsg("📅 $scheduleTitle ${stepProgressLabel()}", ChatMessage.TYPE_MINE, false, null, null)
        uploadToServer(null, null, prompt)
    }

    private fun buildSchedulePrompt(): String {
        val userContext = buildUserContext()
        val noteStr = if (scheduleNote.isNotBlank()) "참고사항: $scheduleNote\n" else ""

        return if (scheduleSteps.isEmpty()) {
            "$userContext\n[일정 수행 모드]\n보호자가 등록한 일정: $scheduleTitle\n${noteStr}" +
            "지금 '$scheduleTitle' 일정을 시작합니다. 짧고 쉬운 말로, 한 번에 한 가지만 안내해주세요."
        } else {
            val stepDesc = scheduleSteps[currentStepIndex]
            val total    = scheduleSteps.size
            val current  = currentStepIndex + 1
            "$userContext\n[일정 수행 모드]\n보호자가 등록한 일정: $scheduleTitle\n${noteStr}" +
            "전체 ${total}단계 중 ${current}단계\n현재 단계: $stepDesc\n" +
            "위 단계를 짧고 쉬운 말로 한 가지만 안내해주세요. " +
            "잘 했으면 크게 칭찬해주세요. " +
            "단계가 끝나면 '다음' 또는 '완료'라고 말하면 넘어간다고 알려주세요."
        }
    }

    /**
     * 일반 대화 시 AI에 전달하는 사용자 컨텍스트.
     * 특이사항과 허용 활동 목록을 포함해 AI가 적절히 응답하도록 한다.
     */
    private fun buildUserContext(): String {
        val sb = StringBuilder("[사용자 정보]\n")
        if (userSpecialNote.isNotBlank()) {
            sb.append("특이사항: $userSpecialNote\n")
        }
        if (whitelistTaskNames.isNotEmpty()) {
            sb.append("보호자가 허용한 활동: ${whitelistTaskNames.joinToString(", ")}\n")
            sb.append("위 목록에 없는 활동은 안전을 위해 안내하지 말고 허용된 활동을 권유해주세요.\n")
        }
        sb.append("사용자는 중등도 지적장애가 있습니다. 짧고 쉬운 말로, 한 번에 한 가지만 말해주세요.")
        return sb.toString()
    }

    private fun stepProgressLabel(): String {
        if (scheduleSteps.isEmpty()) return "시작"
        return "(${currentStepIndex + 1}/${scheduleSteps.size}단계)"
    }

    private fun proceedToNextStep() {
        if (!isScheduleMode) return
        currentStepIndex++
        if (scheduleSteps.isNotEmpty() && currentStepIndex >= scheduleSteps.size) {
            finishSchedule()
            return
        }
        sendSchedulePromptToAI()
    }

    private fun finishSchedule() {
        // DB 완료 처리
        ScheduleRepository.completeSchedule(currentScheduleId)

        currentStepIndex  = -1
        currentScheduleId = -1
        scheduleTitle     = ""
        scheduleSteps     = emptyList()
        scheduleNote      = ""

        setBearMood(BearMood.PRAISE)
        mainHandler.postDelayed({ setBearMood(BearMood.BASIC) }, 4000)

        val doneMsg = "수고했어요! 일정을 모두 완료했어요. 😊"
        addMsg(doneMsg, ChatMessage.TYPE_OTHER, false, null, null)
        ttsManager.speak(doneMsg)
    }

    // ──────────────────────────────────────────────
    // 생명주기
    // ──────────────────────────────────────────────
    override fun onStart() {
        super.onStart()
        if (::voskManager.isInitialized) voskManager.startListening()

        val filter = IntentFilter(ScheduleAlarmReceiver.ACTION_SCHEDULE_ALERT)
        ContextCompat.registerReceiver(this, scheduleReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        super.onStop()
        if (::voskManager.isInitialized) voskManager.stopListening()
        try { unregisterReceiver(scheduleReceiver) } catch (e: Exception) { }
    }

    override fun onDestroy() {
        super.onDestroy()
        voskManager.stopListening()
        voiceRecorder.release()
        ttsManager.release()
    }

    // ──────────────────────────────────────────────
    // UI 설정
    // ──────────────────────────────────────────────
    private fun setupUI() {
        rvChat    = findViewById(R.id.rvChat)
        loadingBar = findViewById(R.id.loadingBar)
        btnMic    = findViewById(R.id.btnNext)
        ivBear    = findViewById(R.id.ivBear)

        adapter = ChatAdapter(chatList, this)
        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = adapter

        btnMic.setOnClickListener {
            if (loadingBar.visibility == View.VISIBLE) return@setOnClickListener
            if (ttsManager.isSpeaking()) {
                ttsManager.stop(); updateMicButtonUI(forceMic = true); handleTtsEndFlow()
                return@setOnClickListener
            }
            if (isRecording) { voiceRecorder.cancel(); resetToIdleState(); return@setOnClickListener }
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
                if (!isRecording && loadingBar.visibility != View.VISIBLE) voskManager.startListening()
            }
        }
    }

    private fun updateMicButtonUI(forceMic: Boolean = false) {
        runOnUiThread {
            val mic = btnMic as? ImageView
            when {
                forceMic              -> { mic?.setImageResource(android.R.drawable.ic_btn_speak_now); btnMic.setBackgroundColor(Color.TRANSPARENT) }
                ttsManager.isSpeaking() -> { mic?.setImageResource(android.R.drawable.ic_menu_close_clear_cancel); btnMic.setBackgroundColor(Color.TRANSPARENT) }
                isRecording           -> { mic?.setImageResource(android.R.drawable.ic_btn_speak_now); btnMic.setBackgroundColor(Color.parseColor("#E91E63")) }
                else                  -> { mic?.setImageResource(android.R.drawable.ic_btn_speak_now); btnMic.setBackgroundColor(Color.TRANSPARENT) }
            }
        }
    }

    // ──────────────────────────────────────────────
    // 매니저 설정
    // ──────────────────────────────────────────────
    private fun setupManagers() {
        ttsManager = TTSManager(this)
        ttsManager.onStatusListener = { isSpeaking ->
            if (!isSpeaking) handleTtsEndFlow() else updateMicButtonUI(forceMic = false)
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
                if (loadingBar.visibility != View.VISIBLE && !isRecording && !ttsManager.isSpeaking())
                    startRecordingFlow(manual = false)
            }
            override fun onModelLoaded()   { voskManager.startListening() }
            override fun onModelLoadFail() { Log.e("VOSK", "Load Fail") }
        })
    }

    private fun resetToIdleState() {
        isRecording = false
        shouldLaunchCamera = false
        updateMicButtonUI(forceMic = true)
        runOnUiThread { voskManager.startListening() }
    }

    private fun openBackCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra("android.intent.extras.CAMERA_FACING", 0)
            putExtra("android.intent.extras.LENS_FACING_FRONT", 0)
            putExtra("android.intent.extra.USE_FRONT_CAMERA", false)
        }
        try { takePictureLauncher.launch(intent) } catch (e: Exception) { resetToIdleState() }
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

    // ──────────────────────────────────────────────
    // 서버 통신
    // ──────────────────────────────────────────────
    private fun uploadToServer(newBitmap: Bitmap?, voice: File?, text: String?) {
        runOnUiThread {
            loadingBar.visibility = View.VISIBLE
            setBearMood(BearMood.WORRY)
            voskManager.stopListening()
        }

        // 일반 대화인 경우 사용자 컨텍스트를 textPrompt 앞에 붙임
        val finalText = if (text != null && !isScheduleMode) {
            "${buildUserContext()}\n\n사용자 질문: $text"
        } else {
            text
        }

        if (finalText != null) conversationHistory.add(ChatLog("user", finalText))

        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        val textMt = "text/plain; charset=utf-8".toMediaTypeOrNull()

        builder.addFormDataPart("userId",      null, RequestBody.create(textMt, USER_ID))
        builder.addFormDataPart("historyJson", null, RequestBody.create(textMt, gson.toJson(conversationHistory)))
        finalText?.let { builder.addFormDataPart("textPrompt", null, RequestBody.create(textMt, it)) }

        newBitmap?.let { bitmap ->
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            builder.addFormDataPart("imageFile", "capture.jpg",
                RequestBody.create("image/jpeg".toMediaTypeOrNull(), stream.toByteArray()))
        }
        voice?.let { if (it.exists()) builder.addFormDataPart("voiceFile", it.name, it.asRequestBody("audio/m4a".toMediaTypeOrNull())) }

        val request = Request.Builder().url(SERVER_URL).post(builder.build()).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { loadingBar.visibility = View.GONE; setBearMood(BearMood.BASIC); resetToIdleState() }
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                runOnUiThread { loadingBar.visibility = View.GONE; setBearMood(BearMood.BASIC); parseServerResponse(body) }
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

                // 일정 수행 중이면 "다음 단계" / "완료" 버튼 추가
                if (isScheduleMode && scheduleSteps.isNotEmpty()) {
                    val nextLabel = if (currentStepIndex + 1 < scheduleSteps.size) "다음 단계로" else "완료"
                    suggests.add(0, nextLabel)
                }

                // 질문 완료 로그 (백그라운드)
                val userContent = conversationHistory.lastOrNull { it.role == "user" }?.message ?: ""
                Thread {
                    ScheduleRepository.logQuestionSuccess(
                        ScheduleRepository.logQuestionStart(USER_ID, USER_IDX, userContent, answer)
                    )
                }.start()

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

    // ──────────────────────────────────────────────
    // 권한
    // ──────────────────────────────────────────────
    private fun checkPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        val needed = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        else voskManager.initModel()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voskManager.initModel()
        }
    }

    // ──────────────────────────────────────────────
    // 제안 버튼 클릭
    // ──────────────────────────────────────────────
    override fun onSuggestionClick(text: String?) {
        if (loadingBar.visibility == View.VISIBLE || isRecording || ttsManager.isSpeaking()) return

        if (isScheduleMode && (text == "다음 단계로" || text == "완료")) {
            proceedToNextStep()
            return
        }

        voskManager.stopListening()
        voiceMsgIndex = -1
        addMsg(text, ChatMessage.TYPE_MINE, false, null, null)
        uploadToServer(null, null, text)
    }
}
