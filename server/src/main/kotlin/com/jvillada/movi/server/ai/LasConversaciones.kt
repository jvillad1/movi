package com.jvillada.movi.server.ai

import com.jvillada.movi.server.db.AccionesPropuestas
import com.jvillada.movi.server.db.AiTurns
import com.jvillada.movi.server.db.CONVERSACION_BORRADA
import com.jvillada.movi.server.db.ConversacionesConElAsistente
import com.jvillada.movi.server.db.ConversacionesDelAsistente
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.dbQuery
import com.jvillada.movi.shared.model.CONVERSACIONES_QUE_SE_GUARDAN
import com.jvillada.movi.shared.model.ConversacionDelAsistente
import com.jvillada.movi.shared.model.ConversacionEnLaLista
import com.jvillada.movi.shared.model.ListaDeConversaciones
import com.jvillada.movi.shared.model.TITULO_SIN_TEXTO
import com.jvillada.movi.shared.model.TURNOS_QUE_SE_GUARDAN_POR_CONVERSACION
import com.jvillada.movi.shared.model.tituloDeLaConversacion
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

/**
 * # Varias conversaciones con Movi AI (vuelta 117)
 *
 * Pedido del dueño el 2-nov-2026: «el chat de AI no me lista los chats del pasado, me gustaría poder
 * abrir un chat anterior o eliminarlo y navegar entre múltiples chats». Hasta acá había UNA
 * conversación por dueño: «Nueva conversación» movía una marca ([ConversacionesDelAsistente]) y lo de
 * antes quedaba en `ai_turns` sin forma de verlo.
 *
 * **El modelo:** una fila por conversación en [ConversacionesConElAsistente] (id, título, cuándo
 * empezó y cuándo se le preguntó por última vez) y cada turno de [AiTurns] con su `conversacion_id`.
 *
 * - **El título** es la primera pregunta, recortada sin partir una palabra (`tituloDeLaConversacion`
 *   en `:core`), sin llamar a la IA. Se pone cuando llega esa pregunta: antes la conversación está
 *   vacía, **no se lista**, y «Nueva conversación» sobre ella no crea otra ([conversacionNueva]).
 * - **La más reciente** es la de la última pregunta ([ConversacionesConElAsistente.ultimaEn]): la
 *   que siguen un APK viejo (`GET /api/ai/conversacion`) y una pregunta sin id.
 * - **Los turnos de antes** (sin conversación) se agrupan al arrancar con la única marca que había,
 *   la de la última «Nueva conversación»: los anteriores a ella son una conversación y los
 *   posteriores otra (ver [agruparLosTurnosSueltos]).
 * - **Retención:** las últimas `CONVERSACIONES_QUE_SE_GUARDAN` (50), cada una con sus últimos
 *   `TURNOS_QUE_SE_GUARDAN_POR_CONVERSACION` (200) turnos. La que pasa el tope se borra entera al
 *   empezar la siguiente, y la lista lo dice (`textoDeLaRetencion`).
 * - **Borrar es de verdad** ([borrarLasConversacionesEn]): los turnos y sus propuestas se van. La
 *   única excepción no guarda texto: un turno que fue al modelo dentro de la ventana que cuenta el
 *   tope del mes y el costo (`DIAS_QUE_NO_SE_PODAN`) queda como una cifra —fecha, modelo y fichas,
 *   sin pregunta ni respuesta— hasta que lo saque `podarLasConversaciones` (al empezar otra
 *   conversación, si ya salió de la ventana). Si no, borrar una conversación le
 *   devolvería preguntas del tope.
 *
 * Todo filtra por el `user_id` del token: una conversación ajena no se ve, no se borra y no recibe
 * preguntas (un id ajeno o borrado empieza una nueva, nunca escribe en la de otro).
 */

/** Un id de conversación nuevo: `cv_` y veinte hexadecimales, como los turnos. */
internal fun nuevoIdDeConversacion(): String = "cv_" + UUID.randomUUID().toString().replace("-", "").take(20)

/**
 * El id de la conversación que agrupa los turnos sueltos cuyo primero es [primerTurno]. Sale del
 * turno y no del azar: si el arranque se corta a la mitad, la siguiente vuelta llega al mismo id.
 */
