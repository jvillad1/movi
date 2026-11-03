package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.AiTurns
import com.jvillada.movi.server.db.dbQuery
import org.jetbrains.exposed.sql.insert
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

/**
 * Vuelta 106 · los días hacia atrás que la poda respeta: con esos turnos se cuenta el tope de
 * preguntas (`ElTopeDelMes`) y lo que gastó cada uno en 30 días. Desde la vuelta 117 lo que se borra
 * dentro de esta ventana queda como cifra sin texto (ver `borrarLasConversacionesEn`).
 */
internal const val DIAS_QUE_NO_SE_PODAN = 31L
private const val UN_DIA = 24L * 60 * 60 * 1000

/** Desde cuándo un turno que fue al modelo no se puede borrar entero: el mes en curso y los últimos 31 días. */
internal fun desdeCuandoNoSePoda(ahora: Long): Long =
    minOf(ElTopeDelMes.inicioDelMes(ahora), ahora - DIAS_QUE_NO_SE_PODAN * UN_DIA)

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
    /** Vuelta 106 · las escritas en la caché, que se cobran aparte (ver `AiTurns.fichasCacheEscritas`). */
    fichasCacheEscritas: Long = 0,
    /** Las que llegaron al dueño sin respaldo (ver `responderSinInventar`). Vacío se guarda NULL. */
    cifrasSinRespaldo: List<String> = emptyList(),
    /** Las que dispararon el reintento. Vacío se guarda NULL. */
    cifrasCorregidas: List<String> = emptyList(),
    /**
     * El id del turno. Lo elige quien llama cuando necesita colgarle algo —las propuestas de la
     * Ola 3 se guardan con el id del turno que las hizo, para volver a pintarlas en su globo—.
     */
    id: String = nuevoIdDeTurno(),
    /**
     * Vuelta 117 · la conversación del turno (la que eligió el chat con `conversacionParaLaPregunta`).
     * `null` = la más reciente del dueño, o una nueva si no tiene: lo de siempre para quien no sabe de
     * conversaciones.
     */
    conversacionId: String? = null,
): Boolean = runCatching {
    val comoJson = json.encodeToString(
        consultas.map {
            ConsultaGuardada(it.herramienta, it.argumentos, it.devolvio.take(TOPE_CONSULTAS / (consultas.size.coerceAtLeast(1))))
        },
    ).take(TOPE_CONSULTAS)

    // Revisión de la vuelta 117: los turnos sueltos se agrupan antes y en su propia transacción, así un
    // choque ahí (dos lecturas a la vez durante un despliegue) nunca se lleva este turno.
    if (conversacionId == null) agruparLosTurnosSueltosSinRomper(uid)
    dbQuery {
        val conversacion = conversacionParaLaPreguntaEn(uid, conversacionId, ahora)
        AiTurns.insert {
            it[AiTurns.id] = id
            it[AiTurns.conversacionId] = conversacion
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
            it[AiTurns.fichasCacheEscritas] = fichasCacheEscritas
            it[imagen] = hayImagen
            it[AiTurns.cifrasSinRespaldo] = cifrasSinRespaldo.comoColumna()
            it[AiTurns.cifrasCorregidas] = cifrasCorregidas.comoColumna()
        }
        // Vuelta 117 · la conversación pasa a ser la más reciente; con su primera pregunta toma el
        // título y se aplica la retención: de cada dueño quedan las últimas 50 conversaciones (antes,
        // los últimos 200 turnos, que con la lista a la vista le habrían borrado chats en silencio).
        anotarElTurnoEnSuConversacion(uid, conversacion, pregunta, hayImagen, ahora)
    }
    true
}.getOrDefault(false)

/** Un id de turno nuevo, con el mismo formato de siempre. */
fun nuevoIdDeTurno(): String = "ai_" + UUID.randomUUID().toString().replace("-", "").take(20)

/** «$185.831 · 12 %», o NULL: así un `is not null` cuenta los turnos con cifras sin respaldo. */
private fun List<String>.comoColumna(): String? =
    takeIf { it.isNotEmpty() }?.joinToString(" · ")?.take(TOPE_CIFRAS)

private const val TOPE_CIFRAS = 1_000
