package com.jvillada.movi.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Vuelta 109 · **¿Esta instalación de Movi puede leer SMS?** Lo dice su propio manifiesto, no un flag
 * de build.
 *
 * Hay dos canales de la misma app (`androidApp`, sabores `directa` y `play`). El de Play sale sin
 * `READ_SMS` ni `RECEIVE_SMS` (Play los restringe a quien tenga la excepción aprobada) y el de Drive
 * los sigue pidiendo. Preguntarle al `PackageManager` qué permisos **pide** el paquete —no cuáles
 * están concedidos: eso es otra pregunta, la de `hasSmsPermissions`— hace que la decisión siga al
 * manifiesto que de verdad se instaló. Si mañana Play aprueba la excepción y el sabor `play` vuelve a
 * pedir los permisos, la app ofrece SMS sin tocar una línea de esto.
 *
 * Con `false`: «Captura en este teléfono» y la guía de primer uso no ofrecen el permiso de SMS ni el
 * historial de SMS (ofrecen las notificaciones del banco y el correo, como la web), el barrido del
 * inbox no se agenda (y si quedó agendado sale sin leer) y el receiver no existe en el manifiesto.
 *
 * Si el `PackageManager` no contesta se dice `false`: ofrecer un permiso que el manifiesto no pide
 * deja un botón que no hace nada, y eso es peor que no ofrecerlo. Para lo que NO se puede deshacer
 * (borrar el barrido periódico) está [loQuePideElManifiesto], que distingue «no sé» de «no».
 */
fun elAparatoPuedeLeerSms(context: Context): Boolean = loQuePideElManifiesto(context) ?: false

/**
 * Revisión de la vuelta 109 · **«no» y «no sé» no son lo mismo.** `true`/`false` = el manifiesto se leyó
 * y pide (o no) los dos permisos de SMS; `null` = el `PackageManager` falló y no se sabe. Con un `null`
 * la versión de Drive no puede tomar la decisión de la de Play (borrar su barrido de 6 h por un tropiezo).
 */
fun loQuePideElManifiesto(context: Context): Boolean? = runCatching {
    val pm = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
    }
    elManifiestoPideSms(info.requestedPermissions?.toList())
}.getOrNull()

/** Qué hace el barrido del inbox (`SmsBackfillWorker`) según lo que se sabe del manifiesto. */
enum class BarridoSegunElManifiesto {
    /** Pide SMS: el barrido de siempre. */
    BARRER,

    /** No se pudo leer el manifiesto: esta vez no lee, y el ciclo queda (la próxima corrida vuelve a mirar). */
    SALTAR_ESTA_VEZ,

    /** El manifiesto se leyó y no pide SMS (la versión de Play): no lee y se borra el ciclo. */
    SALTAR_Y_BORRAR_EL_CICLO,
}

/** La regla, pura: solo un «no» seguro borra el ciclo. */
fun barridoSegunElManifiesto(pideSms: Boolean?): BarridoSegunElManifiesto = when (pideSms) {
    true -> BarridoSegunElManifiesto.BARRER
    null -> BarridoSegunElManifiesto.SALTAR_ESTA_VEZ
    false -> BarridoSegunElManifiesto.SALTAR_Y_BORRAR_EL_CICLO
}

/**
 * La regla, pura: hacen falta los dos permisos. `RECEIVE_SMS` sin `READ_SMS` no sirve para el barrido
 * y `READ_SMS` sin `RECEIVE_SMS` no captura en vivo; ofrecer la mitad sería prometer una captura que
 * no está.
 */
fun elManifiestoPideSms(permisosPedidos: List<String>?): Boolean =
    permisosPedidos.orEmpty().let { Manifest.permission.READ_SMS in it && Manifest.permission.RECEIVE_SMS in it }
