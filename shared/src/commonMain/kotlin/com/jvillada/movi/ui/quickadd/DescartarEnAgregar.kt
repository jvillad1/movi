package com.jvillada.movi.ui.quickadd

import com.jvillada.movi.ui.components.formatAmountKeypadDisplay

/*
 * # Vuelta 71 · Agregar no pierde lo escrito
 *
 * Escape, la X, el velo y arrastrar la hoja descartaban $1.250.000 sin preguntar. Ahora, con un monto
 * o una nota escritos (distintos de lo que la hoja trajo al abrirse), cerrar pregunta antes con la
 * confirmación del sistema (`ConfirmarEnHoja`): «¿Descartar $1.250.000?». Sin nada escrito, cierra
 * directo, como siempre. El «atrás» de Android pregunta igual.
 */

/** El botón que confirma: se pierde lo escrito. */
const val DESCARTAR: String = "Descartar"

/** Lo que dice debajo de la pregunta. */
const val DETALLE_DE_DESCARTAR: String = "Lo que escribiste no se guarda."

/**
 * **La pregunta antes de cerrar**: «¿Descartar $1.250.000?» con el monto como lo dice la cifra de la
 * hoja, o «¿Descartar la nota?» si solo hay nota. [monto] son los dígitos escritos.
 */
internal fun preguntaDeDescartar(monto: String, moneda: String): String {
    val escrito = monto.toLongOrNull()?.takeIf { it > 0L }
        ?: return "¿Descartar la nota?"
    return "¿Descartar ${simboloDeMoneda(moneda)}${formatAmountKeypadDisplay(escrito.toString())}?"
}

/**
 * ¿Hay algo que se perdería al cerrar? Un monto o una nota **distintos de lo que la hoja trajo al
 * abrirse** (un «Anotar pago» llega con el monto puesto: cerrarlo sin tocar nada no pregunta).
 */
internal fun hayAlgoQuePerder(monto: String, montoAlAbrir: String, nota: String, notaAlAbrir: String): Boolean {
    val escrito = monto.toLongOrNull()?.takeIf { it > 0L }
    val montoNuevo = escrito != null && monto != montoAlAbrir
    return montoNuevo || (nota.isNotBlank() && nota.trim() != notaAlAbrir.trim())
}