internal fun idDeLaConversacionAgrupada(primerTurno: String): String = "cv_" + primerTurno.removePrefix("ai_").take(40)

private const val TOPE_DEL_TITULO = 80

/** El título tal como cabe en la columna. */
private fun tituloParaLaColumna(pregunta: String, teniaImagen: Boolean): String =
    tituloDeLaConversacion(pregunta, teniaImagen).take(TOPE_DEL_TITULO)

/** Una fila de [AiTurns] sin conversación, lo justo para agruparla. */
private data class TurnoSuelto(val id: String, val dueno: String, val creadoEn: Long, val pregunta: String, val imagen: Boolean)

/**
 * **Los turnos de antes de la vuelta 117 entran a conversaciones.** Para cada dueño con turnos sin
 * `conversacion_id`: la marca de su última «Nueva conversación» ([ConversacionesDelAsistente], la
 * única forma de cortarlos que había) los parte en dos —los de hasta la marca, y los de después, que
 * eran la conversación en curso— y cada parte es una conversación con el título de su primera
 * pregunta. Sin marca, todos van en una.
 *
 * Si la marca es posterior al último turno, el dueño acababa de tocar «Nueva conversación» y su chat
 * se veía vacío: se crea una conversación vacía en la marca, para que siga viéndose vacío (es la más
 * reciente) en vez de reabrir la anterior.
 *
 * Idempotente: solo mira turnos con `conversacion_id` NULL, y los deja con uno. Corre al arrancar
 * (`Migrations.runAll`, [uid] `null` = todos) y, para un dueño, antes de leer su lista: si un server
 * viejo guardó un turno durante un despliegue, entra igual. Devuelve cuántos turnos agrupó.
 */
fun agruparLosTurnosSueltos(transaccion: Transaction, uid: String?): Int = transaccion.agruparLosTurnosSueltosEn(uid)

private fun Transaction.agruparLosTurnosSueltosEn(uid: String?): Int {
    val duenos = AiTurns.select(AiTurns.userId)
        .where { AiTurns.conversacionId.isNull() and (if (uid != null) AiTurns.userId eq uid else Op.TRUE) }
        .withDistinct()
        .map { it[AiTurns.userId] }
    if (duenos.isEmpty()) return 0
    val marcas = ConversacionesDelAsistente.selectAll()
        .where { ConversacionesDelAsistente.userId inList duenos }
        .associate { it[ConversacionesDelAsistente.userId] to it[ConversacionesDelAsistente.empezadaEn] }
    var agrupados = 0
    duenos.forEach { dueno ->
        // Revisión de la vuelta 117 · **de a un dueño, con su fila bloqueada.** Dos lecturas a la vez
        // (la lista y una pregunta, durante un despliegue) veían los mismos turnos sueltos y las dos
        // insertaban la misma conversación: la segunda chocaba en la clave. Con el bloqueo, la que llega
        // después espera, y al releer ya no encuentra nada suelto.
        Users.selectAll().where { Users.id eq dueno }.forUpdate().toList()
        val turnos = AiTurns.select(AiTurns.id, AiTurns.userId, AiTurns.creadoEn, AiTurns.pregunta, AiTurns.imagen)
            .where { AiTurns.conversacionId.isNull() and (AiTurns.userId eq dueno) }
            .orderBy(AiTurns.creadoEn to SortOrder.ASC, AiTurns.id to SortOrder.ASC)
            .map { TurnoSuelto(it[AiTurns.id], it[AiTurns.userId], it[AiTurns.creadoEn], it[AiTurns.pregunta], it[AiTurns.imagen]) }
        if (turnos.isNotEmpty()) {
            agruparLosDeUnDueno(dueno, turnos, marcas[dueno] ?: 0L)
            agrupados += turnos.size
        }
    }
    return agrupados
}

