package com.jvillada.movi.notificaciones

import android.content.Context
import android.os.Process
import com.jvillada.movi.sms.SmsFilterConfigStore
import org.json.JSONArray
import org.json.JSONObject

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
 * de mirarle el contenido. Desde la vuelta 92 guarda además, de esas mismas, la llave de Android y
 * un resumen SHA-256 del texto ([VistaPorLlave]), y las horas del listener (conectado, desconectado,
 * la última notificación de una app de la lista).
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
     * Vuelta 92 · la memoria por llave ([VistaPorLlave]): reconoce la misma notificación repuesta
     * con otra hora. Va aparte de [KEY_VISTAS] para que un APK viejo siga leyendo esa como siempre.
     */
    private const val KEY_POR_LLAVE = "notif_por_llave"

    /** Vuelta 92 · las marcas del listener (ver `laEscuchaEstaConectada`). */
    private const val KEY_CONECTADA_AT = "notif_conectada_at"
    private const val KEY_DESCONECTADA_AT = "notif_desconectada_at"
    private const val KEY_PROCESO_DE_LA_CONEXION = "notif_proceso_de_la_conexion"

    /** Vuelta 92 · la última notificación que llegó de una app de la lista, subida o no. */
    private const val KEY_ULTIMA_DEL_BANCO_AT = "notif_ultima_del_banco_at"

    /** Vuelta 92 · la huella del último «se revocó el acceso» que sonó (ver `queHaceLaVigia`). */
    private const val KEY_REVOCADO_AVISADO = "notif_revocado_avisado"

    /** La memoria completa: las huellas de siempre y las vistas por llave. */
    fun memoria(context: Context): MemoriaDeLoSubido {
        val crudo = prefs(context).getString(KEY_POR_LLAVE, null)
        val porLlave = crudo?.let {
            runCatching {
                val a = JSONArray(it)
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    VistaPorLlave(o.getString("k"), o.getString("c"), o.getLong("t"))
                }
            }.getOrNull()
        }.orEmpty()
        return MemoriaDeLoSubido(vistas(context), porLlave)
    }

    fun guardarMemoria(context: Context, memoria: MemoriaDeLoSubido) {
        val porLlave = JSONArray().apply {
            memoria.porLlave.forEach { put(JSONObject().put("k", it.llave).put("c", it.contenido).put("t", it.cuando)) }
        }
        prefs(context).edit()
            .putString(KEY_VISTAS, JSONArray(memoria.huellas).toString())
            .putString(KEY_POR_LLAVE, porLlave.toString())
            .apply()
    }

    /** `onListenerConnected`: la hora y el proceso que la anotó. */
    fun marcarConectada(context: Context, ahora: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putLong(KEY_CONECTADA_AT, ahora)
            .putInt(KEY_PROCESO_DE_LA_CONEXION, Process.myPid())
            .apply()
    }

    fun marcarDesconectada(context: Context, ahora: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_DESCONECTADA_AT, ahora).apply()
    }

    fun conectadaAt(context: Context): Long = prefs(context).getLong(KEY_CONECTADA_AT, 0L)

    /** ¿Está conectado el listener, en ESTE proceso? La regla, en `laEscuchaEstaConectada`. */
    fun estaConectada(context: Context): Boolean {
        val p = prefs(context)
        return laEscuchaEstaConectada(
            conectadaEn = p.getLong(KEY_CONECTADA_AT, 0L),
            desconectadaEn = p.getLong(KEY_DESCONECTADA_AT, 0L),
            procesoDeLaConexion = p.getInt(KEY_PROCESO_DE_LA_CONEXION, -1),
            procesoActual = Process.myPid(),
        )
    }

    /** Nunca retrocede: el barrido de la bandeja trae notificaciones viejas. */
    fun marcarLlegoDelBanco(context: Context, cuando: Long = System.currentTimeMillis()) {
        if (cuando <= ultimaDelBancoAt(context)) return
        prefs(context).edit().putLong(KEY_ULTIMA_DEL_BANCO_AT, cuando).apply()
    }

    fun ultimaDelBancoAt(context: Context): Long = prefs(context).getLong(KEY_ULTIMA_DEL_BANCO_AT, 0L)

    fun revocadoAvisado(context: Context): String? = prefs(context).getString(KEY_REVOCADO_AVISADO, null)

    fun guardarRevocadoAvisado(context: Context, huella: String) {
        prefs(context).edit().putString(KEY_REVOCADO_AVISADO, huella).apply()
    }

    fun marcarUltima(context: Context) {
        prefs(context).edit().putLong(KEY_ULTIMA, System.currentTimeMillis()).apply()
    }

    fun ultimaAt(context: Context): Long = prefs(context).getLong(KEY_ULTIMA, 0L)
}
