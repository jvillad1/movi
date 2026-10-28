package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.AiTurns
import com.jvillada.movi.server.db.ConversacionesDelAsistente
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.shared.model.ChatMessage
import com.jvillada.movi.shared.model.ChatRole
import com.jvillada.movi.shared.model.ConversacionDelAsistente
import com.jvillada.movi.shared.model.PreguntaSugerida
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * # La conversación sobrevive (Ola 3)
 *
 * El chat vivía en un `remember` de la pantalla: salir a mirar un movimiento y volver era empezar de
 * cero. El server ya guardaba cada turno en [AiTurns] —para diagnosticar—, así que la conversación
 * no se perdía: solo no había cómo pedirla de vuelta. Esto es esa puerta, y la de «Nueva
 * conversación».
 *
 * Dos decisiones:
 *
 * - **Se devuelve la conversación EN CURSO, no el historial.** «Nueva conversación» no borra nada:
 *   marca desde cuándo corre la actual ([ConversacionesDelAsistente]), y los turnos viejos siguen en
 *   [AiTurns] para diagnosticar, hasta que la poda los saque.
 * - **Lo que se pinta no es lo que viaja al modelo.** El dueño ve los últimos [TURNOS_QUE_SE_VEN];
 *   al modelo se le manda solo el tramo de [tramoParaElModelo], con tope de mensajes y de
 *   caracteres. Ver ahí por qué.
 */

/** Cuántos turnos (pregunta + respuesta) se le muestran al volver. Veinte son cuarenta globos. */
internal const val TURNOS_QUE_SE_VEN = 20

/**
 * **Tope del tramo que viaja al modelo, en caracteres.** El de mensajes ([ULTIMOS_MENSAJES_QUE_VIAJAN])
 * no alcanza solo: ocho mensajes de consejo largos son miles de fichas que se pagan en CADA pregunta
 * siguiente, y no van cacheados (van después del contexto). Doce mil caracteres son unas tres mil
 * fichas en español: alcanza para que entienda «¿y en julio?» y no crece con la charla.
 */
internal const val TOPE_DE_CARACTERES_DEL_TRAMO = 12_000

/**
 * **El tramo reciente que se le manda al modelo**: los últimos [ULTIMOS_MENSAJES_QUE_VIAJAN], y de
 * esos, solo los que entran en [TOPE_DE_CARACTERES_DEL_TRAMO] contando desde el final. El último
 * mensaje —la pregunta que hay que contestar— va siempre, aunque solo él pase el tope.
 *
 * El `dropWhile` va AL FINAL: si al cortar queda una respuesta del asistente adelante, la API
 * rechaza la conversación entera (ver [com.jvillada.movi.server.routes.mensajesParaElModelo]).
 */
fun tramoParaElModelo(mensajes: List<ChatMessage>): List<ChatMessage> {
    val ultimos = mensajes.takeLast(ULTIMOS_MENSAJES_QUE_VIAJAN)
    val tramo = ArrayDeque<ChatMessage>()
    var usados = 0
    for (mensaje in ultimos.asReversed()) {
        val largo = mensaje.content.length
        if (tramo.isNotEmpty() && usados + largo > TOPE_DE_CARACTERES_DEL_TRAMO) break
        tramo.addFirst(mensaje)
        usados += largo
    }
    return tramo.toList().dropWhile { it.role != ChatRole.USER }
}

/** Desde cuándo corre la conversación en curso de [uid]; 0 si nunca empezó una nueva. */
internal suspend fun conversacionEmpezadaEn(uid: String): Long = dbQuery {
    ConversacionesDelAsistente.selectAll()
        .where { ConversacionesDelAsistente.userId eq uid }
        .firstOrNull()?.get(ConversacionesDelAsistente.empezadaEn) ?: 0L
}

/** Una fila de [AiTurns] de la conversación en curso, lo justo para pintarla. */
internal data class TurnoGuardado(
    val id: String,
    val pregunta: String,
    val respuesta: String,
    val teniaImagen: Boolean,
    /** Cuándo se contestó (`ai_turns.created_at`). */
    val creadoEn: Long = 0L,
    /** El id de la pregunta sugerida que contestó sin modelo, o `null` (ver [preguntaSugeridaDelTurno]). */
    val preguntaSugerida: String? = null,
)

