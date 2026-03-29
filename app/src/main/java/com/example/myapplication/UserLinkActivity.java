package com.example.myapplication;

import android.content.Intent; // Intent 추가
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class UserLinkActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private RequestAdapter adapter;
    private List<User> requestDataList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 레이아웃 파일명이 activity_user_link인지 꼭 확인하세요!
        setContentView(R.layout.activity_user_link);

        // 1. 가상 데이터 생성
        requestDataList = new ArrayList<>();
        requestDataList.add(new User("박보호", "care_park01"));
        requestDataList.add(new User("이간병", "helper_lee"));
        requestDataList.add(new User("최가족", "family_choi"));

        // 2. 리사이클러뷰 설정
        recyclerView = findViewById(R.id.rvRequestList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 3. 어댑터 구현
        adapter = new RequestAdapter(requestDataList, new RequestAdapter.OnRequestClickListener() {
            @Override
            public void onAcceptClick(User user, int position) {
                // 수락 시 메시지
                Toast.makeText(UserLinkActivity.this, user.getName() + "님과 연동되었습니다.", Toast.LENGTH_SHORT).show();

                // ⭐ [수락] 클릭 시 바로 다음 화면(UserMainActivity)으로 이동
                Intent intent = new Intent(UserLinkActivity.this, UserMainActivity.class);
                // 연동된 보호자 이름을 다음 화면에 전달하고 싶다면 아래 줄 추가
                intent.putExtra("partnerName", user.getName());
                startActivity(intent);

                // 연동이 완료되었으므로 요청 화면은 종료
                finish();
            }

            @Override
            public void onRejectClick(User user, int position) {
                // 거절 시 목록에서만 삭제
                Toast.makeText(UserLinkActivity.this, "요청을 거절했습니다.", Toast.LENGTH_SHORT).show();
                removeItem(position);
            }
        });

        recyclerView.setAdapter(adapter);
    }

    private void removeItem(int position) {
        if (position >= 0 && position < requestDataList.size()) {
            requestDataList.remove(position);
            adapter.notifyItemRemoved(position);
            adapter.notifyItemRangeChanged(position, requestDataList.size());
        }
    }
}