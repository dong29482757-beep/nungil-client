package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class FirstActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first);

        EditText etName = findViewById(R.id.etName);
        Button btnSelectUser = findViewById(R.id.btnSelectUser);
        Button btnSelectCaregiver = findViewById(R.id.btnSelectCaregiver);

        // User 버튼 클릭 시
        btnSelectUser.setOnClickListener(v -> {
            String name = etName.getText().toString();
            if (name.isEmpty()) {
                Toast.makeText(this, "이름을 먼저 입력해주세요", Toast.LENGTH_SHORT).show();
            } else {
                moveToMain(name, "User");
            }
        });

        // Caregiver 버튼 클릭 시
        btnSelectCaregiver.setOnClickListener(v -> {
            String name = etName.getText().toString();
            if (name.isEmpty()) {
                Toast.makeText(this, "이름을 먼저 입력해주세요", Toast.LENGTH_SHORT).show();
            } else {
                moveToMain(name, "Caregiver");
            }
        });
    }

    private void moveToMain(String name, String role) {
        Intent intent;
        if(role.equals("User")){
            intent = new Intent(FirstActivity.this, UserLinkActivity.class);
        } else {
            intent = new Intent(FirstActivity.this, CaregiverLinkActivity.class);
        }

        intent.putExtra("my_name", name);
        intent.putExtra("my_role", role);

        startActivity(intent);
    }
}