package com.jvillada.movi.notificaciones

import com.jvillada.movi.ui.compartir.haceCuanto

/*
 * # Vuelta 92 · Que la captura de notificaciones no se caiga callada
 *
 * El `NotificationListenerService` de Android se desconecta —tras una actualización de la app,
 * porque el sistema mató el proceso, por el ahorro de batería— y Android no siempre lo vuelve a
 * enchufar. Mientras tanto las notificaciones del banco siguen llegando al teléfono y ninguna llega a
 * Movi, sin que nada lo diga: la pantalla decía «Concedido», que es cierto y no prueba nada.
 *
 * Acá vive la regla, pura y probada. Los que la usan son el servicio (`EscuchaDeNotificaciones`), la
 * vigía periódica (`VigiaDeLaEscuchaWorker`, `:androidApp`) y la sección «Captura en este teléfono».
 */

/**
 * **¿Está conectado el listener?** Las marcas las escribe el servicio en `onListenerConnected` y
 * `onListenerDisconnected` ([AlmacenDeNotificaciones]).
 *
 * El proceso entra en la cuenta porque un proceso que muere no alcanza a llamar
 * `onListenerDisconnected`: después de una actualización de la app la marca diría «conectada» para
 * siempre. Una conexión anotada por OTRO proceso no vale; si Android vuelve a enchufar el servicio,
 * `onListenerConnected` la anota de nuevo con el proceso de ahora.
 */
fun laEscuchaEstaConectada(conectadaEn: Long, desconectadaEn: Long, procesoDeLaConexion: Int, procesoActual: Int): Boolean =
    conectadaEn > 0L && conectadaEn >= desconectadaEn && procesoDeLaConexion == procesoActual

/** Lo que hace la vigía en cada vuelta. */
enum class LoQueHaceLaVigia {
    /** Concedido y conectado, o nunca se concedió: nada que hacer. */
    NADA,

    /** Concedido pero desconectado: pedirle a Android que vuelva a enchufar el servicio. */
    RECONECTAR,

    /** Se revocó el acceso que antes funcionaba, y todavía no se avisó: UNA notificación. */
    AVISAR_QUE_SE_REVOCO,
}

/**
 * La huella del aviso de «se revocó el acceso»: el episodio se identifica por la última conexión.
 * Si el dueño lo vuelve a conceder, el servicio se conecta, la marca cambia y una revocación
 * posterior es otro episodio, que sí suena.
 */
fun huellaDelAccesoRevocado(ultimaConexion: Long): String = "revocado@$ultimaConexion"

/**
 * **La regla de la vigía.**
 *
 * - Concedido y conectado → [LoQueHaceLaVigia.NADA].
 * - Concedido y desconectado → [LoQueHaceLaVigia.RECONECTAR] (`requestRebind`; no molesta al dueño:
 *   es lo que Android tendría que haber hecho solo).
 * - Sin acceso y nunca conectado → [LoQueHaceLaVigia.NADA]: no lo concedió nunca, y para eso está la
 *   tarjeta de la pantalla, no una notificación.
 * - Sin acceso pero alguna vez conectado → se revocó: [LoQueHaceLaVigia.AVISAR_QUE_SE_REVOCO] la
 *   primera vez, y [LoQueHaceLaVigia.NADA] si esta misma huella ya sonó.
 */
fun queHaceLaVigia(
    concedido: Boolean,
    conectada: Boolean,
    ultimaConexion: Long,
    huellaYaAvisada: String?,
): LoQueHaceLaVigia = when {
    concedido && conectada -> LoQueHaceLaVigia.NADA
    concedido -> LoQueHaceLaVigia.RECONECTAR
    ultimaConexion <= 0L -> LoQueHaceLaVigia.NADA
    huellaYaAvisada == huellaDelAccesoRevocado(ultimaConexion) -> LoQueHaceLaVigia.NADA
    else -> LoQueHaceLaVigia.AVISAR_QUE_SE_REVOCO
}

/** El título de la notificación de la vigía, y el botón que lleva al ajuste del sistema. */
const val TITULO_DEL_ACCESO_REVOCADO = "Movi dejó de leer las notificaciones del banco"
const val TEXTO_DEL_ACCESO_REVOCADO = "Vuelve a darle el acceso para que tus movimientos entren solos."
const val BOTON_ACTIVAR_EL_ACCESO = "Activar"

/**
 * Lo que dice la sección sobre el listener. [conectada] decide la píldora y si se ofrece
 * «Reconectar»; [texto] es la frase corta de al lado.
 */
data class EstadoDeLaEscucha(val conectada: Boolean, val texto: String)

/** El botón de la sección cuando el listener está desconectado: abre el ajuste del sistema. */
const val BOTON_RECONECTAR = "Reconectar"

/**
 * **La frase del estado, pura.** `null` sin acceso: ahí la tarjeta ya dice «Falta» y ofrece
 * concederlo.
 *
 * - «Conectada · última notificación del banco hace 2 h»
 * - «Conectada · todavía no llega ninguna notificación del banco»
 * - «Desconectada · Android dejó de pasarle a Movi las notificaciones del banco»
 *
 * «La última notificación del banco» es la última que llegó de una app de la lista, la haya subido
 * Movi o no (una persistente o un resumen de grupo también cuentan): dice que el listener oye, no
 * que hubo un movimiento.
 */
fun textoDelEstadoDeLaCaptura(concedido: Boolean, conectada: Boolean, ultimaDelBanco: Long, ahora: Long): EstadoDeLaEscucha? {
    if (!concedido) return null
    if (!conectada) {
        return EstadoDeLaEscucha(false, "Desconectada · Android dejó de pasarle a Movi las notificaciones del banco")
    }
    val ultima = if (ultimaDelBanco > 0L) {
        "última notificación del banco ${haceCuanto(ultimaDelBanco, ahora)}"
    } else {
        "todavía no llega ninguna notificación del banco"
    }
    return EstadoDeLaEscucha(true, "Conectada · $ultima")
}

/**
 * **¿Ofrecer la exención del ahorro de batería?** Solo si existe (Android 6+), si la app no está
 * exenta y si hay una captura que cuidar (el permiso de SMS o el acceso a notificaciones). Nunca se
 * pide sola: la sección la ofrece con un botón.
 */
fun debeOfrecerLaExencionDeBateria(sdkInt: Int, exenta: Boolean, hayCaptura: Boolean): Boolean =
    sdkInt >= 23 && !exenta && hayCaptura
