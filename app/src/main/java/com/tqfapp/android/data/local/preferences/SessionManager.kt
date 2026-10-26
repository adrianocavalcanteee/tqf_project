package com.tqfapp.android.data.local.preferences

import android.content.Context

class SessionManager(context: Context) {

    private val prefs = context.getSharedPreferences(
        "tqf_session",
        Context.MODE_PRIVATE
    )

    fun saveLogin(userId: String) {
        prefs.edit()
            .putString("user_id", userId)
            .putBoolean("logged_in", true)
            .apply()
    }

    fun getUserId(): String? {
        return prefs.getString("user_id", null)
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean("logged_in", false)
    }

    fun logout() {
        prefs.edit().clear().apply()
    }
}