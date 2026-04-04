package com.example.myapplication;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class UserChatActivity extends AppCompatActivity {
    private List<ChatMessage> chatList = new ArrayList<>();
    private ChatAdapter adapter;
    private RecyclerView rvChat;

    private String serverDescription = "";
    private List<String> serverSteps = new ArrayList<>();
    private int currentStepIndex = 0;
    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_chat);

        // 1. 뷰 연결 및 리사이클러뷰 설정
        rvChat = findViewById(R.id.rvChat);
        adapter = new ChatAdapter(chatList);
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(adapter);

        final View bottomBarBg = findViewById(R.id.bottomBarBg);

        // 2. [핵심] 기기별 하단 내비게이션 바 대응 로직
        // 기존 채팅 로직과 별개로 실행되므로 충돌 걱정 NO!
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainLayout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) bottomBarBg.getLayoutParams();

            // 시스템 하단바 높이 + 우리가 원하는 최소 여백(30dp)
            int extraMargin = (int) (30 * getResources().getDisplayMetrics().density);
            params.bottomMargin = systemBars.bottom + extraMargin;

            bottomBarBg.setLayoutParams(params);
            return insets;
        });

        // 첫 환영 인사
        rvChat.postDelayed(() -> addMsg("안녕하세요! 궁금한 물건을 찍어주세요.", ChatMessage.TYPE_OTHER, false, null), 500);

        // 3. 버튼 리스너 (기존 로직 유지)
        findViewById(R.id.btnCamera).setOnClickListener(v -> {
            startActivityForResult(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), 1);
        });

        findViewById(R.id.btnNext).setOnClickListener(v -> {
            if (!serverSteps.isEmpty() && currentStepIndex < serverSteps.size()) {
                addMsg("다음 사용법 알려줘", ChatMessage.TYPE_MINE, false, null);
                processNextGuideStep();
            } else {
                Toast.makeText(this, "먼저 촬영을 완료해주세요!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- 기존의 서버 통신 및 메시지 처리 로직들 (변화 없음) ---

    private void sendTextRequestToServer(String questionText) {
        String url = "http://10.100.0.46:8080/nungil-server/api/v1/vision/ask";
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("question", questionText)
                .build();
        Request request = new Request.Builder().url(url).post(requestBody).build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(UserChatActivity.this, "연결 실패", Toast.LENGTH_SHORT).show());
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    final String responseData = response.body().string();
                    runOnUiThread(() -> parseAndInitializeData(responseData));
                }
            }
        });
    }

    private void parseAndInitializeData(String jsonString) {
        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            JSONObject resultObject = jsonObject.getJSONObject("result");
            serverDescription = resultObject.optString("description", "");
            JSONArray stepsArray = resultObject.optJSONArray("usage_steps");

            serverSteps.clear(); currentStepIndex = 0;
            if (stepsArray != null) {
                for (int i = 0; i < stepsArray.length(); i++) serverSteps.add(stepsArray.getString(i));
            }
            if (!serverDescription.isEmpty()) {
                addMsg(serverDescription, ChatMessage.TYPE_OTHER, false, null);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void processNextGuideStep() {
        if (currentStepIndex < serverSteps.size()) {
            String currentStepText = serverSteps.get(currentStepIndex);
            String finalMessage = (currentStepIndex == serverSteps.size() - 1) ?
                    currentStepText : currentStepText + "\n\n단계를 완료하면 말해주세요.";
            addMsg(finalMessage, ChatMessage.TYPE_OTHER, false, null);
            currentStepIndex++;
        }
    }

    @Override
    protected void onActivityResult(int req, int res, @Nullable Intent d) {
        super.onActivityResult(req, res, d);
        if (res == RESULT_OK && req == 1 && d != null) {
            Bitmap b = (Bitmap) d.getExtras().get("data");
            addMsg(null, ChatMessage.TYPE_MINE, true, b);
            sendTextRequestToServer("사용법 알려줘");
        }
    }

    private void addMsg(String c, int t, boolean i, Bitmap b) {
        chatList.add(new ChatMessage(c, t, i, b));
        adapter.notifyItemInserted(chatList.size() - 1);
        rvChat.scrollToPosition(chatList.size() - 1);
    }
}