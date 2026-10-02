package com.jvillada.movi.avisos

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jvillada.movi.app.R
import com.jvillada.movi.shared.model.AvisoPorRevisar

/**
 * **Quien hace sonar el teléfono** (Ola 1 · Movi avisa). Lo que dicen los avisos y cuándo se decide
 * en `:shared` (`AvisosDelTelefono.kt`, puro y probado); acá solo hay canales, `NotificationCompat`
 * y el `Intent` que abre la pantalla al tocar.
 *
 * Dos canales separados, con nombre en español, para que el dueño pueda silenciar uno sin el otro
 * desde los ajustes del sistema: los movimientos pueden llegar diez veces al día, los vencimientos
 * una.
 */
object Avisador {
    private const val TAG = "movi"

    const val CANAL_MOVIMIENTOS = "movimientos"
    const val CANAL_VENCIMIENTOS = "vencimientos"
    const val CANAL_CAPTURA = "captura"

    /** Un id fijo por tipo: la notificación de «Por revisar» se reemplaza, no se apila. */
    private const val ID_MOVIMIENTOS = 7101
    private const val ID_VENCIMIENTOS = 7102
    private const val ID_BANCO_MUDO = 7103

    /** Idempotente: Android ignora crear un canal que ya existe (solo actualiza su nombre). */
    fun crearCanales(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val gestor = context.getSystemService(NotificationManager::class.java) ?: return
        gestor.createNotificationChannel(
            NotificationChannel(CANAL_MOVIMIENTOS, "Movimientos por revisar", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Cuando la captura del banco deja un movimiento en «Por revisar»."
            },
        )
        gestor.createNotificationChannel(
            NotificationChannel(CANAL_VENCIMIENTOS, "Pagos por vencer", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Los pagos del período que vencen hoy o mañana y no están pagados."
            },
        )
        gestor.createNotificationChannel(
            NotificationChannel(CANAL_CAPTURA, "La captura del banco", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Cuando un banco que avisaba siempre lleva días sin mandar nada."
            },
        )
    }

    /**
     * Avisa que llegaron [nuevos] a «Por revisar», agrupados con los que ya decía la notificación
     * si todavía está a la vista ([acumularMovimientos]). No hace nada con el interruptor apagado
     * o sin permiso.
     */
    fun avisarMovimientos(context: Context, nuevos: List<AvisoPorRevisar>) {
        if (nuevos.isEmpty()) return
        if (!PreferenciasDeAvisos.avisarMovimientos(context) || !puedeAvisar(context)) return
        val todos = acumularMovimientos(
            previos = PreferenciasDeAvisos.agrupados(context),
            nuevos = nuevos,
            sigueVisible = sigueALaVista(context, ID_MOVIMIENTOS),
        )
        PreferenciasDeAvisos.guardarAgrupados(context, todos)
        val texto = textoDeMovimientos(todos) ?: return
        publicar(context, ID_MOVIMIENTOS, CANAL_MOVIMIENTOS, texto, ABRIR_POR_REVISAR, cuantos = todos.size)
    }

    /** Avisa los vencimientos de hoy y mañana con [texto] (ver [textoDeVencimientos]). */
    fun avisarVencimientos(context: Context, texto: TextoDeAviso) {
        publicar(context, ID_VENCIMIENTOS, CANAL_VENCIMIENTOS, texto, ABRIR_PLAN, cuantos = texto.lineas.size)
    }

    /** Ola 2: avisa que un banco dejó de mandar avisos (ver `textoDeBancosMudos`). Tocarla abre la captura. */
    fun avisarBancoMudo(context: Context, texto: TextoDeAviso) {
        publicar(context, ID_BANCO_MUDO, CANAL_CAPTURA, texto, ABRIR_CAPTURA, cuantos = maxOf(1, texto.lineas.size))
    }

    /**
     * ¿La notificación [id] sigue en la barra? Si el dueño la tocó o la descartó, ya no: lo
     * próximo empieza un grupo nuevo. `activeNotifications` es de Android 6; antes, se asume que no.
     */
    private fun sigueALaVista(context: Context, id: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val gestor = context.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching { gestor.activeNotifications.any { it.id == id } }.getOrDefault(false)
    }

    // `puedeAvisar` ya miró el permiso justo antes; el lint no lo ve a través de la función.
    @SuppressLint("MissingPermission")
    private fun publicar(context: Context, id: Int, canal: String, texto: TextoDeAviso, abrir: String, cuantos: Int) {
        if (!puedeAvisar(context)) return
        crearCanales(context)
        val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return)
            .putExtra(EXTRA_ABRIR, abrir)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val alTocar = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val estilo = if (texto.lineas.isNotEmpty()) {
            NotificationCompat.InboxStyle().also { inbox -> texto.lineas.forEach { inbox.addLine(it) } }
        } else {
            NotificationCompat.BigTextStyle().bigText(texto.texto)
        }
        val aviso = NotificationCompat.Builder(context, canal)
            .setSmallIcon(R.drawable.ic_stat_movi)
            .setContentTitle(texto.titulo)
            .setContentText(texto.texto)
            .setStyle(estilo)
            .setNumber(cuantos)
            .setContentIntent(alTocar)
            .setAutoCancel(true)
            // Montos y nombres de destinatarios: en la pantalla bloqueada, solo que hay un aviso.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(if (canal == CANAL_VENCIMIENTOS) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_STATUS)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, aviso) }
            .onFailure { Log.w(TAG, "no se pudo publicar el aviso $id", it) }
    }
}
