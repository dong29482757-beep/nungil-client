package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.core.network.Session
import com.example.myapplication.guardian.main.GuardianMainActivity
import com.example.myapplication.guardian.onboarding.LoginActivity
import com.example.myapplication.guardian.onboarding.WelcomeActivity
import com.example.myapplication.user.UserStartActivity

class UserOrGuardianActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_or_guardian)

        // 사용자 카드 클릭
        findViewById<View>(R.id.cardUser).setOnClickListener {
            startActivity(Intent(this, UserStartActivity::class.java))
        }

        // 보호자 카드 클릭
        findViewById<View>(R.id.cardGuardian).setOnClickListener {
            // 보호자는 세션 상태에 따라 로그인/온보딩/메인으로 분기
            val nextIntent = when {
                Session.isLoggedIn() && Session.isOnboarded ->
                    Intent(this, GuardianMainActivity::class.java)
                Session.isLoggedIn() && !Session.isOnboarded ->
                    Intent(this, WelcomeActivity::class.java)
                else ->
                    Intent(this, LoginActivity::class.java)
            }
            startActivity(nextIntent)
        }
    }
}