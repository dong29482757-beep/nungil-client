package com.example.myapplication.guardian.onboarding

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.guardian.main.GuardianMainActivity

// IS-004 : 초기 설정 완료
class SetupCompleteActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_complete)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            startActivity(Intent(this, GuardianMainActivity::class.java))
            finish()
        }
    }
}
