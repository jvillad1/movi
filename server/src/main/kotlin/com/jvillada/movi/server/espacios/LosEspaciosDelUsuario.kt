package com.jvillada.movi.server.espacios

import com.jvillada.movi.server.db.Espacios
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.GastosDelEspacio
import com.jvillada.movi.server.db.KnownDestinations
import com.jvillada.movi.server.db.LiquidacionesDelEspacio
import com.jvillada.movi.server.db.RecurrentesDelHogar
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.toFinancialEvent
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.LiquidacionDelEspacio
import com.jvillada.movi.shared.model.LoCompartido
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.MiembroDelEspacio
import com.jvillada.movi.shared.model.RepartoDelEspacio
import com.jvillada.movi.shared.model.RepartoDelGasto
import com.jvillada.movi.shared.model.esDelGrupo
import com.jvillada.movi.shared.model.loCompartidoDe
import com.jvillada.movi.shared.model.partesDelGasto
import com.jvillada.movi.shared.model.tuParteComoEventos
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.selectAll

private val jsonDeEspacios = Json { ignoreUnknownKeys = true }
private val serializadorDePartes = MapSerializer(String.serializer(), Long.serializer())
private val serializadorDeMiembros = ListSerializer(MiembroDelEspacio.serializer())

/**
 * Los miembros como se guardan: sin nombre (se resuelve al leer). El dueño solo va si el reparto
 * es por porcentajes (para guardar el suyo); con partes iguales es implícito.
 */
internal fun miembrosComoJson(miembros: List<MiembroDelEspacio>, reparto: RepartoDelEspacio): String =
    jsonDeEspacios.encodeToString(
        serializadorDeMiembros,
        miembros
            .filter { it.id != MIEMBRO_YO || reparto == RepartoDelEspacio.PORCENTAJES }
            .distinctBy { it.id }
            .map { it.copy(nombre = "", porcentaje = if (reparto == RepartoDelEspacio.PORCENTAJES) it.porcentaje else null) },
    )

private fun miembrosDeLaFila(json: String): List<MiembroDelEspacio> =
    runCatching { jsonDeEspacios.decodeFromString(serializadorDeMiembros, json) }.getOrDefault(emptyList())

internal fun partesComoJson(partes: Map<String, Long>): String = jsonDeEspacios.encodeToString(serializadorDePartes, partes)

/**
 * Vuelta 97 · el reparto propio como se guarda: `null` si es la regla del grupo (así un gasto que vuelve
 * a «Partes iguales» queda igual que uno de antes de esta vuelta).
 */
internal fun repartoComoJson(reparto: RepartoDelGasto?): String? =
    reparto?.takeIf { !esDelGrupo(it) }?.let { jsonDeEspacios.encodeToString(RepartoDelGasto.serializer(), it) }

internal fun repartoDeLaFila(json: String?): RepartoDelGasto? =
    json?.let { runCatching { jsonDeEspacios.decodeFromString(RepartoDelGasto.serializer(), it) }.getOrNull() }
        ?.takeIf { !esDelGrupo(it) }

private fun partesDeLaFila(json: String): Map<String, Long> =
    runCatching { jsonDeEspacios.decodeFromString(serializadorDePartes, json) }.getOrDefault(emptyMap())

/**
 * ¿Ya existen las tablas de los espacios? En producción siempre (las crea el arranque). La guarda es
 * para las bases de prueba de `:server` que arman solo las tablas que su ruta usa: sin ella, cada
 * lectura de movimientos —que ahora pregunta por lo compartido— se caería en una base sin espacios.
 * `exists()` lee una lista de tablas que el dialecto guarda en caché.
 */
internal fun Transaction.hayEspacios(): Boolean =
    runCatching { GastosDelEspacio.exists() && LiquidacionesDelEspacio.exists() && Espacios.exists() }.getOrDefault(false)

