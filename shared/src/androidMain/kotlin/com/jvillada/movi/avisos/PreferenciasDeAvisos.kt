package com.jvillada.movi.avisos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jvillada.movi.shared.model.AvisoPorRevisar
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * **Lo que este teléfono recuerda de sus avisos** (Ola 1 · Movi avisa): los dos interruptores de
 * Ajustes, si la tarjeta de Hoy ya se contestó, y lo que dice la notificación agrupada de «Por
 * revisar» mientras siga a la vista.
 *
 * En el aparato y no en el server por lo mismo que el tema: es del teléfono, no del dueño. La web
 * tiene sus propios avisos (push por VAPID) con su propia suscripción.
 *
 * `SharedPreferences` a secas y no `Settings()` de multiplatform: lo leen dos Workers que pueden
 * correr con el proceso recién levantado, sin `MainActivity` y sin la UI de por medio.
 */
object PreferenciasDeAvisos {
    private const val PREFS = "movi_avisos"
    private const val KEY_MOVIMIENTOS = "avisar_movimientos"
    private const val KEY_VENCIMIENTOS = "avisar_vencimientos"
    private const val KEY_OFERTA_CERRADA = "oferta_de_hoy_cerrada"
    private const val KEY_AGRUPADOS = "movimientos_agrupados"
    private const val KEY_HUELLA_VENCIMIENTOS = "huella_vencimientos"
    private const val KEY_BANCO_MUDO = "avisar_banco_mudo"
    private const val KEY_HUELLA_BANCO_MUDO = "huella_banco_mudo"

    private val json = Json { ignoreUnknownKeys = true }
    private val listaDeAvisos = ListSerializer(AvisoPorRevisar.serializer())

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** «Avisarme cuando llegue un movimiento». Encendido por defecto (decisión del dueño). */
    fun avisarMovimientos(context: Context): Boolean = prefs(context).getBoolean(KEY_MOVIMIENTOS, true)
    fun ponerAvisarMovimientos(context: Context, valor: Boolean) {
        prefs(context).edit().putBoolean(KEY_MOVIMIENTOS, valor).apply()
    }

    /** «Avisarme antes de que venza un pago». Encendido por defecto. */
    fun avisarVencimientos(context: Context): Boolean = prefs(context).getBoolean(KEY_VENCIMIENTOS, true)
    fun ponerAvisarVencimientos(context: Context, valor: Boolean) {
        prefs(context).edit().putBoolean(KEY_VENCIMIENTOS, valor).apply()
    }

    /** Ola 2: «Avisarme si mi banco deja de avisar». Encendido por defecto, como los otros dos. */
    fun avisarBancoMudo(context: Context): Boolean = prefs(context).getBoolean(KEY_BANCO_MUDO, true)
    fun ponerAvisarBancoMudo(context: Context, valor: Boolean) {
        prefs(context).edit().putBoolean(KEY_BANCO_MUDO, valor).apply()
    }

    /** La huella del último silencio avisado (ver `huellaDeBancosMudos`). */
    fun huellaDeBancoMudo(context: Context): String? = prefs(context).getString(KEY_HUELLA_BANCO_MUDO, null)
    fun guardarHuellaDeBancoMudo(context: Context, huella: String) {
        prefs(context).edit().putString(KEY_HUELLA_BANCO_MUDO, huella).apply()
    }

    /** La tarjeta de Hoy ya se contestó («Activar avisos» o «Ahora no»): no vuelve a salir. */
    fun ofertaCerrada(context: Context): Boolean = prefs(context).getBoolean(KEY_OFERTA_CERRADA, false)
    fun cerrarOferta(context: Context) {
        prefs(context).edit().putBoolean(KEY_OFERTA_CERRADA, true).apply()
    }

    /** Lo que decía la notificación agrupada de «Por revisar». Vacío si no hay o no se lee. */
    fun agrupados(context: Context): List<AvisoPorRevisar> =
        prefs(context).getString(KEY_AGRUPADOS, null)
            ?.let { runCatching { json.decodeFromString(listaDeAvisos, it) }.getOrNull() }
            .orEmpty()

    fun guardarAgrupados(context: Context, avisos: List<AvisoPorRevisar>) {
        prefs(context).edit().putString(KEY_AGRUPADOS, json.encodeToString(listaDeAvisos, avisos)).apply()
    }

    /** La huella del último aviso de vencimientos (ver `huellaDeVencimientos`). */
    fun huellaDeVencimientos(context: Context): String? = prefs(context).getString(KEY_HUELLA_VENCIMIENTOS, null)
    fun guardarHuellaDeVencimientos(context: Context, huella: String) {
        prefs(context).edit().putString(KEY_HUELLA_VENCIMIENTOS, huella).apply()
    }
}

/** Desde Android 13 avisar pide un permiso en tiempo de ejecución; antes venía concedido. */
fun avisosPidenPermiso(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** ¿Está concedido el permiso de notificaciones? Antes de Android 13 no existe, y vale `true`. */
fun tienePermisoDeAvisos(context: Context): Boolean =
    !avisosPidenPermiso() ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/**
 * ¿Puede Movi mostrar una notificación AHORA? Con el permiso (13+) y con las notificaciones de la
 * app encendidas en los ajustes del sistema, que el dueño puede apagar en cualquier versión.
 */
fun puedeAvisar(context: Context): Boolean =
    tienePermisoDeAvisos(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()
