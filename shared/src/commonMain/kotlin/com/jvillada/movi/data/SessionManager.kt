package com.jvillada.movi.data

import com.jvillada.movi.ui.dashboard.DashboardDataCache

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

private const val KEY_TOKEN   = "auth_token"
private const val KEY_USER_ID = "user_id"
private const val KEY_NAME    = "user_name"
private const val KEY_EMAIL   = "user_email"
// F42 · F46: color elegido para el avatar de iniciales — se llena la primera vez que
// PerfilScreen pide el perfil (GET /api/users/me) y se actualiza tras cada edición. Vive en
// Settings (no solo en memoria) para que sobreviva un reinicio de la app en Android/iOS,
// igual que userName/userEmail.
private const val KEY_AVATAR_COLOR = "avatar_color"
private const val KEY_REMEMBERED_EMAIL = "remembered_email"
// F1: preferencia explícita de la casilla "Recordar mi correo en este dispositivo"
// del login web (index.html). "0" = no recordar; ausente o "1" = sí (por defecto,
// y también el comportamiento de siempre en Android/iOS, que no tienen la casilla).
private const val KEY_REMEMBER_PREF = "remember_email_pref"

/**
 * **El almacenamiento del dispositivo, que puede no existir.**
 *
 * Top-level y `by lazy`, igual que en `LastAccountStore` y `UsedCategoriesCache`, y por el mismo
 * motivo que allá: `Settings()` **explota al construirse, no al leerse**. En wasm es
 * `StorageSettings(localStorage)`, y `window.localStorage` tira `SecurityError` apenas se lo toca
 * cuando el navegador tiene bloqueado el almacenamiento del sitio (modo incógnito con datos
 * bloqueados, la opción «Bloquear datos de sitios», una política de empresa). En un test de JVM
 * pura tampoco hay `Settings` que valga.
 *
 * El `by lazy` difiere esa construcción hasta la primera lectura, y todas las lecturas y
 * escrituras de acá abajo pasan por [leer] / [guardar], que la envuelven en `runCatching`. Sin
 * eso, la excepción caía **en el inicializador del object**, y una init que lanza se lleva puesta
 * la clase entera: `SessionManager` quedaba inutilizable y con él toda la app, porque no hay
 * pantalla de Movi que no lo toque. Es exactamente lo que ya había pasado con `AppTimeZone`
 * llamando a `TimeZone.of("America/Bogota")` en su init — wasm no trae la base de zonas IANA y
 * eso tumbó el Inicio entero.
 *
 * En la web el efecto era total: `index.html` ya se protege solo (todo su script vive adentro de
 * un `try`), así que con el almacenamiento bloqueado su overlay de login se escondía y le pasaba
 * la posta al login de Compose... que no podía dibujarse porque este object estaba muerto. La red
 * de seguridad que ese archivo dice tener no existía.
 */
private val sessionSettings: Settings by lazy { Settings() }

/**
 * **Copia en memoria, para que un almacenamiento bloqueado no deje a nadie afuera.**
 *
 * No alcanza con no explotar: sin ningún lado donde anotar el token, entrar sería imposible
 * —guardar la sesión no haría nada y el siguiente pedido saldría sin `Authorization`—, o sea que
 * la app cargaría solo para rebotar en el login para siempre.
 *
 * Con esto, una sesión abierta con el almacenamiento bloqueado funciona completa **mientras dure
 * la pestaña o el proceso**; lo único que se pierde es sobrevivir a un reinicio, que es
 * literalmente lo que el navegador nos está prohibiendo. No cambia nada cuando el almacenamiento
 * sí anda: [leer] pregunta primero por [sessionSettings] y solo cae acá si esa lectura lanzó.
 */
private val sessionMemoria = mutableMapOf<String, String>()

private fun leer(key: String): String? =
    runCatching { sessionSettings.getStringOrNull(key) }.getOrElse { sessionMemoria[key] }

private fun guardar(key: String, value: String?) {
    if (value == null) sessionMemoria.remove(key) else sessionMemoria[key] = value
    runCatching {
        if (value == null) sessionSettings.remove(key) else sessionSettings[key] = value
    }
}

object SessionManager {
    var loggedIn: Boolean by mutableStateOf(!leer(KEY_TOKEN).isNullOrBlank())
        private set

