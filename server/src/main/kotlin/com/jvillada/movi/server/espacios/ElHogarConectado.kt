package com.jvillada.movi.server.espacios

import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CategoryPrefs
import com.jvillada.movi.server.db.EspaciosConectados
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.Espacios
import com.jvillada.movi.server.db.GastosDelEspacio
import com.jvillada.movi.server.db.LiquidacionesDelEspacio
import com.jvillada.movi.shared.model.RepartoDelEspacio
import com.jvillada.movi.server.db.InvitacionesAEspacio
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
import com.jvillada.movi.server.db.VinculosDeLiquidacion
import com.jvillada.movi.server.compartir.sinNumerosCompletos
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.netoDe
import com.jvillada.movi.shared.model.saldoDelEspacio
import org.jetbrains.exposed.sql.Database
import com.jvillada.movi.server.routes.SIN_CATEGORIA
import com.jvillada.movi.shared.model.ConexionDelEspacio
import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.LadoDelOtro
import com.jvillada.movi.shared.model.LiquidacionDelEspacio
import com.jvillada.movi.shared.model.PREDEFINED_CATEGORIES
import com.jvillada.movi.shared.model.claveComparableDeNombre
import com.jvillada.movi.shared.model.gastoDelOtroLado
import com.jvillada.movi.shared.model.liquidacionDelOtroLado
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * # El hogar conectado, del lado del server (vuelta 131)
 *
 * Una sola entrada para leer un grupo: [gastosDe] y [liquidacionesDe] devuelven lo que anotó el dueño
 * **más** lo que anotó la otra persona de un grupo conectado, ya traducido a su lado por las funciones
 * puras de `:core` ([gastoDelOtroLado], [liquidacionDelOtroLado]). Todo lo que lee un grupo —el detalle,
 * el saldo, `loCompartidoDelUsuario` (o sea tu parte de lo que pagó Caro el día del gasto), la bandeja,
 * Movi AI— pasa por ahí. Lo que escribe o propone sobre lo propio usa [gastosPropiosDe] /
 * [liquidacionesPropiasDe].
 *
 * Lo del otro lado se resuelve **en su lado, con sus reglas** (el monto de su movimiento, el recálculo de
 * las partes, sin sus anulados) y después se traduce: nunca cruza un id de movimiento, ni una cuenta, ni
 * nada que no esté repartido en ese grupo.
 */

/** Una conexión vigente vista desde un usuario: con quién, su grupo, y cómo traducir lo suyo. */
internal data class LadoConectado(
    val conexionId: String,
    val otroUid: String,
    val suEspacioId: String,
    val lado: LadoDelOtro,
)

/**
 * **¿Existen estas tablas?** En producción siempre: las crea el arranque. La pregunta es para las bases de
 * prueba de `:server` que arman solo las tablas de su ruta. Revisión de la vuelta 131: se responde **una vez
 * por base** (lo que existe no deja de existir mientras la base vive), y no en cada lectura de movimientos,
 * que es la ruta caliente. Solo se recuerda el «sí»: una base de prueba que crea la tabla después sigue
 * preguntando.
 */
internal fun Transaction.existenLasTablas(vararg tablas: Table): Boolean {
    val base = db
    val clave = tablas.joinToString(",") { it.tableName }
    val sabidas = synchronized(TABLAS_QUE_EXISTEN) { TABLAS_QUE_EXISTEN.getOrPut(base) { HashSet() } }
    if (synchronized(sabidas) { clave in sabidas }) return true
    val existen = runCatching { tablas.all { it.exists() } }.getOrDefault(false)
    if (existen) synchronized(sabidas) { sabidas += clave }
    return existen
}

private val TABLAS_QUE_EXISTEN = java.util.WeakHashMap<Database, MutableSet<String>>()

/** ¿Existe la tabla de las conexiones? Las bases de prueba de `:server` que no la crean no tienen ninguna. */
internal fun Transaction.hayConexiones(): Boolean = existenLasTablas(EspaciosConectados)