private val jsonDeLasConsultas = Json { ignoreUnknownKeys = true }

/**
 * **El id de la pregunta sugerida que contestó un turno sin modelo**, leído de lo que ese turno dejó
 * anotado (`modelo` = [MODELO_SIN_IA] y la consulta [CONSULTA_DE_LA_PREGUNTA_SUGERIDA] en
 * `consultas`). Vuelve en el globo recargado para que «Preguntar de nuevo» la mande con su id y la
 * siga contestando Movi sin la IA: se reconoce por el id, nunca por el texto. Lo demás, `null`.
 */
internal fun preguntaSugeridaDelTurno(modelo: String, consultas: String): String? {
    if (modelo != MODELO_SIN_IA) return null
    val guardadas = runCatching {
        jsonDeLasConsultas.decodeFromString(ListSerializer(ConsultaGuardada.serializer()), consultas)
    }.getOrNull() ?: return null
    return guardadas.firstOrNull { it.herramienta == CONSULTA_DE_LA_PREGUNTA_SUGERIDA }?.argumentos?.get("id")
        ?.takeIf { PreguntaSugerida.deId(it) != null }
}

/** Los últimos [TURNOS_QUE_SE_VEN] turnos de la conversación en curso de [uid], del más viejo al más nuevo. */
internal suspend fun turnosDeLaConversacion(uid: String): List<TurnoGuardado> {
    val desde = conversacionEmpezadaEn(uid)
    return dbQuery {
        AiTurns.selectAll()
            .where { (AiTurns.userId eq uid) and (AiTurns.creadoEn greater desde) }
            .orderBy(AiTurns.creadoEn to SortOrder.DESC)
            .limit(TURNOS_QUE_SE_VEN)
            .map {
                TurnoGuardado(
                    id = it[AiTurns.id],
                    pregunta = it[AiTurns.pregunta],
                    respuesta = it[AiTurns.respuesta],
                    teniaImagen = it[AiTurns.imagen],
                    creadoEn = it[AiTurns.creadoEn],
                    preguntaSugerida = preguntaSugeridaDelTurno(it[AiTurns.modelo], it[AiTurns.consultas]),
                )
            }
            .reversed()
    }
}

/**
 * La conversación en curso, como la pinta la pantalla: por cada turno, la pregunta del dueño y la
 * respuesta del asistente.
 */
suspend fun conversacionGuardada(uid: String): ConversacionDelAsistente {
    val turnos = turnosDeLaConversacion(uid)
    // Las tarjetas que propuso cada turno, con su estado de HOY: una que el dueño ya hizo vuelve
    // como hecha, no con «Hacerlo» otra vez.
    val propuestas = propuestasDeLosTurnos(uid, turnos.map { it.id })
    return ConversacionDelAsistente(
        mensajes = turnos.flatMap { turno ->
            // Vuelta de diseño 30: con su fecha (la del turno, en los dos globos), para que el chat
            // diga de qué día es cada respuesta; y la pregunta sugerida con su id.
            listOf(
                ChatMessage(
                    ChatRole.USER, turno.pregunta, teniaImagen = turno.teniaImagen,
                    preguntaSugerida = turno.preguntaSugerida, enviadoEn = turno.creadoEn,
                ),
                ChatMessage(
                    ChatRole.ASSISTANT, turno.respuesta, propuestas = propuestas[turno.id].orEmpty(),
                    enviadoEn = turno.creadoEn,
                ),
            )
        },
    )
}

/** «Nueva conversación»: la en curso pasa a empezar ahora. Idempotente. */
suspend fun empezarConversacionNueva(uid: String, ahora: Long = System.currentTimeMillis()) {
    dbQuery {
        val actualizadas = ConversacionesDelAsistente.update({ ConversacionesDelAsistente.userId eq uid }) {
            it[empezadaEn] = ahora
        }
        if (actualizadas == 0) {
            ConversacionesDelAsistente.insert {
                it[userId] = uid
                it[empezadaEn] = ahora
            }
        }
    }
}
