package com.jvillada.movi.server.sms

import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.shared.model.AnotacionPasada
import com.jvillada.movi.shared.model.MemoriaDeCategorias
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.selectAll

/**
 * **Cuántos movimientos hacia atrás se mira.** No es una vuelta de tuerca de rendimiento: es que
 * una categoría de hace tres años no dice cómo el dueño clasifica hoy, y la memoria tiene que poder
 * cambiar de opinión con él. Con sus datos (133 movimientos en producción al 16-sep-2026) el tope
 * no se alcanza ni de lejos; existe para el día que sí.
 */
private const val CUANTOS_MOVIMIENTOS_RECUERDA = 2_000

/**
 * **Lo que el dueño ya anotó, listo para que Movi lo recuerde.**
 *
 * Es una proyección de cuatro columnas y no [com.jvillada.movi.server.balance.loadNonVoidedEvents]
 * a propósito: la memoria no necesita saldos ni tipos de cuenta, y esto corre en cada apertura de
 * un mensaje del banco.
 *
 * **`merchant` antes que `description`, y los dos.** El primero es el texto como lo mandó el banco
 * —«Pago QR · llave 0092184713»—, que es lo que se va a volver a ver en el próximo SMS y por eso
 * manda para la huella. El segundo es como el dueño lo dejó escrito —«Panadería de la 33»—, que es
 * lo que él reconoce y lo que Movi le va a proponer. Si guardáramos uno solo, renombrar un
 * movimiento rompería el vínculo con la llave o lo dejaría ilegible para siempre.
 *
 * Los anulados quedan afuera, como en todo lo demás que suma plata en Movi.
 */
fun Transaction.anotacionesPasadasDe(uid: String): List<AnotacionPasada> {
    val anulados = VoidEvents.selectAll()
        .where { VoidEvents.userId eq uid }
        .map { it[VoidEvents.originalEventId] }
        .toSet()
    return Events
        .select(Events.id, Events.merchant, Events.description, Events.category, Events.timestamp)
        .where { Events.userId eq uid }
        .orderBy(Events.timestamp to SortOrder.DESC)
        .limit(CUANTOS_MOVIMIENTOS_RECUERDA)
        .filterNot { it[Events.id] in anulados }
        .map { fila ->
            val delBanco = fila[Events.merchant]?.takeIf { it.isNotBlank() }
            AnotacionPasada(
                comoLlego = delBanco ?: fila[Events.description],
                nombre = fila[Events.description],
                categoria = fila[Events.category],
                cuando = fila[Events.timestamp],
            )
        }
}

/** La memoria de [uid], armada con [anotacionesPasadasDe]. */
fun Transaction.memoriaDe(uid: String): MemoriaDeCategorias =
    MemoriaDeCategorias.de(anotacionesPasadasDe(uid))