/** Las conexiones vigentes (sin [EspaciosConectados.desconectadoEn]) de [uid], como A o como B. */
internal fun Transaction.ladosConectadosDe(uid: String): List<LadoConectado> {
    if (!hayConexiones()) return emptyList()
    return EspaciosConectados.selectAll()
        .where { ((EspaciosConectados.userA eq uid) or (EspaciosConectados.userB eq uid)) and EspaciosConectados.desconectadoEn.isNull() }
        .map { f ->
            val soyA = f[EspaciosConectados.userA] == uid
            LadoConectado(
                conexionId = f[EspaciosConectados.id],
                otroUid = if (soyA) f[EspaciosConectados.userB] else f[EspaciosConectados.userA],
                suEspacioId = if (soyA) f[EspaciosConectados.espacioB] else f[EspaciosConectados.espacioA],
                lado = LadoDelOtro(
                    miEspacioId = if (soyA) f[EspaciosConectados.espacioA] else f[EspaciosConectados.espacioB],
                    miMiembroDelOtro = if (soyA) f[EspaciosConectados.miembroA] else f[EspaciosConectados.miembroB],
                    suMiembroDeMi = if (soyA) f[EspaciosConectados.miembroB] else f[EspaciosConectados.miembroA],
                    desde = f[EspaciosConectados.desde],
                ),
            )
        }
}

/** La conexión vigente del grupo [espacioId] de [uid], o `null`. */
internal fun Transaction.conexionDelGrupo(uid: String, espacioId: String): LadoConectado? =
    ladosConectadosDe(uid).firstOrNull { it.lado.miEspacioId == espacioId }

/** Los movimientos anulados de [uid]. */
internal fun Transaction.anuladosDelUsuario(uid: String): Set<String> =
    VoidEvents.selectAll().where { VoidEvents.userId eq uid }.map { it[VoidEvents.originalEventId] }.toSet()

/**
 * **Los gastos de un grupo, como se leen**: los que anotó [uid] ([gastosPropiosDe], con sus anulados como
 * siempre: los filtra quien llama) y los que anotó la otra persona de cada grupo conectado, resueltos en
 * su lado sin sus anulados y traducidos al de [uid]. El más reciente primero.
 */
internal fun Transaction.gastosDe(uid: String, espacios: List<Espacio>, espacioId: String? = null): List<GastoDelEspacio> {
    val propios = gastosPropiosDe(uid, espacios, espacioId)
    val lados = ladosConectadosDe(uid).filter { c -> espacioId == null || c.lado.miEspacioId == espacioId }
        .filter { c -> espacios.any { it.id == c.lado.miEspacioId } }
    if (lados.isEmpty()) return propios
    var categoriaMia: ((String) -> String)? = null
    val delOtro = lados.flatMap { c ->
        val suyos = espaciosSinNombres(c.otroUid)
        val anulados = anuladosDelUsuario(c.otroUid)
        val gastos = gastosPropiosConSuEscrituraDe(c.otroUid, suyos, c.suEspacioId)
            .filterNot { (g, _) -> g.eventoId != null && g.eventoId in anulados }
        if (gastos.isEmpty()) return@flatMap emptyList()
        val resolver = categoriaMia ?: resolverDeCategoriasDe(uid).also { categoriaMia = it }
        // Revisión: la descripción que cruza no lleva un número de cuenta entero (la misma regla de la
        // página que se comparte con un tercero).
        gastos.mapNotNull { (g, escrito) -> gastoDelOtroLado(g, c.lado, escrito, resolver)?.let { it.copy(descripcion = sinNumerosCompletos(it.descripcion)) } }
    }
    return (propios + delOtro).sortedByDescending { it.fecha }
}

/**
 * Revisión de la vuelta 131 · **lo que el dueño anotó antes de conectarse**, en su neto (positivo = le
 * deben): sus gastos y saldados de [espacio] escritos antes de `desde`, sin anulados. Siguen en su saldo y la
 * otra persona no los ve; la tarjeta del saldo lo dice. 0 sin conexión.
 */
