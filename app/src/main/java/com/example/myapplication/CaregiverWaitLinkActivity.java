package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class CaregiverWaitLinkActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_caregiver_wait_link);

        Button btnRefresh = findViewById(R.id.btnRefresh);

        btnRefresh.setOnClickListener(v -> {
            checkLinkStatus();
        });
    }

    private void checkLinkStatus() {
        // 🔍 실제로는 여기서 서버 DB에 API 요청을 보내야 합니다.
        // 예: GET /check-link?myId=caregiver01

        // 임시 로직 (랜덤하게 수락되었다고 가정)
        boolean isAccepted = Math.random() > 0.5;

        if (isAccepted) {
            Toast.makeText(this, "연동이 완료되었습니다!", Toast.LENGTH_SHORT).show();

            // 연동 완료 후 메인 기능 화면으로 이동
            Intent intent = new Intent(CaregiverWaitLinkActivity.this, CaregiverMainActivity.class);
            startActivity(intent);
            finish(); // 대기 화면은 종료
        } else {
            Toast.makeText(this, "아직 상대방이 수락하지 않았습니다.", Toast.LENGTH_SHORT).show();
        }
    }
}