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
    private const val KEY_REMEMBERED_EMAIL = "remembered_email"

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

    /** Last email used to log in. Persists across logout so the login form can pre-fill it. */
    var rememberedEmail: String?
        get() = settings.getStringOrNull(KEY_REMEMBERED_EMAIL)
        set(v) { if (v == null) settings.remove(KEY_REMEMBERED_EMAIL) else settings[KEY_REMEMBERED_EMAIL] = v }

    val isLoggedIn: Boolean get() = !token.isNullOrBlank()

    private var consecutive401s = 0
    private const val MAX_CONSECUTIVE_401S = 3

    /** Call on every successful authenticated response to reset the 401 streak. */
    fun onAuthSuccess() { consecutive401s = 0 }

    /**
     * Call on every 401 response. Clears the session only after [MAX_CONSECUTIVE_401S]
     * consecutive failures — avoids logging out on a single transient background-sync 401.
     * Network errors (no connectivity) must NOT call this.
     */
    fun onUnauthorized() {
        consecutive401s++
        if (consecutive401s >= MAX_CONSECUTIVE_401S) clear()
    }

    fun save(token: String, userId: String, name: String, email: String) {
        this.token    = token
        this.userId   = userId
        this.userName = name
        this.userEmail = email
        this.rememberedEmail = email  // kept across clear() so the next login pre-fills it
        consecutive401s = 0
        loggedIn = true
    }

    fun clear() {
        settings.remove(KEY_TOKEN)
        settings.remove(KEY_USER_ID)
        settings.remove(KEY_NAME)
        settings.remove(KEY_EMAIL)
        consecutive401s = 0
        loggedIn = false
        // Ver Platform.kt: en wasmJs esto recarga la página para que el overlay HTML nativo
        // retome el control. clear() tiene dos llamadores (el logout explícito de Perfil y el
        // logout forzado de onUnauthorized tras 401s repetidos) y a los dos les hace falta —
        // los dos dejan a Compose sin sesión, y en la web eso sin recargar es el segundo login
        // de vuelta. En Android/iOS es un no-op.
        reloadForLogout()
    }
}
