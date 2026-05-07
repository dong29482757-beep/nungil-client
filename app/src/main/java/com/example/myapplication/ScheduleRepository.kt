package com.example.myapplication

import android.os.Build
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

object ScheduleRepository {

    private const val USE_DUMMY = false

    private const val BASE_URL = "https://reflector-carrousel-overstock.ngrok-free.dev/nungil-server/api/v1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class ScheduleItem(
        val scheduleId: Int,
        val title: String,
        val steps: List<String>,
        val triggerTimeMillis: Long,
        val location: String,
        val scheduleNote: String
    )

    data class UserInfo(
        val specialNote: String,
        val whitelistTaskNames: List<String>
    )

    // ──────────────────────────────────────────────
    // 2. 사용자 정보 조회
    // GET /api/v1/user/{userId}/{userIdx}
    // ──────────────────────────────────────────────
    fun fetchUserInfo(userId: String, userIdx: Int): UserInfo {
        if (USE_DUMMY) return dummyUserInfo()

        return try {
            val request = Request.Builder()
                .url("$BASE_URL/user/$userId/$userIdx")
                .get()
                .build()
            val body = client.newCall(request).execute().body?.string() ?: "{}"
            val root = JSONObject(body)
            if (root.optString("status") != "SUCCESS") return UserInfo("", emptyList())
            val result = root.getJSONObject("result")
            val tasksArr = result.optJSONArray("whiteList")
            val tasks = (0 until (tasksArr?.length() ?: 0)).map { tasksArr!!.getString(it) }
            UserInfo(
                specialNote        = result.optString("specialNote", ""),
                whitelistTaskNames = tasks
            )
        } catch (e: Exception) {
            Log.e("ScheduleRepo", "사용자 정보 조회 실패: ${e.message}")
            UserInfo("", emptyList())
        }
    }

    // ──────────────────────────────────────────────
    // 3. 오늘 일정 목록 조회
    // GET /api/v1/schedule?userId=&idx=
    // ──────────────────────────────────────────────
    fun fetchTodaySchedules(userId: String, userIdx: Int): List<ScheduleItem> {
        if (USE_DUMMY) return dummySchedules()

        return try {
            val request = Request.Builder()
                .url("$BASE_URL/schedule?userId=$userId&idx=$userIdx")
                .get()
                .build()
            val body = client.newCall(request).execute().body?.string() ?: "{}"
            val root = JSONObject(body)
            if (root.optString("status") != "SUCCESS") return emptyList()
            val arr = root.getJSONArray("result")
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ScheduleItem(
                    scheduleId        = obj.getInt("scheduleId"),
                    title             = obj.optString("taskName", ""),
                    steps             = parseTaskProcess(obj.optString("taskProcess", "[]")),
                    triggerTimeMillis = parseScheduledAt(obj.optString("scheduledAt", "")),
                    location          = obj.optString("location", ""),
                    scheduleNote      = obj.optString("specialNote", "")
                )
            }
        } catch (e: Exception) {
            Log.e("ScheduleRepo", "일정 조회 실패: ${e.message}")
            emptyList()
        }
    }

    // ──────────────────────────────────────────────
    // 3-1. 알람 발동 시 단계 재조회 (목록에서 필터)
    // ──────────────────────────────────────────────
    fun fetchStepsByScheduleId(scheduleId: Int, userId: String, userIdx: Int): Pair<List<String>, String> {
        if (USE_DUMMY) return dummySteps(scheduleId)

        return try {
            val schedules = fetchTodaySchedules(userId, userIdx)
            val item = schedules.find { it.scheduleId == scheduleId }
                ?: return Pair(emptyList(), "")
            val note = buildString {
                if (item.location.isNotBlank())    append("장소: ${item.location} ")
                if (item.scheduleNote.isNotBlank()) append("메모: ${item.scheduleNote}")
            }.trim()
            Pair(item.steps, note)
        } catch (e: Exception) {
            Log.e("ScheduleRepo", "단계 조회 실패: ${e.message}")
            Pair(emptyList(), "")
        }
    }

    // ──────────────────────────────────────────────
    // 3-2. 일정 완료 처리
    // PATCH /api/v1/schedule/{scheduleId}/complete
    // ──────────────────────────────────────────────
    fun completeSchedule(scheduleId: Int) {
        if (scheduleId < 0) return
        Thread {
            if (USE_DUMMY) { Log.d("ScheduleRepo", "[더미] 일정 완료: $scheduleId"); return@Thread }
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/schedule/$scheduleId/complete")
                    .patch(RequestBody.create(null, byteArrayOf()))
                    .build()
                client.newCall(request).execute()
            } catch (e: Exception) {
                Log.e("ScheduleRepo", "일정 완료 처리 실패: ${e.message}")
            }
        }.start()
    }

    // ──────────────────────────────────────────────
    // 4-1. 질문 로그 시작
    // POST /api/v1/question/log
    // ──────────────────────────────────────────────
    fun logQuestionStart(userId: String, userIdx: Int, content: String, answer: String): Int {
        if (USE_DUMMY) return (System.currentTimeMillis() % 10000).toInt()

        return try {
            val json = JSONObject()
                .put("userId", userId)
                .put("userIdx", userIdx)
                .put("content", content)
                .put("answer", answer)
            val body = RequestBody.create("application/json".toMediaTypeOrNull(), json.toString())
            val request = Request.Builder()
                .url("$BASE_URL/question/log")
                .post(body)
                .build()
            val res = JSONObject(client.newCall(request).execute().body?.string() ?: "{}")
            if (res.optString("status") != "SUCCESS") return -1
            res.getJSONObject("result").optInt("questionId", -1)
        } catch (e: Exception) {
            Log.e("ScheduleRepo", "질문 기록 실패: ${e.message}")
            -1
        }
    }

    // ──────────────────────────────────────────────
    // 4-2. 질문 완료 처리
    // PATCH /api/v1/question/log/{questionId}/complete
    // ──────────────────────────────────────────────
    fun logQuestionSuccess(questionId: Int) {
        if (questionId < 0) return
        Thread {
            if (USE_DUMMY) { Log.d("ScheduleRepo", "[더미] 질문 완료: $questionId"); return@Thread }
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/question/log/$questionId/complete")
                    .patch(RequestBody.create(null, byteArrayOf()))
                    .build()
                client.newCall(request).execute()
            } catch (e: Exception) {
                Log.e("ScheduleRepo", "질문 완료 처리 실패: ${e.message}")
            }
        }.start()
    }

    // ──────────────────────────────────────────────
    // 내부 파싱 유틸
    // ──────────────────────────────────────────────
    private fun parseTaskProcess(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) { emptyList() }
    }

    private fun parseScheduledAt(iso: String): Long {
        if (iso.isBlank()) return 0L
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                java.time.LocalDateTime.parse(iso)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli()
            } else {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(iso)?.time ?: 0L
            }
        } catch (e: Exception) { 0L }
    }

    // ──────────────────────────────────────────────
    // 더미 데이터
    // ──────────────────────────────────────────────
    private fun dummyUserInfo() = UserInfo(
        specialNote        = "큰 소리를 무서워함. 차분하게 말해주세요.",
        whitelistTaskNames = listOf("약 먹기", "손 씻기", "양치하기", "운동하기")
    )

    private fun dummySchedules(): List<ScheduleItem> {
        val fiveMinLater = System.currentTimeMillis() + 5 * 60 * 1000L
        return listOf(
            ScheduleItem(
                scheduleId        = 1,
                title             = "약 먹기",
                steps             = listOf("약통에서 오늘 약을 꺼내세요", "물 한 컵을 준비하세요", "약을 물과 함께 드세요"),
                triggerTimeMillis = fiveMinLater,
                location          = "주방",
                scheduleNote      = ""
            )
        )
    }

    private fun dummySteps(scheduleId: Int): Pair<List<String>, String> {
        return when (scheduleId) {
            1 -> Pair(
                listOf("약통에서 오늘 약을 꺼내세요", "물 한 컵을 준비하세요", "약을 물과 함께 드세요"),
                "장소: 주방"
            )
            else -> Pair(emptyList(), "")
        }
    }
}