/** Los turnos sueltos de [dueno] (del más viejo al más nuevo), partidos por su [marca]. */
private fun Transaction.agruparLosDeUnDueno(dueno: String, turnos: List<TurnoSuelto>, marca: Long) {
        // Lo mismo que leía la Ola 3: la conversación en curso eran los turnos POSTERIORES a la marca.
        val (antes, despues) = turnos.partition { it.creadoEn <= marca }
        listOf(antes, despues).filter { it.isNotEmpty() }.forEach { bloque ->
            val idDelBloque = idDeLaConversacionAgrupada(bloque.first().id)
            val existe = ConversacionesConElAsistente.selectAll()
                .where { (ConversacionesConElAsistente.id eq idDelBloque) and (ConversacionesConElAsistente.userId eq dueno) }
                .firstOrNull()
            if (existe == null) {
                ConversacionesConElAsistente.insert {
                    it[ConversacionesConElAsistente.id] = idDelBloque
                    it[userId] = dueno
                    it[titulo] = tituloParaLaColumna(bloque.first().pregunta, bloque.first().imagen)
                    it[creadaEn] = bloque.first().creadoEn
                    it[ultimaEn] = bloque.last().creadoEn
                }
            }
            bloque.chunked(500).forEach { tanda ->
                AiTurns.update({ (AiTurns.userId eq dueno) and (AiTurns.id inList tanda.map { it.id }) }) {
                    it[conversacionId] = idDelBloque
                }
            }
        }
        if (despues.isEmpty() && marca > turnos.last().creadoEn) {
            val hayUnaPosterior = ConversacionesConElAsistente.selectAll()
                .where { (ConversacionesConElAsistente.userId eq dueno) and (ConversacionesConElAsistente.ultimaEn greaterEq marca) }
                .any()
            if (!hayUnaPosterior) {
                ConversacionesConElAsistente.insert {
                    it[id] = nuevoIdDeConversacion()
                    it[userId] = dueno
                    it[titulo] = ""
                    it[creadaEn] = marca
                    it[ultimaEn] = marca
                }
            }
        }
}

/**
 * [agruparLosTurnosSueltos] de [uid] **en su propia transacción y sin poder romper nada**: lo llaman la
 * lista, el chat y «Nueva» antes de lo suyo. Si falla, lo de ellos sigue (el turno del chat se guarda
 * igual; a lo sumo queda suelto hasta la próxima lectura, que lo vuelve a intentar).
 */
internal suspend fun agruparLosTurnosSueltosSinRomper(uid: String) {
    runCatching { dbQuery { agruparLosTurnosSueltos(this, uid) } }
}

/** Cuántos turnos tiene cada conversación de [uid] que tiene alguno. */
private fun Transaction.turnosPorConversacion(uid: String): Map<String, Int> {
    val cuantos = AiTurns.id.count()
    return AiTurns.select(AiTurns.conversacionId, cuantos)
        .where { (AiTurns.userId eq uid) and AiTurns.conversacionId.isNotNull() and (AiTurns.conversacionId neq CONVERSACION_BORRADA) }
        .groupBy(AiTurns.conversacionId)
        .mapNotNull { fila -> fila[AiTurns.conversacionId]?.let { it to fila[cuantos].toInt() } }
        .toMap()
}

/** La conversación [id] si es de [uid]; `null` si no existe o es de otro (las rutas contestan 404 igual). */
private fun Transaction.conversacionDe(uid: String, id: String): ResultRow? =
    ConversacionesConElAsistente.selectAll()
        .where { (ConversacionesConElAsistente.id eq id) and (ConversacionesConElAsistente.userId eq uid) }
        .firstOrNull()

/** Las conversaciones de [uid], de la más reciente a la más vieja (por la última pregunta). */
private fun Transaction.conversacionesDe(uid: String): List<ResultRow> =
    ConversacionesConElAsistente.selectAll()
        .where { ConversacionesConElAsistente.userId eq uid }
        .orderBy(ConversacionesConElAsistente.ultimaEn to SortOrder.DESC, ConversacionesConElAsistente.creadaEn to SortOrder.DESC)
        .toList()

/** `GET /api/ai/conversaciones`: las que tienen alguna pregunta, la más reciente primero. */
suspend fun listaDeConversaciones(uid: String): ListaDeConversaciones {
    agruparLosTurnosSueltosSinRomper(uid)
    return dbQuery { listaDeConversacionesEn(uid) }
}

