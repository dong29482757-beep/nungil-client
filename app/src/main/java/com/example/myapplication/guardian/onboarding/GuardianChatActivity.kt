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
import com.example.myapplication.guardian.userchat.ui.ChatAdapter
import com.example.myapplication.common.model.ChatMessage
import com.example.myapplication.R
import com.example.myapplication.core.network.ApiClient
import com.example.myapplication.core.network.Session
import org.json.JSONObject
import java.util.Locale

class GuardianChatActivity : AppCompatActivity() {

    private lateinit var tts: TextToSpeech
    private val chatList = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter
    private lateinit var rvChat: RecyclerView
    private lateinit var etInput: EditText
    private lateinit var btnNext: Button
    private lateinit var layoutInput: View

    private val registeredTaskIds = mutableListOf<Int>()

    companion object {
        private const val MIN_TASKS = 2
        private const val MAX_TASKS = 4
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guardian_chat)

        rvChat = findViewById(R.id.rvChat)
        etInput = findViewById(R.id.etInput)
        btnNext = findViewById(R.id.btnNext)
        layoutInput = findViewById(R.id.layoutInput)

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
                startChat()
            }
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
                        val msg2 = "사용자님이 해보고 싶은 활동을 알려주세요. 제가 단계별로 도와드릴게요!"
                        addTtoliMessage(msg2)
                        tts.speak(msg2, TextToSpeech.QUEUE_FLUSH, null, "msg2")
                    }
                    "msg2" -> {
                        addTtoliMessage("예를 들어 '빨래하기', '설거지하기' 같은 활동이에요!")
                        runOnUiThread { layoutInput.visibility = View.VISIBLE }
                    }
                }
            }
        })
    }

    private fun sendInput() {
        val input = etInput.text.toString().trim()
        if (input.isEmpty()) return
        etInput.setText("")

        if (registeredTaskIds.size >= MAX_TASKS) {
            addTtoliMessage("이미 최대 ${MAX_TASKS}개를 등록하셨어요!")
            return
        }

        addUserMessage(input)
        addTtoliMessage("잠깐만요, 확인해볼게요!")

        ApiClient.get("/task/search?name=$input") { response ->
            if (response == null) {
                addTtoliMessage("서버 연결에 실패했어요. 다시 시도해주세요.")
                return@get
            }
            val json = JSONObject(response)
            val found = json.optBoolean("found", false)
            if (found) {
                val taskId = json.getInt("taskId")
                val taskName = json.getString("taskName")
                registeredTaskIds.add(taskId)
                addTtoliMessage("${taskName}를 등록했어요! (${registeredTaskIds.size}/$MAX_TASKS)")
                if (registeredTaskIds.size >= MIN_TASKS) {
                    runOnUiThread { btnNext.visibility = View.VISIBLE }
                    if (registeredTaskIds.size < MAX_TASKS) {
                        addTtoliMessage("다른 활동도 알려주세요. 또는 다음으로 넘어가세요!")
                    }
                }
            } else {
                addTtoliMessage("아직 '${input}'은 지원하지 않아요. 다른 활동을 알려주세요!")
            }
        }
    }

    private fun saveWhitelistAndNext() {
        val body = JSONObject().apply {
            put("guardianId", Session.guardianId)
            put("idx", Session.userIdx)
            put("taskIds", org.json.JSONArray(registeredTaskIds))
        }.toString()

        ApiClient.post("/whitelist", body) { response ->
            runOnUiThread {
                startActivity(Intent(this, SpecialNotesActivity::class.java))
                finish()
            }
        }
    }

    private fun addTtoliMessage(text: String) {
        runOnUiThread {
            chatList.add(ChatMessage(text, ChatMessage.TYPE_OTHER, false, null, null))
            adapter.notifyItemInserted(chatList.size - 1)
            rvChat.smoothScrollToPosition(chatList.size - 1)
        }
    }

    private fun addUserMessage(text: String) {
        chatList.add(ChatMessage(text, ChatMessage.TYPE_MINE, false, null, null))
        adapter.notifyItemInserted(chatList.size - 1)
        rvChat.smoothScrollToPosition(chatList.size - 1)
    }

    override fun onDestroy() {
        tts.stop(); tts.shutdown()
        super.onDestroy()
    }
}
