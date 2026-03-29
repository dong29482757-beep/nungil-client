package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 1. Intent 생성 (현재화면.this, 이동할화면.class)
        Intent intent = new Intent(MainActivity.this, FirstActivity.class);

        // 2. 새로운 화면 실행
        startActivity(intent);

        // 3. 현재 화면(MainActivity)을 스택에서 제거 (뒤로가기 눌러도 안 나오게)
        finish();

    }
}