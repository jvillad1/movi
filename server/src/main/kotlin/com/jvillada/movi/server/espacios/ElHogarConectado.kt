package com.jvillada.movi.server.espacios

import com.jvillada.movi.server.db.Budgets
import com.jvillada.movi.server.db.CategoryPrefs
import com.jvillada.movi.server.db.EspaciosConectados
import com.jvillada.movi.server.db.Events
import com.jvillada.movi.server.db.InvitacionesAEspacio
import com.jvillada.movi.server.db.RecurringRules
import com.jvillada.movi.server.db.Users
import com.jvillada.movi.server.db.VoidEvents
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

/** ¿Existe la tabla de las conexiones? Las bases de prueba de `:server` que no la crean no tienen ninguna. */
internal fun Transaction.hayConexiones(): Boolean =
    runCatching { EspaciosConectados.exists() }.getOrDefault(false)

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
        val gastos = gastosPropiosDe(c.otroUid, suyos, c.suEspacioId).filterNot { it.eventoId != null && it.eventoId in anulados }
        if (gastos.isEmpty()) return@flatMap emptyList()
        val resolver = categoriaMia ?: resolverDeCategoriasDe(uid).also { categoriaMia = it }
        gastos.mapNotNull { gastoDelOtroLado(it, c.lado, resolver) }
    }
    return (propios + delOtro).sortedByDescending { it.fecha }
}

/** **Las liquidaciones de un grupo, como se leen**: las propias y las del otro lado de un grupo conectado. */
internal fun Transaction.liquidacionesDe(uid: String, espacioId: String? = null): List<LiquidacionDelEspacio> {
    val propias = liquidacionesPropiasDe(uid, espacioId)
    val lados = ladosConectadosDe(uid).filter { c -> espacioId == null || c.lado.miEspacioId == espacioId }
    if (lados.isEmpty()) return propias
    val delOtro = lados.flatMap { c ->
        val anulados = anuladosDelUsuario(c.otroUid)
        liquidacionesPropiasDe(c.otroUid, c.suEspacioId)
            .filterNot { it.eventoId != null && it.eventoId in anulados }
            .mapNotNull { liquidacionDelOtroLado(it, c.lado) }
    }
    return (propias + delOtro).sortedByDescending { it.fecha }
}

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
    fun <T : Table> siExiste(tabla: T, leer: T.() -> Unit) { if (runCatching { tabla.exists() }.getOrDefault(false)) tabla.leer() }
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
        e.copy(conexion = ConexionDelEspacio(miembroId = c.lado.miMiembroDelOtro, nombre = nombre, desde = c.lado.desde))
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
    if (!runCatching { InvitacionesAEspacio.exists() }.getOrDefault(false)) return 0
    return InvitacionesAEspacio.update({
        (InvitacionesAEspacio.userId eq uid) and (InvitacionesAEspacio.espacioId eq espacioId) and
            InvitacionesAEspacio.usadaEn.isNull() and InvitacionesAEspacio.canceladaEn.isNull()
    }) { it[canceladaEn] = ahora }
}