internal fun Transaction.deAntesDeConectarseEn(uid: String, espacio: Espacio, anulados: Set<String>): Long {
    val c = conexionDelGrupo(uid, espacio.id) ?: return 0L
    val gastos = gastosPropiosConSuEscrituraDe(uid, listOf(espacio), espacio.id)
        .filter { (g, escrito) -> escrito < c.lado.desde && !(g.eventoId != null && g.eventoId in anulados) }.map { it.first }
    val liquidaciones = liquidacionesPropiasConSuEscrituraDe(uid, espacio.id)
        .filter { (l, escrito) -> escrito < c.lado.desde && !(l.eventoId != null && l.eventoId in anulados) }.map { it.first }
    if (gastos.isEmpty() && liquidaciones.isEmpty()) return 0L
    return saldoDelEspacio(espacio, gastos, liquidaciones, anulados).netoDe(MIEMBRO_YO)
}

/** **Las liquidaciones de un grupo, como se leen**: las propias y las del otro lado de un grupo conectado. */
internal fun Transaction.liquidacionesDe(uid: String, espacioId: String? = null): List<LiquidacionDelEspacio> {
    val propias = liquidacionesPropiasDe(uid, espacioId)
    val lados = ladosConectadosDe(uid).filter { c -> espacioId == null || c.lado.miEspacioId == espacioId }
    if (lados.isEmpty()) return propias
    // Revisión: el vínculo **propio** de un saldado cruzado (`vinculos_de_liquidacion`) es su movimiento de
    // este lado; uno anulado no vincula (el saldado de la otra persona sigue contando, sin vínculo).
    val vinculos = vinculosDe(uid)
    val misAnulados = if (vinculos.isEmpty()) emptySet() else anuladosDelUsuario(uid)
    val delOtro = lados.flatMap { c ->
        val anulados = anuladosDelUsuario(c.otroUid)
        liquidacionesPropiasConSuEscrituraDe(c.otroUid, c.suEspacioId)
            .filterNot { (l, _) -> l.eventoId != null && l.eventoId in anulados }
            .mapNotNull { (l, escrito) -> liquidacionDelOtroLado(l, c.lado, escrito) }
            .map { l -> vinculos[l.id]?.takeIf { it !in misAnulados }?.let { l.copy(eventoId = it) } ?: l }
    }
    return (propias + delOtro).sortedByDescending { it.fecha }
}

/** Los vínculos de [uid]: id del saldado cruzado → su movimiento. Vacío si la tabla no existe. */
internal fun Transaction.vinculosDe(uid: String): Map<String, String> {
    if (!existenLasTablas(VinculosDeLiquidacion)) return emptyMap()
    return VinculosDeLiquidacion.selectAll().where { VinculosDeLiquidacion.userId eq uid }
        .associate { it[VinculosDeLiquidacion.liquidacionId] to it[VinculosDeLiquidacion.eventoId] }
}

/** ¿[eventoId] ya está vinculado a un saldado cruzado de [uid]? */
internal fun Transaction.estaVinculado(uid: String, eventoId: String): Boolean =
    existenLasTablas(VinculosDeLiquidacion) &&
        VinculosDeLiquidacion.selectAll().where { (VinculosDeLiquidacion.userId eq uid) and (VinculosDeLiquidacion.eventoId eq eventoId) }.any()

/**
 * **La categoría con que cuenta mi parte** de un gasto que anotó la otra persona: la mía con el mismo
 * nombre normalizado ([claveComparableDeNombre]) o, si no tengo una igual, la de lo que no tiene categoría
 * ([SIN_CATEGORIA], «Otros», la misma que Movi propone cuando no sabe). Mis categorías son las que Movi ya
 * reconoce como mías (`categoryExists`): las del catálogo, las de mis movimientos, presupuestos,
 * recurrentes y preferencias.
 */