/** El nombre de cada miembro posible: el dueño por su nombre de usuario, los demás por su tercero. */
private fun Transaction.nombresDeMiembros(uid: String): Map<String, String> {
    val dueno = Users.selectAll().where { Users.id eq uid }.firstOrNull()?.get(Users.name)
        ?.trim()?.split(" ")?.firstOrNull()?.ifBlank { null } ?: "Tú"
    val terceros = KnownDestinations.selectAll().where { KnownDestinations.userId eq uid }
        .associate { it[KnownDestinations.id] to it[KnownDestinations.nombre] }
    return terceros + (MIEMBRO_YO to dueno)
}

/** Los espacios de [uid] tal como se guardan, sin resolver nombres (para repartir). */
internal fun Transaction.espaciosSinNombres(uid: String): List<Espacio> {
    if (!hayEspacios()) return emptyList()
    return Espacios.selectAll()
        .where { Espacios.userId eq uid }
        .orderBy(Espacios.createdAt)
        .map { fila ->
            val reparto = runCatching { RepartoDelEspacio.valueOf(fila[Espacios.reparto]) }
                .getOrDefault(RepartoDelEspacio.PARTES_IGUALES)
            val guardados = miembrosDeLaFila(fila[Espacios.miembros])
            val yo = guardados.firstOrNull { it.id == MIEMBRO_YO } ?: MiembroDelEspacio(MIEMBRO_YO)
            Espacio(
                id = fila[Espacios.id],
                nombre = fila[Espacios.nombre],
                miembros = listOf(yo) + guardados.filter { it.id != MIEMBRO_YO },
                reparto = reparto,
                creadoEn = fila[Espacios.createdAt],
            )
        }
}

/** Los espacios de [uid], con el dueño primero y los nombres resueltos. */
internal fun Transaction.espaciosDe(uid: String): List<Espacio> {
    val espacios = espaciosSinNombres(uid)
    if (espacios.isEmpty()) return espacios
    val nombres = nombresDeMiembros(uid)
    return espacios.map { e -> e.copy(miembros = e.miembros.map { it.copy(nombre = nombres[it.id] ?: "Alguien") }) }
}

/**
 * Los gastos de [uid] (todos los espacios o uno). Los de un movimiento propio toman el monto, la
 * categoría, la descripción y la fecha **del movimiento, en cada lectura**: si el dueño le corrigió
 * el monto, las partes se recalculan con [partesDelGasto] y el reparto del gasto (vuelta 97: sus
 * porcentajes, sus montos en proporción o su «Solo de …»; sin reparto propio, la regla del grupo), y
 * la parte ajena nunca queda vieja.
 */
internal fun Transaction.gastosDe(uid: String, espacios: List<Espacio>, espacioId: String? = null): List<GastoDelEspacio> {
    if (!hayEspacios()) return emptyList()
    val filas = GastosDelEspacio.selectAll()
        .where {
            val base = GastosDelEspacio.userId eq uid
            if (espacioId == null) base else base and (GastosDelEspacio.espacioId eq espacioId)
        }
        .toList()
    val ids = filas.mapNotNull { it[GastosDelEspacio.eventoId] }
    val eventos = if (ids.isEmpty()) emptyMap() else Events.selectAll()
        .where { (Events.userId eq uid) and (Events.id inList ids) }
        .associate { it[Events.id] to it.toFinancialEvent() }
    val porEspacio = espacios.associateBy { it.id }
    return filas.map { fila ->
        val guardado = GastoDelEspacio(
            id = fila[GastosDelEspacio.id],
            espacioId = fila[GastosDelEspacio.espacioId],
            pagadoPor = fila[GastosDelEspacio.pagadoPor],
            monto = fila[GastosDelEspacio.monto],
            categoria = fila[GastosDelEspacio.categoria],
            descripcion = fila[GastosDelEspacio.descripcion],
            fecha = fila[GastosDelEspacio.fecha],
            eventoId = fila[GastosDelEspacio.eventoId],
            partes = partesDeLaFila(fila[GastosDelEspacio.partes]),
            reparto = repartoDeLaFila(fila[GastosDelEspacio.reparto]),
        )
        val evento = guardado.eventoId?.let { eventos[it] } ?: return@map guardado
        val espacio = porEspacio[guardado.espacioId]
        guardado.copy(
            monto = evento.amount,
            categoria = evento.category,
            descripcion = evento.description,
            fecha = evento.timestamp,
            partes = if (evento.amount != guardado.monto && espacio != null) {
                partesDelGasto(evento.amount, espacio, guardado.pagadoPor, guardado.reparto)
            } else {
                guardado.partes
            },
        )
    }.sortedByDescending { it.fecha }
}

