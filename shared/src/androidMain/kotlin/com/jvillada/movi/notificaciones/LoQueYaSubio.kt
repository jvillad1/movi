package com.jvillada.movi.notificaciones

import java.security.MessageDigest
import kotlin.math.abs

/*
 * # Vuelta 92 · Lo que ya subió, aunque el banco actualice su notificación o el listener se caiga
 *
 * Dos caminos entran por acá y los dos pasan por la MISMA decisión ([decidirNotificacion]) y la
 * MISMA memoria ([MemoriaDeLoSubido]):
 *
 * - `onNotificationPosted`: una notificación nueva o una actualización de una que ya estaba.
 * - `onListenerConnected`: el barrido de lo que sigue en la bandeja del teléfono. Es lo que llegó
 *   mientras el listener estaba caído (una actualización de la app, el sistema lo mató, el ahorro de
 *   batería) y que Android nunca le entregó a Movi.
 *
 * El barrido vuelve a ver lo que ya subió —una notificación del banco se queda en la bandeja hasta
 * que el dueño la descarta—, y por eso la memoria tiene que reconocerlo sin el servidor.
 */

/**
 * **Cuánto dura «es la misma notificación, actualizada»** para una llave de Android que vuelve con el
 * mismo texto. Elegido, no medido: una app de banco repone su notificación al desbloquear el teléfono
 * o al abrirla, y eso pasa en minutos u horas, no días. Pasado esto, el mismo texto bajo la misma
 * llave vuelve a contar como un movimiento (un banco que usa un id fijo para todos sus avisos y un
 * cobro idéntico al de ayer).
 */
internal const val VENTANA_DE_LA_MISMA_LLAVE_MS = 6L * 60 * 60 * 1000

/**
 * Lo que se recuerda de una notificación subida, por su llave de Android (`StatusBarNotification.key`).
 * [contenido] es un resumen del paquete, el título y el cuerpo ([contenidoDeLaNotificacion]), no el
 * texto: para comparar alcanza.
 */
data class VistaPorLlave(val llave: String, val contenido: String, val cuando: Long)

/**
 * **La memoria de lo ya subido.** [huellas] es la de siempre (paquete, título, cuerpo y minuto: ver
 * [huellaDeLaNotificacion]); [porLlave] es la de la vuelta 92, para las actualizaciones.
 */
data class MemoriaDeLoSubido(
    val huellas: List<String> = emptyList(),
    val porLlave: List<VistaPorLlave> = emptyList(),
)

/** El resumen del contenido para [VistaPorLlave]: igual texto, igual resumen; el minuto no entra. */
fun contenidoDeLaNotificacion(paquete: String, titulo: String, texto: String): String {
    val crudo = "$paquete|${titulo.trim()}|${texto.trim()}"
    val digest = MessageDigest.getInstance("SHA-256").digest(crudo.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }.take(24)
}

/**
 * **¿Es nueva? Se decide por el texto, no por la llave.**
 *
 * - La misma huella (mismo texto en el mismo minuto) es la misma notificación: no sube.
 * - La misma llave con el **mismo texto** dentro de [VENTANA_DE_LA_MISMA_LLAVE_MS] es una
 *   actualización o una reposición (el banco la vuelve a publicar al desbloquear, con otra hora): no
 *   sube. Sin esto, cada reposición en otro minuto era una fila más en «Por revisar».
 * - La misma llave con **otro texto** sí sube: hay bancos que usan un id fijo para todos sus avisos,
 *   y ahí cada movimiento llega como una «actualización» de la anterior. Deduplicar por llave se los
 *   comería.
 * - Otra llave con el mismo texto en otro minuto también sube: dos cobros iguales existen (ver
 *   [huellaDeLaNotificacion]).
 *
 * Devuelve `null` si es una repetición, o la memoria con esta anotada (recortada al [tope] por el
 * extremo más viejo) si hay que subirla. Una llave vacía (no se pudo leer) solo usa la huella.
 */
fun anotarSiEsNueva(
    memoria: MemoriaDeLoSubido,
    huella: String,
    vista: VistaPorLlave,
    tope: Int = TOPE_DE_MEMORIA,
): MemoriaDeLoSubido? {
    if (huella in memoria.huellas) return null
    val actualizada = vista.llave.isNotBlank() && memoria.porLlave.any {
        it.llave == vista.llave && it.contenido == vista.contenido &&
            abs(vista.cuando - it.cuando) <= VENTANA_DE_LA_MISMA_LLAVE_MS
    }
    if (actualizada) return null
    val huellas = recordarHuella(memoria.huellas, huella, tope) ?: return null
    val porLlave = if (vista.llave.isBlank()) memoria.porLlave else (memoria.porLlave + vista).takeLast(tope)
    return MemoriaDeLoSubido(huellas, porLlave)
}

/**
 * Una notificación que hay que subir: lo que el `SmsSyncWorker` recibe. [paquete] es para el rótulo
 * («Notificación · Bancolombia»), que el servicio resuelve con el `PackageManager`.
 */
data class SubidaDeNotificacion(val id: String, val paquete: String, val texto: String, val cuando: Long)

/** Lo que sale de [procesarNotificaciones]: qué subir y la memoria con eso anotado. */
data class ResultadoDelProceso(val subidas: List<SubidaDeNotificacion>, val memoria: MemoriaDeLoSubido)

/**
 * **El camino único de una o varias notificaciones** —la que acaba de llegar o el barrido de la
 * bandeja—: la decisión de siempre ([decidirNotificacion]: paquete, persistente, resumen de grupo,
 * sin texto) y la memoria ([anotarSiEsNueva]). Van de la más vieja a la más nueva, así una
 * actualización del barrido se compara con la que la precedió.
 *
 * Pura: el servicio la llama, guarda la memoria y encola un Worker por subida.
 */
fun procesarNotificaciones(
    entrantes: List<NotificacionEntrante>,
    config: AppsQueAvisan,
    memoria: MemoriaDeLoSubido,
): ResultadoDelProceso {
    var actual = memoria
    val subidas = mutableListOf<SubidaDeNotificacion>()
    entrantes.sortedBy { it.cuando }.forEach { n ->
        val decision = decidirNotificacion(n, config)
        if (decision !is DecisionDeNotificacion.Subir) return@forEach
        val huella = huellaDeLaNotificacion(n.paquete, n.titulo, n.texto, n.cuando)
        val vista = VistaPorLlave(n.llave, contenidoDeLaNotificacion(n.paquete, n.titulo, n.texto), n.cuando)
        actual = anotarSiEsNueva(actual, huella, vista) ?: return@forEach
        subidas += SubidaDeNotificacion(idDeNotificacion(huella), n.paquete, decision.texto, n.cuando)
    }
    return ResultadoDelProceso(subidas, actual)
}