internal fun Transaction.resolverDeCategoriasDe(uid: String): (String) -> String {
    val mias = LinkedHashMap<String, String>()
    fun sumar(nombre: String) {
        val clave = claveComparableDeNombre(nombre)
        if (clave.isNotEmpty()) mias.putIfAbsent(clave, nombre)
    }
    // Lo del dueño gana sobre el catálogo: si escribió «mercado» en minúscula, es esa.
    fun <T : Table> siExiste(tabla: T, leer: T.() -> Unit) { if (existenLasTablas(tabla)) tabla.leer() }
    siExiste(Events) { select(category).where { userId eq uid }.withDistinct().forEach { sumar(it[category]) } }
    siExiste(Budgets) { select(category).where { userId eq uid }.forEach { sumar(it[category]) } }
    siExiste(RecurringRules) { select(category).where { userId eq uid }.withDistinct().forEach { sumar(it[category]) } }
    siExiste(CategoryPrefs) { select(name).where { userId eq uid }.forEach { sumar(it[name]) } }
    PREDEFINED_CATEGORIES.forEach { sumar(it.name) }
    return { categoria -> mias[claveComparableDeNombre(categoria)] ?: SIN_CATEGORIA }
}

/** El nombre del perfil de [uid] como lo dice Movi: su primer nombre («Juan»), o «Alguien». */
internal fun Transaction.nombreDelPerfil(uid: String): String =
    Users.selectAll().where { Users.id eq uid }.firstOrNull()?.get(Users.name)
        ?.trim()?.split(" ")?.firstOrNull()?.ifBlank { null } ?: "Alguien"

/** [espacios] con su [Espacio.conexion] vigente, el nombre que el dueño le da a la otra persona incluido. */
internal fun Transaction.conSusConexiones(uid: String, espacios: List<Espacio>): List<Espacio> {
    if (espacios.isEmpty()) return espacios
    val lados = ladosConectadosDe(uid).associateBy { it.lado.miEspacioId }
    if (lados.isEmpty()) return espacios
    return espacios.map { e ->
        val c = lados[e.id] ?: return@map e
        val nombre = e.miembros.firstOrNull { it.id == c.lado.miMiembroDelOtro }?.nombre.orEmpty()
        e.copy(
            conexion = ConexionDelEspacio(
                miembroId = c.lado.miMiembroDelOtro, nombre = nombre, desde = c.lado.desde,
                // Revisión: quién aceptó (o invitó), con el nombre de su perfil de Movi.
                nombreEnMovi = nombreDelPerfil(c.otroUid),
            ),
        )
    }
}

/**
 * **Desconecta el grupo [espacioId] de [uid]** (vuelta 131): la conexión vigente queda con
 * `desconectado_en` (no se borra nada de nadie: cada uno sigue con lo que anotó) y la invitación
 * pendiente del grupo se cancela. Devuelve si había una conexión que desconectar. Lo llaman «Desconectar»
 * (cualquiera de los dos) y borrar el grupo.
 */
internal fun Transaction.desconectarElGrupo(uid: String, espacioId: String, ahora: Long = System.currentTimeMillis()): Boolean {
    cancelarLaInvitacionPendiente(uid, espacioId, ahora)
    val conexion = conexionDelGrupo(uid, espacioId) ?: return false
    EspaciosConectados.update({ (EspaciosConectados.id eq conexion.conexionId) and EspaciosConectados.desconectadoEn.isNull() }) {
        it[desconectadoEn] = ahora
    }
    return true
}

/** Cancela la invitación pendiente del grupo [espacioId] de [uid], si la hay. */
internal fun Transaction.cancelarLaInvitacionPendiente(uid: String, espacioId: String, ahora: Long = System.currentTimeMillis()): Int {
    if (!existenLasTablas(InvitacionesAEspacio)) return 0
    return InvitacionesAEspacio.update({
        (InvitacionesAEspacio.userId eq uid) and (InvitacionesAEspacio.espacioId eq espacioId) and
            InvitacionesAEspacio.usadaEn.isNull() and InvitacionesAEspacio.canceladaEn.isNull()
    }) { it[canceladaEn] = ahora }
}

/** ¿El tercero [destinoId] de [uid] es la otra persona de un grupo conectado vigente? */
internal fun Transaction.esUnTerceroConectado(uid: String, destinoId: String): Boolean =
    ladosConectadosDe(uid).any { it.lado.miMiembroDelOtro == destinoId }

