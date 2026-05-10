package com.example.myapplication.core.network

import android.content.Context
import android.content.SharedPreferences

object Session {
    private const val PREF_NAME = "nungil_session"
    private const val KEY_ID   = "guardianId"
    private const val KEY_IDX  = "userIdx"
    private const val KEY_NAME = "guardianName"
    private const val KEY_ONBOARDED = "isOnboarded"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    var guardianId: String
        get() = prefs?.getString(KEY_ID, "hong123") ?: "hong123"
        set(v) { prefs?.edit()?.putString(KEY_ID, v)?.apply() }

    var userIdx: Int
        get() = prefs?.getInt(KEY_IDX, 1) ?: 1
        set(v) { prefs?.edit()?.putInt(KEY_IDX, v)?.apply() }

    var guardianName: String
        get() = prefs?.getString(KEY_NAME, "") ?: ""
        set(v) { prefs?.edit()?.putString(KEY_NAME, v)?.apply() }

    var isOnboarded: Boolean
        get() = prefs?.getBoolean(KEY_ONBOARDED, false) ?: false
        set(v) { prefs?.edit()?.putBoolean(KEY_ONBOARDED, v)?.apply() }

    fun isLoggedIn() = (prefs?.contains(KEY_ID) == true) && guardianId.isNotEmpty()

    fun logout() = prefs?.edit()?.clear()?.apply()
}
