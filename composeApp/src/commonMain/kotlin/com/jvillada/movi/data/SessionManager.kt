package com.jvillada.movi.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

object SessionManager {
    private val settings: Settings by lazy { Settings() }

    var loggedIn: Boolean by mutableStateOf(!settings.getStringOrNull("auth_token").isNullOrBlank())
        private set

    private const val KEY_TOKEN   = "auth_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_NAME    = "user_name"
    private const val KEY_EMAIL   = "user_email"

    var token: String?
        get() = settings.getStringOrNull(KEY_TOKEN)
        set(v) { if (v == null) settings.remove(KEY_TOKEN) else settings[KEY_TOKEN] = v }

    var userId: String?
        get() = settings.getStringOrNull(KEY_USER_ID)
        set(v) { if (v == null) settings.remove(KEY_USER_ID) else settings[KEY_USER_ID] = v }

    var userName: String?
        get() = settings.getStringOrNull(KEY_NAME)
        set(v) { if (v == null) settings.remove(KEY_NAME) else settings[KEY_NAME] = v }

    var userEmail: String?
        get() = settings.getStringOrNull(KEY_EMAIL)
        set(v) { if (v == null) settings.remove(KEY_EMAIL) else settings[KEY_EMAIL] = v }

    val isLoggedIn: Boolean get() = !token.isNullOrBlank()

    fun save(token: String, userId: String, name: String, email: String) {
        this.token    = token
        this.userId   = userId
        this.userName = name
        this.userEmail = email
        loggedIn = true
    }

    fun clear() {
        settings.remove(KEY_TOKEN)
        settings.remove(KEY_USER_ID)
        settings.remove(KEY_NAME)
        settings.remove(KEY_EMAIL)
        loggedIn = false
    }
}
