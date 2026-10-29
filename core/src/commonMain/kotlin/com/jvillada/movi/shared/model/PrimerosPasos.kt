package com.jvillada.movi.shared.model

/*
 * # Los primeros pasos: una sola lista (vuelta 54)
 *
 * Había dos listas que no cuadraban. La guía «Primer uso» tenía cuatro pasos (período, cuentas,
 * captura y pagos de cada mes) y la tarjeta «Primeros pasos» del Inicio decía «2 de 4» sobre cinco
 * renglones distintos (cuenta, recurrentes, préstamos y tarjetas, movimiento, extracto). Ahora hay
 * una: [primerosPasos]. La leen la guía (qué pasos pinta y en qué orden), la fila del Inicio («Te
 * faltan 3 pasos ›»), la pantalla «Primeros pasos» de Tú y el resumen del final de la guía, y el
 * contador sale de la misma lista ([pasosQueFaltan], [contadorDeLosPrimerosPasos]).
 *
 * Qué quedó afuera, y por qué:
 * - **«Si tienes préstamos o tarjetas, cárgalos»**: alguien sin deudas nunca lo podía tildar, y la
 *   lista quedaba incompleta para siempre. Las tarjetas se agregan en el paso de las cuentas (la guía
 *   las ofrece ahí) y los préstamos, en Créditos.
 * - **«Registra un movimiento»**: no es algo que se prepare una vez, es el uso de todos los días. En
 *   la web y en iOS lo cubre el paso de la captura («Sube un extracto», que se da por hecho con el
 *   primer movimiento, venga de donde venga); en Android, la captura del teléfono.
 * - **«Sube un extracto» como hábito sin tilde**: en la web y en iOS es el paso de la captura; en
 *   Android sigue a un toque en «Subir comprobante» de la cuadrícula.
 *
 * Todo es puro: quien llama sabe la plataforma y lee los datos que ya tiene.
 */

/** Un paso de la lista, en su orden. */
enum class PrimerPaso { PERIODO, CUENTAS, CAPTURA, FIJOS }

/**
 * **Cómo se entera Movi de los movimientos, según dónde corre** (vuelta 54). Android lee los SMS y
 * las notificaciones del banco; la web y iOS no pueden, y ahí lo que sí existe es subir un extracto
 * para que Movi lo lea («Compartir con Movi»). Si tampoco se pueden elegir archivos, el paso no va.
 */
enum class CapturaDelPrimerUso { PERMISOS_DEL_TELEFONO, SUBIR_UN_EXTRACTO, NINGUNA }

/** La captura de una plataforma. Pura, para probar la decisión sin depender de dónde corre la prueba. */
fun capturaDelPrimerUsoEn(esAndroid: Boolean, puedeElegirArchivos: Boolean = true): CapturaDelPrimerUso = when {
    esAndroid -> CapturaDelPrimerUso.PERMISOS_DEL_TELEFONO
    puedeElegirArchivos -> CapturaDelPrimerUso.SUBIR_UN_EXTRACTO
    else -> CapturaDelPrimerUso.NINGUNA
}

/**
 * **Lo que ya hay**, leído de los datos de siempre: el corte del perfil, las cuentas, la captura y las
 * reglas recurrentes. [capturaHecha] lo decide quien llama según la plataforma: en Android, el permiso
 * del teléfono o algún aviso que ya llegó; en la web y en iOS, algún movimiento (o el extracto que se
 * acaba de subir en la guía).
 */
data class LoQueYaHay(
    val periodoElegido: Boolean,
    val cuentasDeDinero: Int,
    val capturaHecha: Boolean,
    val fijos: Int,
)

/** Un paso con su estado. */
data class EstadoDelPrimerPaso(val paso: PrimerPaso, val hecho: Boolean)

/**
 * **La lista única**: los pasos que van en esta plataforma, en orden, cada uno hecho o pendiente. Sin
 * captura posible ([CapturaDelPrimerUso.NINGUNA]) ese paso no va: nada se lista como pendiente si en
 * esa plataforma no se puede hacer.
 */
fun primerosPasos(loQueHay: LoQueYaHay, captura: CapturaDelPrimerUso): List<EstadoDelPrimerPaso> = buildList {
    add(EstadoDelPrimerPaso(PrimerPaso.PERIODO, loQueHay.periodoElegido))
    add(EstadoDelPrimerPaso(PrimerPaso.CUENTAS, loQueHay.cuentasDeDinero > 0))
    if (captura != CapturaDelPrimerUso.NINGUNA) add(EstadoDelPrimerPaso(PrimerPaso.CAPTURA, loQueHay.capturaHecha))
    add(EstadoDelPrimerPaso(PrimerPaso.FIJOS, loQueHay.fijos > 0))
}

/** Cuántos faltan. */
fun pasosQueFaltan(lista: List<EstadoDelPrimerPaso>): Int = lista.count { !it.hecho }

/** El primero que falta, en el orden de la lista; `null` si está todo. Ahí abre la guía la fila del Inicio. */
fun primerPasoPendiente(lista: List<EstadoDelPrimerPaso>): PrimerPaso? = lista.firstOrNull { !it.hecho }?.paso

/** «2 de 4»: los hechos de los que hay. */
fun contadorDeLosPrimerosPasos(lista: List<EstadoDelPrimerPaso>): String = "${lista.count { it.hecho }} de ${lista.size}"

/** «Te falta 1 paso» / «Te faltan 3 pasos»; `null` sin nada que hacer. */
fun textoDeLosPasosQueFaltan(faltan: Int): String? = when {
    faltan <= 0 -> null
    faltan == 1 -> "Te falta 1 paso"
    else -> "Te faltan $faltan pasos"
}

/** El nombre de un paso, el mismo en la guía, en la lista de Tú y en el resumen. */
fun tituloDelPrimerPaso(paso: PrimerPaso, captura: CapturaDelPrimerUso): String = when (paso) {
    PrimerPaso.PERIODO -> "Tu período"
    PrimerPaso.CUENTAS -> "Tus cuentas"
    PrimerPaso.CAPTURA -> when (captura) {
        CapturaDelPrimerUso.PERMISOS_DEL_TELEFONO -> "Que Movi se entere solo"
        else -> "Sube un extracto"
    }
    PrimerPaso.FIJOS -> "Lo que pagas cada mes"
}

/**
 * ¿El período ya se eligió? Un corte que no es el mes de calendario (otro día, la regla del día hábil o
 * un inicio a mano). El mes de calendario es lo que tiene quien nunca lo tocó.
 */
fun periodoElegidoEn(ajustes: PeriodSettings): Boolean = !ajustes.esMesDeCalendario