private fun Transaction.listaDeConversacionesEn(uid: String): ListaDeConversaciones {
    val cuantos = turnosPorConversacion(uid)
    return ListaDeConversaciones(
        conversaciones = conversacionesDe(uid).mapNotNull { fila ->
            val id = fila[ConversacionesConElAsistente.id]
            val turnos = cuantos[id] ?: return@mapNotNull null
            ConversacionEnLaLista(
                id = id,
                titulo = fila[ConversacionesConElAsistente.titulo].ifBlank { TITULO_SIN_TEXTO },
                creadaEn = fila[ConversacionesConElAsistente.creadaEn],
                ultimaEn = fila[ConversacionesConElAsistente.ultimaEn],
                mensajes = turnos * 2,
            )
        },
        tope = CONVERSACIONES_QUE_SE_GUARDAN,
        turnosPorConversacion = TURNOS_QUE_SE_GUARDAN_POR_CONVERSACION,
    )
}

/** `GET /api/ai/conversaciones/{id}`: sus turnos, con la forma de siempre; `null` si no es de [uid]. */
suspend fun conversacionAbierta(uid: String, id: String, cuantos: Int = TURNOS_DE_UNA_CONVERSACION_QUE_SE_VEN): ConversacionDelAsistente? {
    val (titulo, turnos) = dbQuery {
        val fila = conversacionDe(uid, id) ?: return@dbQuery null
        fila[ConversacionesConElAsistente.titulo] to turnosDeLaConversacionEn(uid, id, cuantos)
    } ?: return null
    return comoLaPintaLaPantalla(uid, id, titulo, turnos)
}

/**
 * `GET /api/ai/conversacion` (un APK viejo): la más reciente, con sus últimos [TURNOS_QUE_SE_VEN]
 * turnos. Sin ninguna, vacía y sin id.
 */
suspend fun conversacionMasReciente(uid: String): ConversacionDelAsistente {
    agruparLosTurnosSueltosSinRomper(uid)
    val reciente = dbQuery {
        conversacionesDe(uid).firstOrNull()?.let { fila ->
            val id = fila[ConversacionesConElAsistente.id]
            Triple(id, fila[ConversacionesConElAsistente.titulo], turnosDeLaConversacionEn(uid, id, TURNOS_QUE_SE_VEN))
        }
    } ?: return ConversacionDelAsistente()
    return comoLaPintaLaPantalla(uid, reciente.first, reciente.second, reciente.third)
}

/**
 * **«Nueva conversación»** (`POST /api/ai/conversaciones` y el `…/conversacion/nueva` de siempre). Si
 * el dueño ya tiene una vacía, es esa —pasa a ser la más reciente— y no se crea otra: tocar «Nueva»
 * dos veces no deja dos vacías. Si no, una nueva, vacía y sin título.
 */
suspend fun conversacionNueva(uid: String, ahora: Long = System.currentTimeMillis()): ConversacionEnLaLista {
    agruparLosTurnosSueltosSinRomper(uid)
    return dbQuery { conversacionNuevaEn(uid, ahora) }
}

private fun Transaction.conversacionNuevaEn(uid: String, ahora: Long): ConversacionEnLaLista {
    val conTurnos = turnosPorConversacion(uid).keys
    val vacias = conversacionesDe(uid).filter { it[ConversacionesConElAsistente.id] !in conTurnos }
    val vacia = vacias.firstOrNull()
    // Dos vacías solo pueden quedar de una carrera (dos «Nueva» a la vez): sobra la de más.
    val sobran = vacias.drop(1).map { it[ConversacionesConElAsistente.id] }
    if (sobran.isNotEmpty()) {
        ConversacionesConElAsistente.deleteWhere { (userId eq uid) and (ConversacionesConElAsistente.id inList sobran) }
    }
    val id = vacia?.get(ConversacionesConElAsistente.id) ?: nuevoIdDeConversacion()
    if (vacia != null) {
        ConversacionesConElAsistente.update({ (ConversacionesConElAsistente.id eq id) and (ConversacionesConElAsistente.userId eq uid) }) {
            it[creadaEn] = ahora
            it[ultimaEn] = ahora
        }
    } else {
        ConversacionesConElAsistente.insert {
            it[ConversacionesConElAsistente.id] = id
            it[userId] = uid
            it[titulo] = ""
            it[creadaEn] = ahora
            it[ultimaEn] = ahora
        }
    }
    return ConversacionEnLaLista(id = id, titulo = "", creadaEn = ahora, ultimaEn = ahora, mensajes = 0)
}

