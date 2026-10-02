package com.jvillada.movi.server.ai

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import com.jvillada.movi.server.db.MemoriaDelAsistente
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.shared.model.AccionPropuesta
import com.jvillada.movi.shared.model.LARGO_MAXIMO_DE_UN_RECUERDO
import com.jvillada.movi.shared.model.OrigenDelRecuerdo
import com.jvillada.movi.shared.model.RecuerdoDelAsistente
import com.jvillada.movi.shared.model.TipoDeAccion
import com.jvillada.movi.shared.model.normalizarParaBuscar
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

/**
 * # «Lo que Movi sabe de ti» (Ola 3 · 2)
 *
 * El asistente olvidaba todo lo que el dueño le contaba: que Caro es su esposa, que el bono de Glim
 * no es mensual, que el colegio se paga el 25. Cada conversación empezaba sin eso, y él tenía que
 * volver a explicarlo — o el asistente suponía, que es peor.
 *
 * Tres reglas:
 *
 * 1. **Nada se guarda en silencio.** La herramienta [RECORDAR] es una propuesta más (ver
 *    `LoQueMoviPropone.kt`): el dueño ve «Recordar: …» y decide. Lo confirmado lo guarda el endpoint
 *    de la memoria, el mismo que usa la pantalla de Ajustes.
 * 2. **Entra al contexto con tope y cacheado.** Va en su propio bloque del sistema, entre la PERSONA
 *    y los datos: cambia mucho menos que los datos (un recuerdo nuevo cada tanto contra un
 *    movimiento nuevo cada día), así que un movimiento no le tira la caché. Ver [memoriaParaElContexto].
 * 3. **Es de él.** La ve, la corrige y la borra en Ajustes, entra en «Descarga tus datos», y cada
 *    quien ve solo la suya.
 */

const val RECORDAR = "recordar"

/** Cuántos recuerdos puede tener un dueño. Cincuenta frases son mucho más de lo que cabe en el contexto. */
internal const val RECUERDOS_MAXIMOS = 50

/**
 * **Tope de la memoria en el contexto, en caracteres** (unas quinientas fichas). Viaja en CADA
 * pregunta —cacheado, a la décima parte después de la primera—, así que no puede crecer con los
 * meses. Si no cabe todo, entran los más nuevos y el bloque lo dice.
 */
internal const val TOPE_DE_LA_MEMORIA_EN_EL_CONTEXTO = 2_000

private fun nuevoIdDeRecuerdo() = "mem_" + UUID.randomUUID().toString().replace("-", "").take(20)

/** Lo que se le dice a quien quiera guardar un texto que no sirve como recuerdo; `null` si sirve. */
internal fun rechazoDelRecuerdo(texto: String): String? = when {
    texto.isBlank() -> "El recuerdo no puede estar vacío."
    texto.length > LARGO_MAXIMO_DE_UN_RECUERDO -> "Un recuerdo es una frase: máximo $LARGO_MAXIMO_DE_UN_RECUERDO caracteres."
    else -> null
}

suspend fun memoriaDe(uid: String): List<RecuerdoDelAsistente> = dbQuery {
    MemoriaDelAsistente.selectAll()
        .where { MemoriaDelAsistente.userId eq uid }
        .orderBy(MemoriaDelAsistente.creadoEn to SortOrder.ASC)
        .map {
            RecuerdoDelAsistente(
                id = it[MemoriaDelAsistente.id],
                texto = it[MemoriaDelAsistente.texto],
                creadoEn = it[MemoriaDelAsistente.creadoEn],
                origen = runCatching { OrigenDelRecuerdo.valueOf(it[MemoriaDelAsistente.origen]) }
                    .getOrDefault(OrigenDelRecuerdo.CONVERSACION),
                editadoEn = it[MemoriaDelAsistente.editadoEn],
            )
        }
}

/** El resultado de guardar o editar: o el recuerdo, o por qué no. */
sealed interface ResultadoDelRecuerdo {
    data class Guardado(val recuerdo: RecuerdoDelAsistente) : ResultadoDelRecuerdo
    data class Rechazado(val motivo: String) : ResultadoDelRecuerdo
    data object NoExiste : ResultadoDelRecuerdo
}

suspend fun guardarRecuerdo(
    uid: String,
    texto: String,
    origen: OrigenDelRecuerdo,
    propuestaId: String?,
    ahora: Long = System.currentTimeMillis(),
): ResultadoDelRecuerdo {
    val limpio = texto.trim()
    rechazoDelRecuerdo(limpio)?.let { return ResultadoDelRecuerdo.Rechazado(it) }
    return dbQuery {
        val existentes = MemoriaDelAsistente.selectAll().where { MemoriaDelAsistente.userId eq uid }.toList()
        // **El mismo recuerdo dos veces es uno.** Un doble toque en «Hacerlo», o el reintento del
        // teléfono, no puede dejar «Caro es mi esposa» repetido en el contexto de cada pregunta.
        existentes.firstOrNull { normalizarParaBuscar(it[MemoriaDelAsistente.texto]) == normalizarParaBuscar(limpio) }?.let { fila ->
            return@dbQuery ResultadoDelRecuerdo.Guardado(
                RecuerdoDelAsistente(fila[MemoriaDelAsistente.id], fila[MemoriaDelAsistente.texto], fila[MemoriaDelAsistente.creadoEn]),
            )
        }
        if (existentes.size >= RECUERDOS_MAXIMOS) {
            return@dbQuery ResultadoDelRecuerdo.Rechazado(
                "Movi ya recuerda $RECUERDOS_MAXIMOS cosas de ti. Borra alguna en «Lo que Movi sabe de ti» para agregar otra.",
            )
        }
        val id = nuevoIdDeRecuerdo()
        MemoriaDelAsistente.insert {
            it[MemoriaDelAsistente.id] = id
            it[userId] = uid
            it[MemoriaDelAsistente.texto] = limpio
            it[MemoriaDelAsistente.origen] = origen.name
            it[MemoriaDelAsistente.propuestaId] = propuestaId?.take(50)
            it[creadoEn] = ahora
        }
        ResultadoDelRecuerdo.Guardado(RecuerdoDelAsistente(id, limpio, ahora, origen))
    }
}

