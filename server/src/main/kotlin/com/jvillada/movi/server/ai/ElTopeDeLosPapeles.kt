package com.jvillada.movi.server.ai

import com.jvillada.movi.server.auth.RateLimiter
import com.jvillada.movi.server.parsing.ClaudeStatementParser
import com.jvillada.movi.server.parsing.LectorDePapeles
import com.jvillada.movi.server.parsing.LectorDePapelesConClaude

/**
 * # Cuántos papeles se leen con IA (revisión de lanzamiento, nov-2026)
 *
 * El chat tiene su tope del mes ([ElTopeDelMes]); leer un papel no tenía ninguno. Cada PDF o foto
 * distinta va a Haiku para clasificarla y, si es un extracto, a Sonnet para leerla, y el registro es
 * abierto: un script que crea cuentas y sube archivos distintos vaciaba el crédito de Anthropic, y
 * entonces la IA se caía para todos (`IA_SIN_CREDITO`).
 *
 * Solo cuenta lo que **de verdad** va a Claude: se mira justo antes de cada llamada, después de la
 * lectura guardada por huella y de los lectores sin IA (Bancolombia, Nu, Skandia, la AFC, Famirios).
 * Dos baldes de una hora: uno por usuario y otro de todos, que frena una inundación desde muchas
 * cuentas. Umbrales **elegidos, no medidos**: el dueño sube unos pocos papeles al mes.
 *
 * Vive en memoria ([RateLimiter]), como los demás topes: con una sola réplica alcanza (ver
 * `docs/lanzamiento/revision-de-lanzamiento.md`, §3). El tope diario de gasto sobre `ia_por_dia`
 * queda pendiente.
 */
internal object ElTopeDeLosPapeles {
    const val POR_USUARIO_POR_HORA: Int = 15
    const val DE_TODOS_POR_HORA: Int = 200
    private const val UNA_HORA_MS: Long = 60 * 60_000L

    /**
     * `true` si [uid] puede mandar un papel más a la IA; si sí, lo cuenta. Solo cuenta lo que [cuesta]:
     * con un lector de prueba o sin `ANTHROPIC_API_KEY` no sale nada a Anthropic (y la suite de pruebas
     * no llena el balde de todos).
     */
    fun pasa(uid: String, cuesta: Boolean = laLecturaCuesta()): Boolean =
        !cuesta || (RateLimiter.allow("papeles-ia:usuario:$uid", maxAttempts = POR_USUARIO_POR_HORA, windowMs = UNA_HORA_MS) &&
            RateLimiter.allow("papeles-ia:todos", maxAttempts = DE_TODOS_POR_HORA, windowMs = UNA_HORA_MS))

    private fun laLecturaCuesta(): Boolean =
        LectorDePapeles.actual === LectorDePapelesConClaude && ClaudeStatementParser.tieneLlave
}

/** Lo que dice la app pasado el tope. El archivo ya quedó guardado en Documentos. */
const val PAPELES_DEMASIADOS: String =
    "Movi leyó muchos papeles con IA en la última hora. Espera un rato y vuelve a intentarlo: " +
        "el archivo quedó guardado en Documentos."