/** Las liquidaciones de [uid] (todos los espacios o uno), la más reciente primero. */
internal fun Transaction.liquidacionesDe(uid: String, espacioId: String? = null): List<LiquidacionDelEspacio> {
    if (!hayEspacios()) return emptyList()
    return LiquidacionesDelEspacio.selectAll()
        .where {
            val base = LiquidacionesDelEspacio.userId eq uid
            if (espacioId == null) base else base and (LiquidacionesDelEspacio.espacioId eq espacioId)
        }
        .map {
            LiquidacionDelEspacio(
                id = it[LiquidacionesDelEspacio.id],
                espacioId = it[LiquidacionesDelEspacio.espacioId],
                de = it[LiquidacionesDelEspacio.de],
                a = it[LiquidacionesDelEspacio.a],
                monto = it[LiquidacionesDelEspacio.monto],
                fecha = it[LiquidacionesDelEspacio.fecha],
                eventoId = it[LiquidacionesDelEspacio.eventoId],
            )
        }
        .sortedByDescending { it.fecha }
}

/**
 * Lo que los espacios de [uid] dicen de sus totales: el índice para [conLoCompartido] (la parte
 * ajena de cada movimiento repartido y los que saldan) y su parte de lo que pagaron otros, como
 * movimientos de gasto que solo reciben los totales.
 */
internal class CompartidoDelUsuario(
    val indice: LoCompartido,
    val tuParteDeOtros: List<FinancialEvent>,
) {
    companion object {
        val NADA = CompartidoDelUsuario(LoCompartido.NADA, emptyList())
    }
}

/**
 * [CompartidoDelUsuario] de [uid]. Tres tablas chicas y, si hay movimientos repartidos, esos
 * movimientos por id: la parte ajena sale de [gastosDe], que la recalcula si el monto cambió.
 */
internal fun Transaction.loCompartidoDelUsuario(uid: String): CompartidoDelUsuario {
    if (!hayEspacios()) return CompartidoDelUsuario.NADA
    val espacios = espaciosSinNombres(uid)
    if (espacios.isEmpty()) return CompartidoDelUsuario.NADA
    val gastos = gastosDe(uid, espacios)
    val liquidaciones = liquidacionesDe(uid)
    if (gastos.isEmpty() && liquidaciones.isEmpty()) return CompartidoDelUsuario.NADA
    return CompartidoDelUsuario(loCompartidoDe(gastos, liquidaciones), tuParteComoEventos(gastos))
}

/**
 * Vuelta 97 · **los recurrentes del hogar de [uid]**: id de la regla → id del grupo en que se reparte.
 * Vacío si la tabla no existe (las bases de prueba de `:server` que no la crean).
 */
internal fun Transaction.recurrentesDelHogarDe(uid: String): Map<String, String> {
    if (!runCatching { RecurrentesDelHogar.exists() }.getOrDefault(false)) return emptyMap()
    return RecurrentesDelHogar.selectAll().where { RecurrentesDelHogar.userId eq uid }
        .associate { it[RecurrentesDelHogar.reglaId] to it[RecurrentesDelHogar.espacioId] }
}