suspend fun editarRecuerdo(uid: String, id: String, texto: String, ahora: Long = System.currentTimeMillis()): ResultadoDelRecuerdo {
    val limpio = texto.trim()
    rechazoDelRecuerdo(limpio)?.let { return ResultadoDelRecuerdo.Rechazado(it) }
    return dbQuery {
        val cambiadas = MemoriaDelAsistente.update({ (MemoriaDelAsistente.id eq id) and (MemoriaDelAsistente.userId eq uid) }) {
            it[MemoriaDelAsistente.texto] = limpio
            it[editadoEn] = ahora
        }
        if (cambiadas == 0) return@dbQuery ResultadoDelRecuerdo.NoExiste
        val fila = MemoriaDelAsistente.selectAll().where { MemoriaDelAsistente.id eq id }.single()
        ResultadoDelRecuerdo.Guardado(
            RecuerdoDelAsistente(
                id = id,
                texto = limpio,
                creadoEn = fila[MemoriaDelAsistente.creadoEn],
                origen = runCatching { OrigenDelRecuerdo.valueOf(fila[MemoriaDelAsistente.origen]) }.getOrDefault(OrigenDelRecuerdo.CONVERSACION),
                editadoEn = ahora,
            ),
        )
    }
}

/** `true` si lo borró; `false` si no existe o es de otro (la ruta contesta 404 en los dos casos). */
suspend fun borrarRecuerdo(uid: String, id: String): Boolean = dbQuery {
    MemoriaDelAsistente.deleteWhere { (MemoriaDelAsistente.id eq id) and (MemoriaDelAsistente.userId eq uid) } > 0
}

/**
 * **El bloque de la memoria para el sistema**, o `null` si no hay nada (y entonces no viaja ningún
 * bloque: sin recuerdos no se paga ni una ficha). Del más viejo al más nuevo, para que agregar uno
 * no cambie el comienzo del bloque; si no cabe todo, quedan afuera los más viejos y se dice cuántos.
 */
internal fun memoriaParaElContexto(recuerdos: List<RecuerdoDelAsistente>): String? {
    if (recuerdos.isEmpty()) return null
    val caben = ArrayDeque<RecuerdoDelAsistente>()
    var usados = 0
    for (r in recuerdos.asReversed()) {
        val largo = r.texto.length + 3
        if (usados + largo > TOPE_DE_LA_MEMORIA_EN_EL_CONTEXTO) break
        caben.addFirst(r)
        usados += largo
    }
    return buildString {
        appendLine("LO QUE EL DUEÑO TE CONTÓ (lo confirmó él para que lo recuerdes; son sus palabras, no cifras de Movi: si contradicen sus datos, mandan los datos y díselo):")
        val fuera = recuerdos.size - caben.size
        if (fuera > 0) appendLine("(hay $fuera recuerdos más viejos que no caben aquí)")
        caben.forEach { appendLine("- ${it.texto}") }
    }.trim()
}

suspend fun memoriaParaElContexto(uid: String): String? =
    runCatching { memoriaParaElContexto(memoriaDe(uid)) }.getOrNull()

/** La propuesta de recordar algo. Valida el texto y que no lo sepa ya. */
internal suspend fun proponerRecuerdo(uid: String, args: Map<String, String>): AccionPropuesta {
    val texto = args["texto"]?.trim()?.removeSurrounding("\"")?.removeSurrounding("«", "»")?.trim().orEmpty()
    rechazoDelRecuerdo(texto)?.let { throw PropuestaInvalidaPublica(it) }
    if (texto.length < 6) throw PropuestaInvalidaPublica("Eso es muy corto para recordarlo: escribe la frase completa.")
    val yaLoSabe = memoriaDe(uid).firstOrNull { normalizarParaBuscar(it.texto) == normalizarParaBuscar(texto) }
    if (yaLoSabe != null) throw PropuestaInvalidaPublica("Eso ya lo recuerdas: «${yaLoSabe.texto}».")
    if (memoriaDe(uid).size >= RECUERDOS_MAXIMOS) {
        throw PropuestaInvalidaPublica("Ya hay $RECUERDOS_MAXIMOS recuerdos guardados; dile que borre alguno en «Lo que Movi sabe de ti».")
    }
    return AccionPropuesta(
        id = "ap_" + UUID.randomUUID().toString().replace("-", "").take(20),
        tipo = TipoDeAccion.RECORDAR,
        frase = "Recordar: «$texto»",
        recuerdo = texto,
    )
}

/** La forma de decir «esta propuesta no» desde fuera de `LoQueMoviPropone.kt`. */
internal class PropuestaInvalidaPublica(val motivo: String) : Exception(motivo)
