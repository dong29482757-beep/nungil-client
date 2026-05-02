package com.example.myapplication.guardian.onboarding

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R

// IS-003 : 사용자 특이사항 입력
class SpecialNotesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_special_notes)

        findViewById<Button>(R.id.btnNext).setOnClickListener {
            // TODO: etNotes 내용 서버 저장
            startActivity(Intent(this, SetupCompleteActivity::class.java))
        }
    }
}