/**
 * Revisión de la vuelta 131 · **unir dos terceros también los une en los grupos** (`/api/destinos/{id}/unir`):
 * donde [uid] tenía a [seVa] —los miembros de un grupo, las partes y el reparto propio de un gasto, quién
 * pagó, el `de`/`a` de un saldado, la invitación pendiente y su lado de una conexión— pasa a estar
 * [queda]. Si los dos eran del mismo grupo, quedan como uno (sus partes y porcentajes se suman). Sin esto
 * el grupo apuntaba a un tercero que ya no existe y lo de la otra persona se leía como «Alguien».
 */
internal fun Transaction.cambiarElMiembroEnLosGrupos(uid: String, seVa: String, queda: String) {
    if (!hayEspacios()) return
    fun <V> juntar(mapa: Map<String, V>, sumar: (V, V) -> V): Map<String, V> {
        val r = LinkedHashMap<String, V>()
        mapa.forEach { (k, v) -> val clave = if (k == seVa) queda else k; r[clave] = r[clave]?.let { sumar(it, v) } ?: v }
        return r
    }
    Espacios.selectAll().where { Espacios.userId eq uid }.forEach { fila ->
        val miembros = miembrosDeLaFila(fila[Espacios.miembros])
        if (miembros.none { it.id == seVa }) return@forEach
        val reparto = runCatching { RepartoDelEspacio.valueOf(fila[Espacios.reparto]) }.getOrDefault(RepartoDelEspacio.PARTES_IGUALES)
        val unidos = miembros.groupBy { if (it.id == seVa) queda else it.id }.map { (id, iguales) ->
            iguales.first().copy(id = id, porcentaje = iguales.mapNotNull { it.porcentaje }.takeIf { it.isNotEmpty() }?.sum())
        }
        Espacios.update({ (Espacios.id eq fila[Espacios.id]) and (Espacios.userId eq uid) }) {
            it[Espacios.miembros] = miembrosComoJson(unidos, reparto)
        }
    }
    GastosDelEspacio.selectAll().where { GastosDelEspacio.userId eq uid }.forEach { fila ->
        val partes = partesDeLaFila(fila[GastosDelEspacio.partes])
        val reparto = repartoDeLaFila(fila[GastosDelEspacio.reparto])
        val tocaReparto = reparto != null && (seVa in reparto.porcentajes || seVa in reparto.montos || reparto.soloDe == seVa)
        if (fila[GastosDelEspacio.pagadoPor] != seVa && seVa !in partes && !tocaReparto) return@forEach
        GastosDelEspacio.update({ (GastosDelEspacio.id eq fila[GastosDelEspacio.id]) and (GastosDelEspacio.userId eq uid) }) {
            if (fila[GastosDelEspacio.pagadoPor] == seVa) it[pagadoPor] = queda
            it[GastosDelEspacio.partes] = partesComoJson(juntar(partes) { a, b -> a + b })
            if (reparto != null) {
                it[GastosDelEspacio.reparto] = repartoComoJson(
                    reparto.copy(
                        porcentajes = juntar(reparto.porcentajes) { a, b -> a + b },
                        montos = juntar(reparto.montos) { a, b -> a + b },
                        soloDe = reparto.soloDe?.let { s -> if (s == seVa) queda else s },
                    ),
                )
            }
        }
    }
    LiquidacionesDelEspacio.update({ (LiquidacionesDelEspacio.userId eq uid) and (LiquidacionesDelEspacio.de eq seVa) }) { it[de] = queda }
    LiquidacionesDelEspacio.update({ (LiquidacionesDelEspacio.userId eq uid) and (LiquidacionesDelEspacio.a eq seVa) }) { it[a] = queda }
    if (existenLasTablas(InvitacionesAEspacio)) {
        InvitacionesAEspacio.update({ (InvitacionesAEspacio.userId eq uid) and (InvitacionesAEspacio.miembroId eq seVa) }) { it[miembroId] = queda }
    }
    if (hayConexiones()) {
        EspaciosConectados.update({ (EspaciosConectados.userA eq uid) and (EspaciosConectados.miembroA eq seVa) }) { it[miembroA] = queda }
        EspaciosConectados.update({ (EspaciosConectados.userB eq uid) and (EspaciosConectados.miembroB eq seVa) }) { it[miembroB] = queda }
    }
}
