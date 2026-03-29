package com.example.myapplication;

import android.app.AlertDialog;
import android.content.Intent; // Intent 추가
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class CaregiverLinkActivity extends AppCompatActivity {

    private UserAdapter adapter;
    private List<User> allUsers;
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_caregiver_link);

        // 1. 임시 데이터 생성
        allUsers = new ArrayList<>();
        allUsers.add(new User("김철수", "user01"));
        allUsers.add(new User("박민수", "user02"));
        allUsers.add(new User("이영희", "user03"));

        // 2. 뷰 연결
        EditText etSearchId = findViewById(R.id.etSearchId);
        Button btnSearch = findViewById(R.id.btnSearch);
        recyclerView = findViewById(R.id.recyclerView);

        // 3. 어댑터 초기화
        adapter = new UserAdapter(new ArrayList<>(), user -> {
            showConfirmDialog(user);
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // 4. 검색 로직
        btnSearch.setOnClickListener(v -> {
            String inputId = etSearchId.getText().toString().trim();
            if (inputId.isEmpty()) {
                Toast.makeText(this, "ID를 입력하세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            List<User> resultList = new ArrayList<>();
            for (User u : allUsers) {
                if (u.getId().equalsIgnoreCase(inputId)) {
                    resultList.add(u);
                }
            }

            if (resultList.size() > 0) {
                adapter.updateList(resultList);
                recyclerView.setVisibility(View.VISIBLE);
            } else {
                adapter.updateList(new ArrayList<>());
                Toast.makeText(this, "사용자를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showConfirmDialog(User user) {
        new AlertDialog.Builder(this)
                .setTitle("연동 확인")
                .setMessage(user.getName() + " (ID: " + user.getId() + ") 님과 연동하시겠습니까?")
                .setPositiveButton("예", (dialog, which) -> {
                    // ⭐ 5. 대기 화면으로 이동
                    Intent intent = new Intent(CaregiverLinkActivity.this, CaregiverWaitLinkActivity.class);
                    // 대기 화면에 선택한 유저 이름을 넘겨줄 수도 있습니다.
                    intent.putExtra("targetName", user.getName());
                    startActivity(intent);

                    // 현재 검색 화면은 닫음 (선택 사항)
                    finish();
                })
                .setNegativeButton("아니요", null)
                .show();
    }
}