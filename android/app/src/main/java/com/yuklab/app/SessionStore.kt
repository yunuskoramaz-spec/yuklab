package com.yuklab.app

import android.content.Context

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("yuklab", Context.MODE_PRIVATE)

    var apiBaseUrl: String
        get() = prefs.getString(KEY_SERVER, BuildConfig.API_BASE_URL)?.trim()?.removeSuffix("/") ?: BuildConfig.API_BASE_URL
        set(value) { prefs.edit().putString(KEY_SERVER, value.trim().removeSuffix("/")).apply() }

    val accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }

    val refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }

    val role: String?
        get() = prefs.getString(KEY_ROLE, null)?.takeIf { it.isNotBlank() }

    fun saveSession(accessToken: String, refreshToken: String, role: String?) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_ROLE, role)
            .apply()
    }

    fun updateTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    fun updateRole(role: String) {
        prefs.edit().putString(KEY_ROLE, role).apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_ROLE)
            .apply()
    }

    fun saveDraft(key: String, value: String?) {
        val editor = prefs.edit()
        if (value == null) editor.remove(key) else editor.putString(key, value)
        editor.apply()
    }

    fun draft(key: String): String? = prefs.getString(key, null)

    companion object {
        const val DRAFT_ORDER = "draft_order"
        const val DRAFT_TEXT = "draft_text"
        private const val KEY_SERVER = "server"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ROLE = "role"
    }
}