/**
 * **A qué conversación va una pregunta.** La [pedida] si es de [uid]; sin id, la más reciente; y si
 * no tiene ninguna —o la pedida no es suya o ya la borró—, una nueva ([conversacionNuevaEn], que
 * reusa la vacía). Nunca escribe en la de otro.
 */
internal fun Transaction.conversacionParaLaPreguntaEn(uid: String, pedida: String?, ahora: Long): String {
    if (pedida != null) {
        if (conversacionDe(uid, pedida) != null) return pedida
        return conversacionNuevaEn(uid, ahora).id
    }
    return conversacionesDe(uid).firstOrNull()?.get(ConversacionesConElAsistente.id) ?: conversacionNuevaEn(uid, ahora).id
}

/**
 * [conversacionParaLaPreguntaEn] en su propia transacción: el chat la pide antes de llamar al modelo.
 * Los turnos sueltos se agrupan antes y aparte ([agruparLosTurnosSueltosSinRomper]).
 */
suspend fun conversacionParaLaPregunta(uid: String, pedida: String?, ahora: Long = System.currentTimeMillis()): String {
    agruparLosTurnosSueltosSinRomper(uid)
    return dbQuery { conversacionParaLaPreguntaEn(uid, pedida, ahora) }
}

/**
 * **Lo que hace un turno recién guardado en su conversación**: la deja como la más reciente y, si era
 * su primera pregunta, le pone el título y aplica la retención (la que pasa del tope se borra: ver
 * [podarLasConversaciones]).
 */
internal fun Transaction.anotarElTurnoEnSuConversacion(uid: String, conversacionId: String, pregunta: String, hayImagen: Boolean, ahora: Long) {
    val fila = conversacionDe(uid, conversacionId) ?: return
    val primera = fila[ConversacionesConElAsistente.titulo].isBlank()
    ConversacionesConElAsistente.update({ (ConversacionesConElAsistente.id eq conversacionId) and (ConversacionesConElAsistente.userId eq uid) }) {
        it[ultimaEn] = maxOf(ahora, fila[ConversacionesConElAsistente.ultimaEn])
        if (primera) it[titulo] = tituloParaLaColumna(pregunta, hayImagen)
    }
    if (primera) podarLasConversaciones(uid, conservar = conversacionId, ahora = ahora)
    podarLosTurnosDeLaConversacion(uid, conversacionId, ahora)
}

/**
 * Revisión de la vuelta 117 · **el tope por conversación**: de cada una quedan sus últimos
 * [TURNOS_QUE_SE_GUARDAN_POR_CONVERSACION] turnos (el mismo número que el tope por dueño de antes);
 * los más viejos se van con la regla de borrar ([borrarLosTurnosEn]). Sin esto, una conversación del
 * admin (sin tope de preguntas) o de las tres sugeridas (que no cuentan) crecía sin límite.
 */
internal fun Transaction.podarLosTurnosDeLaConversacion(uid: String, conversacionId: String, ahora: Long) {
    val sobran = AiTurns.select(AiTurns.id, AiTurns.modelo, AiTurns.creadoEn)
        .where { (AiTurns.userId eq uid) and (AiTurns.conversacionId eq conversacionId) }
        .orderBy(AiTurns.creadoEn to SortOrder.DESC, AiTurns.id to SortOrder.DESC)
        .drop(TURNOS_QUE_SE_GUARDAN_POR_CONVERSACION)
        .map { Triple(it[AiTurns.id], it[AiTurns.modelo], it[AiTurns.creadoEn]) }
    borrarLosTurnosEn(uid, sobran, ahora)
}

