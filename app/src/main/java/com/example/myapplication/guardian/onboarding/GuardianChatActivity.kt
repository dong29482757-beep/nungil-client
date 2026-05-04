package com.example.myapplication.guardian.onboarding

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.common.model.ChatMessage
import com.example.myapplication.core.network.ApiClient
import com.example.myapplication.core.network.ApiResult
import com.example.myapplication.core.network.Session
import com.example.myapplication.guardian.userchat.ui.ChatAdapter
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

class GuardianChatActivity : AppCompatActivity() {

    private lateinit var tts: TextToSpeech
    private val chatList = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    private lateinit var rvChat: RecyclerView
    private lateinit var etInput: EditText
    private lateinit var btnNext: Button
    private lateinit var layoutInput: View
    private lateinit var scrollChips: View
    private lateinit var chipGroup: ChipGroup

    private val registeredTaskIds   = mutableListOf<Int>()
    private val registeredTaskNames = mutableListOf<String>()
    private val allTasks            = mutableListOf<Pair<Int, String>>()

    // 봇이 응답 중일 때 입력 막기
    private var isBotTyping = false

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val MIN_TASKS = 2
        private const val MAX_TASKS = 4
    }

    private val fallbackTasks = listOf(
        Pair(0, "빨래하기"), Pair(0, "설거지하기"), Pair(0, "청소기 돌리기"),
        Pair(0, "밥 먹기"), Pair(0, "약 먹기")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guardian_chat)

        rvChat      = findViewById(R.id.rvChat)
        etInput     = findViewById(R.id.etInput)
        btnNext     = findViewById(R.id.btnNext)
        layoutInput = findViewById(R.id.layoutInput)
        scrollChips = findViewById(R.id.scrollChips)
        chipGroup   = findViewById(R.id.chipGroup)

        rvChat.layoutManager = LinearLayoutManager(this).also { it.stackFromEnd = true }
        adapter = ChatAdapter(chatList) {}
        rvChat.adapter = adapter

        btnNext.setOnClickListener { saveWhitelistAndNext() }
        findViewById<Button>(R.id.btnSend).setOnClickListener { sendInput() }
        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendInput(); true } else false
        }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.KOREAN
                loadTasksAndStartChat()
            }
        }
    }

    // ── 과업 목록 로드 → 채팅 시작 ──────────────────────────────────────
    private fun loadTasksAndStartChat() {
        ApiClient.get("/v1/guardian/tasks") { result ->
            when (result) {
                is ApiResult.Success -> {
                    try {
                        val arr = JSONObject(result.data).getJSONArray("tasks")
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            allTasks.add(Pair(obj.getInt("taskId"), obj.getString("taskName")))
                        }
                    } catch (_: Exception) {
                        allTasks.addAll(fallbackTasks)
                    }
                }
                is ApiResult.Error -> allTasks.addAll(fallbackTasks)
            }
            startChat()
        }
    }

    // ── 인사 → 안내 (TTS 완료 후 순서대로) ─────────────────────────────
    private fun startChat() {
        val greeting = "안녕하세요! 저는 눈길 도우미 똘똘이예요 😊"
        botMessage(greeting)
        tts.speak(greeting, TextToSpeech.QUEUE_FLUSH, null, "msg1")

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {}
            override fun onError(id: String) {}
            override fun onDone(id: String) {
                if (id == "msg1") {
                    val guide = "사용자가 해볼 수 있는 활동을 ${MIN_TASKS}~${MAX_TASKS}개 골라주세요.\n아래 목록에서 탭하거나 직접 입력해도 돼요!"
                    botMessage(guide, delay = 600)
                    tts.speak(guide, TextToSpeech.QUEUE_FLUSH, null, "msg2")
                } else if (id == "msg2") {
                    runOnUiThread {
                        layoutInput.visibility = View.VISIBLE
                        showSuggestionChips()
                    }
                }
            }
        })
    }

    // ── 제안 칩 표시 ────────────────────────────────────────────────────
    private fun showSuggestionChips() {
        chipGroup.removeAllViews()

        val remaining = allTasks.filter { (id, _) -> !registeredTaskIds.contains(id) }

        if (remaining.isEmpty() || registeredTaskIds.size >= MAX_TASKS) {
            scrollChips.visibility = View.GONE
            return
        }

        remaining.forEach { (taskId, taskName) ->
            val chip = Chip(this).apply {
                text = taskName
                isClickable = true
                isCheckable = false
                setChipBackgroundColorResource(R.color.colorPrimary)
                setTextColor(resources.getColor(android.R.color.white, null))
                setOnClickListener {
                    if (isBotTyping) return@setOnClickListener   // 봇 응답 중 탭 무시

                    addUserMessage(taskName)
                    setBotTyping(true)

                    if (registeredTaskIds.contains(taskId)) {
                        // 중복 → 한 마디로 끝
                        botMessage("'$taskName'은 이미 등록하셨어요! 다른 걸 선택해볼까요?", delay = 400)
                        handler.postDelayed({ setBotTyping(false) }, 600)
                    } else {
                        // 딜레이 후 등록 (바로 3개 뜨는 느낌 제거)
                        handler.postDelayed({ registerToWhitelist(taskId, taskName) }, 500)
                    }
                }
            }
            chipGroup.addView(chip)
        }
        scrollChips.visibility = View.VISIBLE
    }

    // ── 텍스트 직접 입력 ─────────────────────────────────────────────────
    private fun sendInput() {
        if (isBotTyping) return
        val input = etInput.text.toString().trim()
        if (input.isEmpty()) return
        etInput.setText("")
        addUserMessage(input)
        setBotTyping(true)
        searchAndRegister(input)
    }

    private fun searchAndRegister(input: String) {
        if (registeredTaskIds.size >= MAX_TASKS) {
            botMessage("이미 최대 ${MAX_TASKS}개를 등록하셨어요! '다음'을 눌러주세요.", delay = 400)
            handler.postDelayed({ setBotTyping(false) }, 600)
            return
        }

        // "잠깐만요" 메시지 → 딜레이 후 API 호출
        botMessage("잠깐만요, 확인해볼게요! 🔍", delay = 400)

        handler.postDelayed({
            val encodedInput = URLEncoder.encode(input, "UTF-8")
            ApiClient.get("/tasks/search?item=$encodedInput") { result ->
                when (result) {
                    is ApiResult.Success -> {
                        try {
                            val json = JSONObject(result.data)
                            if (json.optBoolean("found", false)) {
                                val taskId   = json.getInt("taskId")
                                val taskName = json.getString("taskName")
                                if (registeredTaskIds.contains(taskId)) {
                                    botMessage("'$taskName'은 이미 등록하셨어요! 다른 활동을 알려주세요.", delay = 500)
                                    handler.postDelayed({ setBotTyping(false) }, 800)
                                } else {
                                    registerToWhitelist(taskId, taskName)
                                }
                            } else {
                                botMessage("'$input'을 찾지 못했어요 😅\n아래 목록에서 선택하거나 다르게 입력해보세요!", delay = 500)
                                handler.postDelayed({ setBotTyping(false) }, 800)
                            }
                        } catch (_: Exception) {
                            botMessage("응답 처리 중 오류가 생겼어요. 다시 시도해주세요.", delay = 500)
                            handler.postDelayed({ setBotTyping(false) }, 800)
                        }
                    }
                    is ApiResult.Error -> {
                        botMessage("서버 연결에 실패했어요. 다시 시도해주세요.", delay = 500)
                        handler.postDelayed({ setBotTyping(false) }, 800)
                    }
                }
            }
        }, 800)   // "잠깐만요" 보여준 뒤 API 호출
    }

    // ── 화이트리스트 등록 ────────────────────────────────────────────────
    private fun registerToWhitelist(taskId: Int, taskName: String) {
        val body = JSONObject().apply { put("taskId", taskId) }.toString()
        val path = "/v1/guardian/settings/user/${Session.guardianId}/${Session.userIdx}/whitelist"

        ApiClient.post(path, body) { result ->
            when (result) {
                is ApiResult.Success -> {
                    registeredTaskIds.add(taskId)
                    registeredTaskNames.add(taskName)

                    val left = MAX_TASKS - registeredTaskIds.size
                    // ★ 핵심: 등록 확인 + 다음 안내를 메시지 1개로 합침
                    val msg = when {
                        left == 0 ->
                            "'$taskName' 등록 완료! 🎉\n활동 목록이 다 찼어요. '다음'을 눌러주세요."
                        registeredTaskIds.size >= MIN_TASKS ->
                            "'$taskName' 등록했어요! (${registeredTaskIds.size}/$MAX_TASKS)\n${left}개 더 추가하거나 '다음'으로 넘어가세요."
                        else ->
                            "'$taskName' 등록했어요! (${registeredTaskIds.size}/$MAX_TASKS)\n${left}개 더 추가해주세요."
                    }
                    botMessage(msg, delay = 400)

                    if (registeredTaskIds.size >= MIN_TASKS) {
                        runOnUiThread { btnNext.visibility = View.VISIBLE }
                    }

                    // 칩 갱신 + 입력 재활성화
                    handler.postDelayed({
                        setBotTyping(false)
                        showSuggestionChips()
                    }, 700)
                }
                is ApiResult.Error -> {
                    val msg = if (result.message.contains("MAX_ITEM_EXCEEDED", ignoreCase = true))
                        "과업은 최대 ${MAX_TASKS}개까지만 등록할 수 있어요."
                    else
                        "등록 중 오류가 생겼어요. 다시 시도해주세요."
                    botMessage(msg, delay = 400)
                    handler.postDelayed({ setBotTyping(false) }, 600)
                }
            }
        }
    }

    // ── 다음 화면으로 ────────────────────────────────────────────────────
    private fun saveWhitelistAndNext() {
        startActivity(Intent(this, SpecialNotesActivity::class.java))
        finish()
    }

    // ── UI 헬퍼 ─────────────────────────────────────────────────────────

    /** 봇 메시지: delay ms 후에 말풍선 추가 */
    private fun botMessage(text: String, delay: Long = 0) {
        handler.postDelayed({
            runOnUiThread {
                chatList.add(ChatMessage(text, ChatMessage.TYPE_OTHER, false, null, null))
                adapter.notifyItemInserted(chatList.size - 1)
                rvChat.smoothScrollToPosition(chatList.size - 1)
            }
        }, delay)
    }

    private fun addUserMessage(text: String) {
        runOnUiThread {
            chatList.add(ChatMessage(text, ChatMessage.TYPE_MINE, false, null, null))
            adapter.notifyItemInserted(chatList.size - 1)
            rvChat.smoothScrollToPosition(chatList.size - 1)
        }
    }

    /** 봇 응답 중 여부 → 칩/입력 활성화 제어 */
    private fun setBotTyping(typing: Boolean) {
        isBotTyping = typing
        runOnUiThread {
            etInput.isEnabled  = !typing
            val btnSend = findViewById<Button>(R.id.btnSend)
            btnSend?.isEnabled = !typing
            // 칩도 흐리게
            for (i in 0 until chipGroup.childCount) {
                chipGroup.getChildAt(i).alpha = if (typing) 0.4f else 1.0f
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
