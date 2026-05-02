package com.example.myapplication.guardian.userchat.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.common.model.ChatMessage;

import org.json.JSONArray;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.*;

public class UserChatActivity extends AppCompatActivity implements ChatAdapter.OnSuggestionClickListener, RecognitionListener {

    // 서버 URL (스크린샷 기준)
    private final String SERVER_URL = "http://10.100.0.51:8080/nungil-server/api/v1/nungil/analyze";
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private List<ChatMessage> chatList = new ArrayList<>();
    private ChatAdapter adapter;
    private RecyclerView rvChat;
    private ProgressBar loadingBar;

    private SpeechService speechService;
    private Model model;
    private MediaRecorder recorder;
    private File voiceFile;
    private boolean isRecording = false;
    private int voiceMsgIndex = -1;

    // [핵심] 새로운 사진이 찍히기 전까지 유지될 비트맵 변수
    private Bitmap lastCapturedBitmap = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_chat);

        rvChat = findViewById(R.id.rvChat);
        loadingBar = findViewById(R.id.loadingBar);
        adapter = new ChatAdapter(chatList, this);

        // 리사이클러뷰 설정 (Padding과 clipToPadding은 XML에서 설정 권장)
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(adapter);

        checkPermissions();

        // 카메라 버튼
        findViewById(R.id.btnCamera).setOnClickListener(v ->
                startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), 1));

        // 마이크 버튼
        findViewById(R.id.btnNext).setOnClickListener(v -> {
            if (!isRecording && loadingBar.getVisibility() != View.VISIBLE) {
                Toast.makeText(this, "어떤 것이 궁금하신가요? 말씀해주세요!", Toast.LENGTH_SHORT).show();
                stopVosk();
                startVoiceRecording();
            }
        });

        addMsg("반가워요! 사진을 찍거나 '똘똘아'라고 불러보세요.", ChatMessage.TYPE_OTHER, false, null, null);
    }

    /**
     * 서버 전송 로직: 새로운 사진이 없으면 lastCapturedBitmap을 재사용하여 전송
     */
    private void uploadToServer(@Nullable Bitmap newBitmap, @Nullable File voice, @Nullable String text) {
        stopVosk(); // 서버 요청 중 호출어 인식 방지
        runOnUiThread(() -> loadingBar.setVisibility(View.VISIBLE));

        MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);

        // 1. 공통 ID 정보
        builder.addFormDataPart("info", "{\"id\": \"ttoli_user_01\"}");

        // 2. 텍스트 정보 (말한 내용 혹은 추천 질문)
        if (text != null) builder.addFormDataPart("text", text);

        // 3. 음성 파일 정보
        if (voice != null) {
            builder.addFormDataPart("voice", "voice_input.m4a",
                    RequestBody.create(voice, MediaType.parse("audio/m4a")));
        }

        // 4. [이미지 컨텍스트 로직]
        if (newBitmap != null) {
            // 새로 사진을 찍은 경우: 변수를 갱신하고 새 사진을 보냄
            lastCapturedBitmap = newBitmap;
            builder.addFormDataPart("image", "current_shot.jpg",
                    RequestBody.create(bitmapToFile(newBitmap), MediaType.parse("image/jpeg")));
        } else if (lastCapturedBitmap != null) {
            // 새로 찍은 사진은 없지만, 기존에 찍어둔 사진이 있는 경우: 기존 사진을 다시 보냄
            builder.addFormDataPart("image", "current_shot.jpg",
                    RequestBody.create(bitmapToFile(lastCapturedBitmap), MediaType.parse("image/jpeg")));
        }

        Request request = new Request.Builder().url(SERVER_URL).post(builder.build()).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    loadingBar.setVisibility(View.GONE);
                    updateVoiceStatus("❌ 서버 연결 실패");
                    resetToVoskMode();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body().string();
                new Handler(Looper.getMainLooper()).post(() -> {
                    loadingBar.setVisibility(View.GONE);
                    if (response.isSuccessful()) {
                        parseServerResponse(body);
                    } else {
                        updateVoiceStatus("❌ 서버 응답 오류");
                    }
                    resetToVoskMode();
                });
            }
        });
    }

    private void parseServerResponse(String json) {
        try {
            JSONObject root = new JSONObject(json);
            if ("SUCCESS".equals(root.optString("status"))) {
                JSONObject result = root.getJSONObject("result");

                // transcribedText 업데이트
                String transcribed = result.optString("transcribedText", "");
                if (!transcribed.isEmpty()) {
                    updateVoiceStatus(transcribed);
                    voiceMsgIndex = -1;
                }

                // 답변 및 추천 질문 추가
                String answer = result.optString("answer", "");
                List<String> suggestions = new ArrayList<>();
                JSONArray suggestArray = result.optJSONArray("suggestedQuestions");
                if (suggestArray != null) {
                    for (int i = 0; i < suggestArray.length(); i++) {
                        suggestions.add(suggestArray.getString(i));
                    }
                }
                addMsg(answer, ChatMessage.TYPE_OTHER, false, null, suggestions);
            }
        } catch (Exception e) {
            updateVoiceStatus("❌ 데이터 처리 오류");
        }
    }

    // --- 호출어 감지 (Vosk) 파트 ---

    @Override
    public void onResult(String hypothesis) {
        try {
            JSONObject json = new JSONObject(hypothesis);
            String text = json.optString("text", "").trim();

            if (text.isEmpty() || isRecording || loadingBar.getVisibility() == View.VISIBLE) return;

            // [잡음 방지] 딱 "똘똘" 혹은 "똘똘아"만 정확히 인식되었을 때(글자수 제한 및 정규식)만 실행
            if (text.matches("^(똘똘|똘똘아)$")) {
                Log.d("Vosk", "호출 감지: " + text);
                runOnUiThread(() -> {
                    Toast.makeText(this, "네, 말씀하세요!", Toast.LENGTH_SHORT).show();
                    stopVosk();
                    startVoiceRecording();
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override public void onPartialResult(String s) { /* 오작동 방지를 위해 비워둠 */ }

    // --- 녹음 로직 ---

    private void startVoiceRecording() {
        isRecording = true;
        runOnUiThread(() -> {
            chatList.add(new ChatMessage("🎤 듣고 있습니다...", ChatMessage.TYPE_MINE, false, null, null));
            voiceMsgIndex = chatList.size() - 1;
            adapter.notifyItemInserted(voiceMsgIndex);
            rvChat.smoothScrollToPosition(voiceMsgIndex);
        });

        try {
            voiceFile = new File(getExternalFilesDir(null), "voice_input.m4a");
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(voiceFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            new Handler(Looper.getMainLooper()).postDelayed(this::stopAndUploadVoice, 5000);
        } catch (Exception e) { resetToVoskMode(); }
    }

    private void stopAndUploadVoice() {
        if (recorder != null && isRecording) {
            try { recorder.stop(); recorder.release(); } catch (Exception e) {}
            recorder = null; isRecording = false;
            updateVoiceStatus("🔍 분석 중...");
            uploadToServer(null, voiceFile, null); // 녹음본 전송 (기존 이미지가 있다면 함께 감)
        }
    }

    private void updateVoiceStatus(String newText) {
        runOnUiThread(() -> {
            if (voiceMsgIndex != -1 && voiceMsgIndex < chatList.size()) {
                chatList.get(voiceMsgIndex).setContent(newText);
                adapter.notifyItemChanged(voiceMsgIndex);
            }
        });
    }

    // --- 엔진 및 기타 설정 ---

    private void startVosk() {
        try {
            if (speechService != null || isRecording) return;
            // 인식 범위를 똘똘, 똘똘아로 좁혀서 잡음 간섭 최소화
            Recognizer rec = new Recognizer(model, 16000.0f, "[\"똘똘\", \"똘똘아\"]");
            speechService = new SpeechService(rec, 16000.0f);
            speechService.startListening(this);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void stopVosk() { if (speechService != null) { speechService.stop(); speechService.shutdown(); speechService = null; } }

    private void resetToVoskMode() { isRecording = false; if (recorder != null) { try { recorder.release(); } catch(Exception e){} } recorder = null; startVosk(); }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA}, 100);
        } else { initVosk(); }
    }

    private void initVosk() {
        StorageService.unpack(this, "model-ko", "model", (m) -> { this.model = m; startVosk(); }, (e) -> Log.e("Vosk", "Load Fail"));
    }

    private void addMsg(String c, int t, boolean i, Bitmap b, List<String> s) {
        runOnUiThread(() -> {
            chatList.add(new ChatMessage(c, t, i, b, s));
            adapter.notifyItemInserted(chatList.size()-1);
            rvChat.smoothScrollToPosition(chatList.size()-1);
        });
    }

    @Override public void onSuggestionClick(String t) {
        if (loadingBar.getVisibility() == View.VISIBLE) return;
        addMsg(t, ChatMessage.TYPE_MINE, false, null, null);
        uploadToServer(null, null, t); // 추천 질문 클릭 시에도 기존 사진이 있으면 함께 감
    }

    @Override protected void onActivityResult(int req, int res, @Nullable Intent d) {
        super.onActivityResult(req, res, d);
        if (res == RESULT_OK && req == 1 && d != null) {
            Bitmap b = (Bitmap) d.getExtras().get("data");
            addMsg(null, ChatMessage.TYPE_MINE, true, b, null);
            uploadToServer(b, null, null); // 새 사진 촬영 및 전송 (lastCapturedBitmap 갱신)
        }
    }

    private File bitmapToFile(Bitmap b) {
        File f = new File(getExternalFilesDir(null), "current_shot.jpg");
        try (FileOutputStream o = new FileOutputStream(f)) { b.compress(Bitmap.CompressFormat.JPEG, 90, o); } catch (Exception e) {}
        return f;
    }

    @Override public void onError(Exception e) { resetToVoskMode(); }
    @Override public void onTimeout() { resetToVoskMode(); }
    @Override public void onFinalResult(String s) { }
    @Override protected void onDestroy() { super.onDestroy(); stopVosk(); if (recorder != null) recorder.release(); }
}