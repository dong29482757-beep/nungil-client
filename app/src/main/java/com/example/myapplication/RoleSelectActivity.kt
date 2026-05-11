package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.core.network.Session
import com.example.myapplication.guardian.onboarding.SplashActivity
import com.example.myapplication.user.main.ui.UserChatActivity

class RoleSelectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Session.init(applicationContext)

        // 역할 이미 선택돼 있으면 바로 분기
        if (Session.hasRole()) {
            route(Session.role)
            return
        }

        setContentView(R.layout.activity_role_select)

        findViewById<android.widget.Button>(R.id.btnGuardian).setOnClickListener {
            confirmRole("보호자", Session.ROLE_GUARDIAN)
        }

        findViewById<android.widget.Button>(R.id.btnUser).setOnClickListener {
            confirmRole("사용자 (피보호자)", Session.ROLE_USER)
        }
    }

    private fun confirmRole(roleName: String, roleValue: String) {
        AlertDialog.Builder(this)
            .setTitle("역할 확정")
            .setMessage("'$roleName'으로 설정하시겠어요?\n한 번 선택하면 앱을 삭제하기 전까지 변경할 수 없어요.")
            .setPositiveButton("확정") { _, _ ->
                Session.role = roleValue
                route(roleValue)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun route(role: String) {
        val next = when (role) {
            Session.ROLE_USER -> Intent(this, UserChatActivity::class.java)
            else              -> Intent(this, SplashActivity::class.java)
        }
        startActivity(next)
        finish()
    }
}
