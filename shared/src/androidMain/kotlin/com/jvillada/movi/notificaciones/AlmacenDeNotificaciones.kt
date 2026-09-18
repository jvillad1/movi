package com.jvillada.movi.notificaciones

import android.content.Context
import com.jvillada.movi.sms.SmsFilterConfigStore
import org.json.JSONArray

/**
 * El poquito estado que la captura por notificaciones necesita en el teléfono, en **el mismo
 * archivo de prefs que el filtro de SMS** (`SmsFilterConfigStore.PREFS`).
 *
 * Mismo archivo a propósito, y no uno nuevo: ese archivo entero ya está excluido del Auto Backup
 * (ver `androidApp/res/xml/backup_rules.xml`), que es exactamente lo que hay que hacer con estos
 * datos. La memoria de lo ya subido es un hecho de ESTE teléfono —restaurada en otro diría «ya lo
 * mandé» de cosas que ese teléfono nunca mandó— y el permiso de notificaciones tampoco viaja.
 *
 * Lo único que guarda de una notificación es su huella ([huellaDeLaNotificacion]), que SÍ contiene
 * el texto — pero solo de las apps que pasaron el filtro, o sea de las que ya se subieron al server
 * del propio dueño. De una app fuera de la lista acá no queda ni rastro: el servicio descarta antes
 * de mirarle el contenido.
 */
object AlmacenDeNotificaciones {

    /** Huellas de lo ya subido, como JSON array ordenado: la más vieja primero. */
    private const val KEY_VISTAS = "notif_vistas"

    /**
     * Momento de la última notificación subida con éxito.
     *
     * Deliberadamente separada de `SmsFilterConfigStore.KEY_LAST_CAPTURE_AT`, por el mismo motivo
     * por el que el backfill tiene la suya: esa clave significa «el receiver de SMS en tiempo real
     * anduvo», y es el único indicador de que el sensor de SMS quedó mudo. Si una notificación la
     * pisara, la captura por notificaciones —que es justamente la que anda cuando el banco dejó de
     * mandar SMS— escondería para siempre el síntoma que esa clave existe para mostrar.
     */
    private const val KEY_ULTIMA = "notif_ultima_at"

    private fun prefs(context: Context) =
        context.getSharedPreferences(SmsFilterConfigStore.PREFS, Context.MODE_PRIVATE)

    fun vistas(context: Context): List<String> {
        val crudo = prefs(context).getString(KEY_VISTAS, null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(crudo)
            (0 until a.length()).map { a.getString(it) }
        }.getOrDefault(emptyList())
    }

    /**
     * **¿Hay que subir esta notificación?** `true` la primera vez y `false` en cada repetición
     * dentro del mismo minuto; la escritura de la memoria va junta con la respuesta a propósito,
     * para que no exista un camino que pregunte y se olvide de anotar.
     *
     * No es atómico entre procesos: dos notificaciones idénticas en el mismo milisegundo podrían
     * pasar las dos. No importa — abajo quedan el `enqueueUniqueWork` por id y el chequeo por id de
     * `/api/sms/sync`, que dan el mismo veredicto.
     */
    fun esNuevaYAnotala(context: Context, huella: String): Boolean {
        val nueva = recordarHuella(vistas(context), huella) ?: return false
        prefs(context).edit().putString(KEY_VISTAS, JSONArray(nueva).toString()).apply()
        return true
    }

    fun marcarUltima(context: Context) {
        prefs(context).edit().putLong(KEY_ULTIMA, System.currentTimeMillis()).apply()
    }

    fun ultimaAt(context: Context): Long = prefs(context).getLong(KEY_ULTIMA, 0L)
}