    var token: String?
        get() = leer(KEY_TOKEN)
        set(v) = guardar(KEY_TOKEN, v)

    var userId: String?
        get() = leer(KEY_USER_ID)
        set(v) = guardar(KEY_USER_ID, v)

    var userName: String?
        get() = leer(KEY_NAME)
        set(v) = guardar(KEY_NAME, v)

    var userEmail: String?
        get() = leer(KEY_EMAIL)
        set(v) = guardar(KEY_EMAIL, v)

    /**
     * `null` hasta que PerfilScreen haga su primer `GET /api/users/me` en esta sesión — no en el
     * login, que no lo devuelve (AuthResponse no cambió: register/login son ajenos a esta
     * tarea). [com.jvillada.movi.ui.components.AvatarButton] cae a
     * [com.jvillada.movi.shared.model.AvatarPalette.DEFAULT] mientras tanto, que es exactamente
     * lo que el server devuelve para una cuenta que nunca eligió color — no hay descalce.
     */
    var avatarColor: String?
        get() = leer(KEY_AVATAR_COLOR)
        set(v) = guardar(KEY_AVATAR_COLOR, v)

    /** Last email used to log in. Persists across logout so the login form can pre-fill it. */
    var rememberedEmail: String?
        get() = leer(KEY_REMEMBERED_EMAIL)
        set(v) = guardar(KEY_REMEMBERED_EMAIL, v)

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
        guardar(KEY_TOKEN, null)
        guardar(KEY_USER_ID, null)
        guardar(KEY_NAME, null)
        guardar(KEY_EMAIL, null)
        guardar(KEY_AVATAR_COLOR, null)
        // F1: el correo recordado solo sobrevive al logout si la persona lo eligió con
        // la casilla del login web. Sin esa preferencia (Android/iOS, o quien nunca la
        // vio) se preserva como siempre — no forzamos un opt-in donde no hay casilla.
        if (leer(KEY_REMEMBER_PREF) == "0") {
            guardar(KEY_REMEMBERED_EMAIL, null)
        }
        consecutive401s = 0
        // Lo que el Inicio tenía cacheado es de la sesión que se va: sin esto, en Android/iOS
        // (donde no se recarga la página) el próximo usuario vería por un instante el balance y
        // las alertas del anterior, y si alguna carga fallara en silencio, se quedarían.
        ScreenDefCache.dashboard = null
        DashboardDataCache.clear()
        // Ola 9: las categorías usadas y lo que ya se ofreció como recurrente también son del
        // usuario que se va — sugerirle al siguiente las categorías del anterior sería filtrar
        // algo suyo por una lista de autocompletado.
        UsedCategoriesCache.clear()
        RecurringOfferGate.clear()
        // Y los canales de aviso: `emailTo` es la dirección del usuario que se va, y decirle al
        // siguiente «te avisamos por correo a juan@…» sería mostrarle un dato ajeno.
        ReminderChannelsCache.clear()
        // Ola 11: y la última cuenta usada, por lo mismo — es una cuenta del usuario que se va.
        LastAccountStore.clear()
        // Ver Platform.kt: en wasmJs esto recarga la página para que el overlay HTML nativo
        // retome el control. Le hace falta a TODOS los caminos que terminan una sesión —hoy el
        // logout explícito de Perfil, el forzado de onUnauthorized tras 401s repetidos, y tres
        // llamadores más del lado Android— porque todos dejan a Compose sin sesión, y en la web
        // eso sin recargar es el segundo login de vuelta. En Android/iOS es un no-op.
        //
        // El orden que SÍ importa es respecto del borrado de arriba: recargar con el token
        // todavía guardado haría que la página vuelva y entre sola, o sea que el logout no
        // cerraría nada.
        //
        // Respecto de `loggedIn = false` da igual, y conviene decirlo para que nadie "arregle"
        // el orden más tarde creyendo que sostiene algo: las dos sentencias corren en el mismo
        // tick de JS y la recomposición de Compose en wasmJs espera al próximo frame, así que
        // entre una y otra no se pinta nada. Van en este orden por costumbre de dejar la
        // mutación de estado al final, no porque evite un parpadeo.
        reloadForLogout()
        loggedIn = false
    }
}
