package com.example.myapplication.guardian.onboarding

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.core.network.Session
import com.example.myapplication.guardian.main.GuardianMainActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            val next = when {
                Session.isLoggedIn() && Session.isOnboarded ->
                    Intent(this, GuardianMainActivity::class.java)
                Session.isLoggedIn() && !Session.isOnboarded ->
                    Intent(this, WelcomeActivity::class.java)
                else ->
                    Intent(this, LoginActivity::class.java)
            }
            startActivity(next)
            finish()
        }, 1500)
    }
}
