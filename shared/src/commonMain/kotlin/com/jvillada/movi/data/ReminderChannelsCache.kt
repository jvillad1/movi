package com.jvillada.movi.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jvillada.movi.shared.model.ReminderChannels

/**
 * **Lo que el server dice sobre por dónde le pueden llegar los recordatorios al dueño.**
 *
 * Se lee una vez por sesión de app y lo comparten los tres lugares que hoy hablan de
 * recordatorios: la casilla «Recordarme unos días antes» (recurrentes, créditos y tarjetas) y el
 * aviso ámbar de la pantalla de Recurrentes. Un solo caché y no un fetch por pantalla: la
 * respuesta no cambia mientras la app está abierta (depende de variables de entorno del server),
 * y el Inicio ya se quejó una vez de disparar diez llamadas.
 *
 * **`null` significa «todavía no se sabe», y eso no es lo mismo que «no hay canal».** Es la
 * distinción entera de este cambio: mientras no se sepa, nadie puede afirmar que un aviso no va a
 * llegar. Por eso una lectura fallida deja el valor en `null` (y no en un `ReminderChannels()`
 * vacío, que se leería como «no hay nada» y volvería a producir la mentira alarmista).
 *
 * Solo vive en memoria: es una afirmación del server sobre el estado de HOY, no un dato del
 * dueño. Persistirla haría que una configuración vieja siguiera hablando por la actual.
 */
object ReminderChannelsCache {

    /** `null` = todavía no se sabe (no llegó, o falló). Nunca se afirma nada con esto en `null`. */
    var canales: ReminderChannels? by mutableStateOf(null)
        private set

    /** Para no disparar dos lecturas simultáneas cuando se abren dos consumidores a la vez. */
    private var enVuelo = false

    /**
     * Idempotente: si ya se sabe, no vuelve a preguntar. Si falló, el próximo llamador reintenta
     * — un server que estaba caído hace un minuto puede contestar ahora, y quedarse en «no sé»
     * para siempre sería apagar el aviso incluso cuando sí corresponde.
     */
    suspend fun cargar() {
        if (canales != null || enVuelo) return
        enVuelo = true
        try {
            runCatching { Repositories.wallets.getReminderChannels() }
                .onSuccess { canales = it }
        } finally {
            enVuelo = false
        }
    }

    /** Al cerrar sesión: `emailTo` es la dirección del usuario que se va (ver `SessionManager.clear`). */
    fun clear() {
        canales = null
    }
}
