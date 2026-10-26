package com.jvillada.movi.sms

import android.content.Context
import com.jvillada.movi.data.SessionManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * **Lo que el banco mandó mientras la sesión estaba cerrada por el server.**
 *
 * El 26-oct-2026 la rotación de `JWT_SECRET` dejó muerto el token del teléfono, y `SmsSyncWorker`
 * devolvía `failure()` con cada SMS y cada notificación del banco: el SMS todavía se recuperaba con
 * el barrido del inbox, pero **la notificación de una app del banco no se puede volver a leer** — se
 * perdía para siempre. Ahora, cuando el server rechaza la sesión, cada captura se guarda acá y sube
 * apenas el dueño vuelve a entrar ([SessionManager.alEntrar] → `SubirCapturasPorSubirWorker`).
 *
 * Cada una lleva el id de quien era la sesión ([SessionManager.sesionRechazadaDe]): si entra otra
 * persona en este teléfono, lo de la anterior **no** se le sube a ella — se descarta. Y solo se
 * guarda cuando fue el server el que cerró: si el dueño salió a mano, la captura se descarta como
 * siempre (no hay a quién esperar).
 *
 * El texto de un aviso del banco es privado, pero no más que el `inputData` del Worker, que ya vive
 * en la base de WorkManager dentro de la app. El tope ([MAXIMO]) es para que un teléfono que nadie
 * vuelve a abrir no acumule sin fin: se quedan las más recientes.
 */
object CapturasPorSubir {

    data class Captura(
        val id: String,
        val sender: String,
        val body: String,
        val ts: Long,
        val deNotificacion: Boolean,
        val userId: String,
    )

    internal const val MAXIMO = 500
    private const val PREFS = "movi_capturas_por_subir"
    private const val CLAVE = "capturas"

    // Lo tocan el Worker de cada SMS y el que sube lo pendiente, a la vez: leer-modificar-escribir
    // sin candado perdería capturas.
    private val candado = Any()

    /** Guarda [captura]; si ya estaba (mismo id), no la duplica. */
    fun guardar(context: Context, captura: Captura) = synchronized(candado) {
        escribir(context, agregar(leer(context), captura))
    }

    /** Las de [userId], en el orden en que llegaron. */
    fun deQuien(context: Context, userId: String): List<Captura> = synchronized(candado) {
        leer(context).filter { it.userId == userId }
    }

    /** Saca las ya subidas ([ids]) y todo lo que no es de [userId] (ver el encabezado). */
    fun quitarSubidas(context: Context, userId: String, ids: Set<String>) = synchronized(candado) {
        escribir(context, leer(context).filter { it.userId == userId && it.id !in ids })
    }

    fun cuantas(context: Context): Int = synchronized(candado) { leer(context).size }

    internal fun agregar(lista: List<Captura>, captura: Captura, maximo: Int = MAXIMO): List<Captura> =
        (lista.filterNot { it.id == captura.id } + captura).takeLast(maximo)

    internal fun codificar(lista: List<Captura>): String = JSONArray().apply {
        lista.forEach {
            put(
                JSONObject()
                    .put("id", it.id)
                    .put("sender", it.sender)
                    .put("body", it.body)
                    .put("ts", it.ts)
                    .put("notificacion", it.deNotificacion)
                    .put("userId", it.userId),
            )
        }
    }.toString()

    /** Una lista ilegible es una lista vacía: esto nunca puede tumbar al Worker que sube un SMS. */
    internal fun decodificar(json: String?): List<Captura> = runCatching {
        val array = JSONArray(json ?: return emptyList())
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Captura(
                id = o.getString("id"),
                sender = o.optString("sender"),
                body = o.getString("body"),
                ts = o.getLong("ts"),
                deNotificacion = o.optBoolean("notificacion"),
                userId = o.getString("userId"),
            )
        }
    }.getOrDefault(emptyList())

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun leer(context: Context): List<Captura> = decodificar(prefs(context).getString(CLAVE, null))

    private fun escribir(context: Context, lista: List<Captura>) {
        // commit y no apply: el proceso de un Worker puede morir apenas `doWork` retorna.
        prefs(context).edit().apply { if (lista.isEmpty()) remove(CLAVE) else putString(CLAVE, codificar(lista)) }.commit()
    }
}

/**
 * ¿De quién guardar una captura que no se pudo subir por falta de sesión? Del dueño de la sesión que
 * el server cerró; `null` (descartarla) si no hay sesión porque alguien salió a mano o nunca entró.
 */
fun duenoDeLaCapturaSinSesion(sesionRechazadaDe: String?): String? = sesionRechazadaDe?.takeIf { it.isNotBlank() }
