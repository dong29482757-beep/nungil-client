package com.example.myapplication.guardian.onboarding

import android.content.Intent
import android.os.Bundle
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

    // 등록된 taskId 목록 (중복 방지용)
    private val registeredTaskIds = mutableListOf<Int>()
    private val registeredTaskNames = mutableListOf<String>()

    companion object {
        private const val MIN_TASKS = 2
        private const val MAX_TASKS = 4
    }

    // 서버에서 받아온 전체 과업 목록
    private val allTasks = mutableListOf<Pair<Int, String>>() // taskId to taskName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guardian_chat)

        rvChat = findViewById(R.id.rvChat)
        etInput = findViewById(R.id.etInput)
        btnNext = findViewById(R.id.btnNext)
        layoutInput = findViewById(R.id.layoutInput)
        scrollChips = findViewById(R.id.scrollChips)
        chipGroup = findViewById(R.id.chipGroup)

        val lm = LinearLayoutManager(this)
        lm.stackFromEnd = true
        rvChat.layoutManager = lm
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

    // 서버 연결 실패 시 보여줄 기본 과업 목록
    private val fallbackTasks = listOf(
        Pair(0, "빨래하기"), Pair(0, "설거지하기"), Pair(0, "청소기 돌리기"),
        Pair(0, "밥 먹기"), Pair(0, "약 먹기")
    )

    // 과업 목록 먼저 받아온 뒤 채팅 시작
    private fun loadTasksAndStartChat() {
        ApiClient.get("/v1/guardian/tasks") { result ->
            when (result) {
                is ApiResult.Success -> {
                    android.util.Log.d("GuardianChat", "과업 목록 응답: ${result.data}")
                    try {
                        val arr = JSONObject(result.data).getJSONArray("tasks")
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            allTasks.add(Pair(obj.getInt("taskId"), obj.getString("taskName")))
                        }
                        android.util.Log.d("GuardianChat", "과업 ${allTasks.size}개 로드 완료")
                    } catch (e: Exception) {
                        android.util.Log.e("GuardianChat", "과업 목록 파싱 실패: ${e.message}")
                        allTasks.addAll(fallbackTasks)
                    }
                }
                is ApiResult.Error -> {
                    android.util.Log.e("GuardianChat", "과업 목록 로드 실패: ${result.message}")
                    // 서버 실패 시 기본 목록으로 대체
                    allTasks.addAll(fallbackTasks)
                }
            }
            // 목록 로드 성공/실패 상관없이 채팅은 시작
            startChat()
        }
    }

    private fun startChat() {
        val msg1 = "안녕하세요! 저는 눈길 도우미 똘똘이예요!"
        addTtoliMessage(msg1)
        tts.speak(msg1, TextToSpeech.QUEUE_FLUSH, null, "msg1")

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {}
            override fun onError(id: String) {}
            override fun onDone(id: String) {
                when (id) {
                    "msg1" -> {
                        val msg2 = "사용자가 해볼 수 있는 활동을 등록해주세요! 아래 목록에서 선택하거나 직접 입력할 수 있어요."
                        addTtoliMessage(msg2)
                        tts.speak(msg2, TextToSpeech.QUEUE_FLUSH, null, "msg2")
                    }
                    "msg2" -> {
                        runOnUiThread {
                            layoutInput.visibility = View.VISIBLE
                            showSuggestionChips()
                        }
                    }
                }
            }
        })
    }

    // 제안 칩 표시 (서버 목록 기반, 이미 등록된 항목 제외)
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
                    addUserMessage(taskName)
                    // 칩 탭은 이미 taskId를 알고 있으니 검색 없이 바로 등록
                    if (registeredTaskIds.contains(taskId)) {
                        addTtoliMessage("'${taskName}'은 이미 등록하셨어요!")
                    } else {
                        addTtoliMessage("'${taskName}' 확인했어요!")
                        registerToWhitelist(taskId, taskName)
                    }
                }
            }
            chipGroup.addView(chip)
        }
        scrollChips.visibility = View.VISIBLE
    }

    private fun sendInput() {
        val input = etInput.text.toString().trim()
        if (input.isEmpty()) return
        etInput.setText("")
        addUserMessage(input)
        searchAndRegister(input)
    }

    private fun searchAndRegister(input: String) {
        if (registeredTaskIds.size >= MAX_TASKS) {
            addTtoliMessage("이미 최대 ${MAX_TASKS}개를 등록하셨어요!")
            return
        }

        addTtoliMessage("잠깐만요, 확인해볼게요!")

        val encodedInput = URLEncoder.encode(input, "UTF-8")
        // 1단계: 과업 검색 (DB 저장 없이 taskId만 받아옴)
        ApiClient.get("/tasks/search?item=$encodedInput") { result ->
            when (result) {
                is ApiResult.Success -> {
                    android.util.Log.d("GuardianChat", "검색 응답: ${result.data}")
                    try {
                        val json = JSONObject(result.data)
                        val found = json.optBoolean("found", false)
                        if (found) {
                            val taskId = json.getInt("taskId")
                            val taskName = json.getString("taskName")

                            // 중복 체크
                            if (registeredTaskIds.contains(taskId)) {
                                addTtoliMessage("'${taskName}'은 이미 등록하셨어요! 다른 활동을 알려주세요.")
                                return@get
                            }

                            // 2단계: 화이트리스트에 즉시 개별 등록
                            registerToWhitelist(taskId, taskName)

                        } else {
                            addTtoliMessage("'${input}'을 찾지 못했어요. 아래 목록에서 선택하거나 다르게 입력해보세요!")
                        }
                    } catch (e: Exception) {
                        addTtoliMessage("응답 처리 중 오류가 발생했어요.")
                        android.util.Log.e("GuardianChat", "파싱 오류: ${result.data}")
                    }
                }
                is ApiResult.Error -> {
                    addTtoliMessage("서버 연결에 실패했어요. 다시 시도해주세요.")
                    android.util.Log.e("GuardianChat", "네트워크 오류: ${result.message}")
                }
            }
        }
    }

    // 화이트리스트 개별 등록 (검색 후 바로 호출)
    private fun registerToWhitelist(taskId: Int, taskName: String) {
        val body = JSONObject().apply {
            put("taskId", taskId)
        }.toString()

        val path = "/v1/guardian/settings/user/${Session.guardianId}/${Session.userIdx}/whitelist"
        ApiClient.post(path, body) { result ->
            when (result) {
                is ApiResult.Success -> {
                    registeredTaskIds.add(taskId)
                    registeredTaskNames.add(taskName)
                    addTtoliMessage("'${taskName}'을 등록했어요! (${registeredTaskIds.size}/$MAX_TASKS)")

                    if (registeredTaskIds.size >= MIN_TASKS) {
                        runOnUiThread { btnNext.visibility = View.VISIBLE }
                    }
                    if (registeredTaskIds.size < MAX_TASKS) {
                        addTtoliMessage("다른 활동도 추가할 수 있어요!")
                    } else {
                        addTtoliMessage("최대 개수를 채웠어요! '다음'을 눌러주세요.")
                    }
                    runOnUiThread { showSuggestionChips() }
                }
                is ApiResult.Error -> {
                    // 서버에서 MAX_ITEM_EXCEEDED 던질 경우 (클라이언트 MAX_TASKS 체크 뚫렸을 때)
                    val msg = if (result.message.contains("MAX_ITEM_EXCEEDED", ignoreCase = true)) {
                        "과업은 최대 ${MAX_TASKS}개까지만 등록할 수 있어요."
                    } else {
                        "등록 중 오류가 발생했어요. 다시 시도해주세요."
                    }
                    addTtoliMessage(msg)
                    android.util.Log.e("GuardianChat", "등록 오류: ${result.message}")
                }
            }
        }
    }

    // "다음" 버튼 - 이미 등록 완료됐으니 화면 이동만
    private fun saveWhitelistAndNext() {
        startActivity(Intent(this, SpecialNotesActivity::class.java))
        finish()
    }

    private fun addTtoliMessage(text: String) {
        runOnUiThread {
            chatList.add(ChatMessage(text, ChatMessage.TYPE_OTHER, false, null, null))
            adapter.notifyItemInserted(chatList.size - 1)
            rvChat.smoothScrollToPosition(chatList.size - 1)
        }
    }

    private fun addUserMessage(text: String) {
        runOnUiThread {
            chatList.add(ChatMessage(text, ChatMessage.TYPE_MINE, false, null, null))
            adapter.notifyItemInserted(chatList.size - 1)
            rvChat.smoothScrollToPosition(chatList.size - 1)
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
