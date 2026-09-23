package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.AiTurns
import com.jvillada.movi.server.db.dbQuery
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * # Guardar lo que el dueño le preguntó al asistente
 *
 * Nació de un diagnóstico imposible: el chat vivía solo en el teléfono, así que cuando una
 * respuesta salía mal, del lado del server solo quedaban las fichas gastadas. «El asistente no
 * supo» no se arregla sin saber **qué se preguntó, qué consultó y qué contestó** — y averiguarlo
 * dependía de que el dueño lo contara de memoria o mandara una foto de la pantalla.
 *
 * Lo pidió él, y lo pidió sabiendo qué se guarda. Las reglas están en [AiTurns]; acá vive la única
 * que es de código y no de esquema:
 *
 * **Guardar no puede romper una respuesta.** Si el insert falla —la tabla no existe todavía, la
 * base está lenta, lo que sea—, el dueño igual recibe lo que preguntó. Una función de diagnóstico
 * que tumba la función que diagnostica es peor que no tenerla.
 */

/** Cuántas se guardan de cada dueño. Lo que sirve para diagnosticar es lo de ayer, no lo del año pasado. */
internal const val CUANTAS_CONVERSACIONES_SE_GUARDAN = 200

/** Topes por campo, para que una respuesta larga o una consulta gorda no crezcan la tabla sin límite. */
private const val TOPE_PREGUNTA = 2_000
private const val TOPE_RESPUESTA = 8_000
private const val TOPE_CONSULTAS = 12_000

private val json = Json { encodeDefaults = true }

/** Una consulta que el asistente hizo, tal como se guarda. */
@kotlinx.serialization.Serializable
internal data class ConsultaGuardada(
    val herramienta: String,
    val argumentos: Map<String, String>,
    val devolvio: String,
)

/**
 * Guarda el turno y poda lo viejo. **Nunca lanza**: devuelve `true` si guardó, `false` si algo
 * falló — quien llama solo lo usa para un log.
 */
suspend fun guardarLaConversacion(
    uid: String,
    pregunta: String,
    respuesta: String,
    consultas: List<ConsultaHecha>,
    modelo: String,
    criterio: Boolean,
    fichasEntrada: Long,
    fichasCache: Long,
    fichasSalida: Long,
    hayImagen: Boolean,
    ahora: Long = System.currentTimeMillis(),
    /** Las que llegaron al dueño sin respaldo (ver `responderSinInventar`). Vacío se guarda NULL. */
    cifrasSinRespaldo: List<String> = emptyList(),
    /** Las que dispararon el reintento. Vacío se guarda NULL. */
    cifrasCorregidas: List<String> = emptyList(),
): Boolean = runCatching {
    val comoJson = json.encodeToString(
        consultas.map {
            ConsultaGuardada(it.herramienta, it.argumentos, it.devolvio.take(TOPE_CONSULTAS / (consultas.size.coerceAtLeast(1))))
        },
    ).take(TOPE_CONSULTAS)

    dbQuery {
        AiTurns.insert {
            it[id] = "ai_" + UUID.randomUUID().toString().replace("-", "").take(20)
            it[userId] = uid
            it[creadoEn] = ahora
            it[AiTurns.pregunta] = pregunta.take(TOPE_PREGUNTA)
            it[AiTurns.respuesta] = respuesta.take(TOPE_RESPUESTA)
            it[AiTurns.consultas] = comoJson
            it[AiTurns.modelo] = modelo
            it[AiTurns.criterio] = criterio
            // El reintento del verificador es una vuelta más: sin contarlo, un turno corregido se
            // vería igual de barato que uno limpio.
            it[vueltas] = consultas.size + 1 + (if (cifrasCorregidas.isNotEmpty()) 1 else 0)
            it[AiTurns.fichasEntrada] = fichasEntrada
            it[AiTurns.fichasCache] = fichasCache
            it[AiTurns.fichasSalida] = fichasSalida
            it[imagen] = hayImagen
            it[AiTurns.cifrasSinRespaldo] = cifrasSinRespaldo.comoColumna()
            it[AiTurns.cifrasCorregidas] = cifrasCorregidas.comoColumna()
        }
        // Y se poda: de cada dueño quedan las últimas N. Se borra por id y no por fecha para que
        // dos turnos del mismo milisegundo no se lleven uno al otro por delante.
        val sobran = AiTurns.selectAll()
            .where { AiTurns.userId eq uid }
            .orderBy(AiTurns.creadoEn to SortOrder.DESC)
            .drop(CUANTAS_CONVERSACIONES_SE_GUARDAN)
            .map { it[AiTurns.id] }
        if (sobran.isNotEmpty()) {
            AiTurns.deleteWhere { AiTurns.id inList sobran }
        }
    }
    true
}.getOrDefault(false)

/** «$185.831 · 12 %», o NULL: así un `is not null` cuenta los turnos con cifras sin respaldo. */
private fun List<String>.comoColumna(): String? =
    takeIf { it.isNotEmpty() }?.joinToString(" · ")?.take(TOPE_CIFRAS)

private const val TOPE_CIFRAS = 1_000