/**
 * **La retención**: de [uid] quedan las últimas [CONVERSACIONES_QUE_SE_GUARDAN] con preguntas (por la
 * última pregunta); las demás se borran enteras con [borrarLasConversacionesEn]. [conservar] —la que
 * acaba de recibir su primera pregunta— nunca es la que se va. Y lo que quedó de turnos borrados
 * ([CONVERSACION_BORRADA]) se va cuando sale de la ventana del tope.
 */
internal fun Transaction.podarLasConversaciones(uid: String, conservar: String?, ahora: Long) {
    val conTurnos = turnosPorConversacion(uid).keys
    val sobran = conversacionesDe(uid)
        .map { it[ConversacionesConElAsistente.id] }
        .filter { it in conTurnos }
        .filter { it != conservar }
        .drop(CONVERSACIONES_QUE_SE_GUARDAN - if (conservar != null && conservar in conTurnos) 1 else 0)
    if (sobran.isNotEmpty()) borrarLasConversacionesEn(uid, sobran, ahora)
    val desde = desdeCuandoNoSePoda(ahora)
    AiTurns.deleteWhere {
        (AiTurns.userId eq uid) and (AiTurns.conversacionId eq CONVERSACION_BORRADA) and (AiTurns.creadoEn less desde)
    }
}

/**
 * **Borra de verdad las conversaciones [ids] de [uid]**: sus propuestas, sus turnos y la fila. Un
 * turno que fue al modelo dentro de la ventana del tope ([desdeCuandoNoSePoda]) queda sin texto y
 * suelto ([CONVERSACION_BORRADA]): solo su fecha, su modelo y sus fichas, que es lo que cuentan el
 * tope del mes y el costo.
 */
internal fun Transaction.borrarLasConversacionesEn(uid: String, ids: List<String>, ahora: Long) {
    if (ids.isEmpty()) return
    val turnos = AiTurns.select(AiTurns.id, AiTurns.modelo, AiTurns.creadoEn)
        .where { (AiTurns.userId eq uid) and (AiTurns.conversacionId inList ids) }
        .map { Triple(it[AiTurns.id], it[AiTurns.modelo], it[AiTurns.creadoEn]) }
    borrarLosTurnosEn(uid, turnos, ahora)
    ConversacionesConElAsistente.deleteWhere { (userId eq uid) and (ConversacionesConElAsistente.id inList ids) }
}

/**
 * **Borra [turnos] de [uid]** (id, modelo, fecha) con sus propuestas. El que fue al modelo dentro de
 * la ventana del tope ([desdeCuandoNoSePoda]) queda sin texto y suelto ([CONVERSACION_BORRADA]).
 */
internal fun Transaction.borrarLosTurnosEn(uid: String, turnos: List<Triple<String, String, Long>>, ahora: Long) {
    if (turnos.isEmpty()) return
    val desde = desdeCuandoNoSePoda(ahora)
    val (quedanComoCifra, seBorran) = turnos.partition { (_, modelo, creadoEn) -> modelo != MODELO_SIN_IA && creadoEn >= desde }
    turnos.map { it.first }.chunked(500).forEach { tanda ->
        AccionesPropuestas.deleteWhere { (AccionesPropuestas.userId eq uid) and (AccionesPropuestas.turnoId inList tanda) }
    }
    seBorran.map { it.first }.chunked(500).forEach { tanda ->
        AiTurns.deleteWhere { (AiTurns.userId eq uid) and (AiTurns.id inList tanda) }
    }
    quedanComoCifra.map { it.first }.chunked(500).forEach { tanda ->
        AiTurns.update({ (AiTurns.userId eq uid) and (AiTurns.id inList tanda) }) {
            it[pregunta] = ""
            it[respuesta] = ""
            it[consultas] = "[]"
            it[imagen] = false
            it[cifrasSinRespaldo] = null
            it[cifrasCorregidas] = null
            it[conversacionId] = CONVERSACION_BORRADA
        }
    }
}

/** `DELETE /api/ai/conversaciones/{id}`: `false` si no existe o es de otro (la ruta contesta 404). */
suspend fun borrarLaConversacion(uid: String, id: String, ahora: Long = System.currentTimeMillis()): Boolean = dbQuery {
    if (conversacionDe(uid, id) == null) return@dbQuery false
    borrarLasConversacionesEn(uid, listOf(id), ahora)
    true
}
