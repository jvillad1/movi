package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * # El hogar conectado (vuelta 131)
 *
 * Juan y Caro usan Movi, cada uno con su cuenta. Un grupo de dos («Casa con Caro») se **conecta** con
 * la cuenta de la otra persona y los dos ven **solo lo repartido en ese grupo** y lo saldado: nada de
 * sus cuentas, saldos, otros movimientos ni categorías.
 *
 * - **Cada uno es dueño de sus filas.** Nada se copia: lo que anotó Caro vive con el usuario de Caro y
 *   se **une al leer** con [gastoDelOtroLado] / [liquidacionDelOtroLado], derivado como `conLoCompartido`.
 *   Cada uno edita o borra solo lo suyo; desconectar no borra nada de nadie.
 * - **Quién es quién**: en el lado de Caro, ella es [MIEMBRO_YO] y Juan es un tercero suyo. Al cruzar al
 *   lado de Juan, el «yo» de Caro pasa a ser el tercero de Juan que es Caro, y el tercero de Caro que es
 *   Juan pasa a ser [MIEMBRO_YO]. Lo mismo en las partes, en `de`/`a` y en el reparto propio.
 * - **Lo que no cruza**: el id de un movimiento de la otra cuenta (`eventoId` llega nulo, también el de
 *   una liquidación) ni el concepto del movimiento vinculado.
 * - **Solo lo de la conexión vigente**: lo del otro lado cuenta si **se escribió** desde
 *   [LadoDelOtro.desde] (revisión de la vuelta 131: por cuándo se anotó la fila, no por su `fecha`; el
 *   mercado de ayer repartido hoy sí cruza); desconectado, nada.
 *
 * Con eso el saldo es **espejo**: lo que Juan dice que Caro le debe es lo que Caro dice que le debe a
 * Juan, al peso (las partes, con el redondeo de [partesDelGasto], las guarda quien anotó el gasto y cruzan
 * tal cual). Lo que cada uno anotó antes de `desde` sigue siendo solo suyo
 * ([DetalleDelEspacio.deAntesDeConectarse] lo dice).
 */

/**
 * La conexión de un grupo, vista desde el lado de quien lee: [miembroId] es su tercero que es la otra
 * persona, [nombre] cómo lo llama, [desde] el momento en que se conectaron (epoch ms).
 */
@Serializable
data class ConexionDelEspacio(
    val miembroId: String,
    val nombre: String = "",
    val desde: Long = 0L,
    /** Revisión de la vuelta 131 · el nombre del perfil de Movi de la otra persona («Carolina»). */
    val nombreEnMovi: String = "",
)

/**
 * Una conexión vista desde un lado: lo que hace falta para traducir lo del otro lado al mío.
 *
 * @param miEspacioId mi grupo.
 * @param miMiembroDelOtro mi tercero que es la otra persona.
 * @param suMiembroDeMi el tercero de la otra persona que soy yo.
 * @param desde desde cuándo cuenta lo del otro lado.
 */
data class LadoDelOtro(
    val miEspacioId: String,
    val miMiembroDelOtro: String,
    val suMiembroDeMi: String,
    val desde: Long,
)

/** El id de un miembro del otro lado, en el mío; `null` si no es ninguno de los dos (no cruza). */
fun miembroDelOtroLado(id: String, lado: LadoDelOtro): String? = when (id) {
    MIEMBRO_YO -> lado.miMiembroDelOtro
    lado.suMiembroDeMi -> MIEMBRO_YO
    else -> null
}

private fun <V> clavesDelOtroLado(mapa: Map<String, V>, lado: LadoDelOtro): Map<String, V>? {
    val traducido = LinkedHashMap<String, V>()
    for ((k, v) in mapa) traducido[miembroDelOtroLado(k, lado) ?: return null] = v
    return traducido
}

private fun repartoDelOtroLado(r: RepartoDelGasto?, lado: LadoDelOtro): RepartoDelGasto? {
    if (r == null) return null
    val porcentajes = clavesDelOtroLado(r.porcentajes, lado) ?: return null
    val montos = clavesDelOtroLado(r.montos, lado) ?: return null
    val soloDe = r.soloDe?.let { miembroDelOtroLado(it, lado) ?: return null }
    return r.copy(porcentajes = porcentajes, montos = montos, soloDe = soloDe)
}

/**
 * **Un gasto que anotó la otra persona, en mi lado** (la unión al leer). [g] viene ya resuelto en su
 * lado, con sus reglas de siempre (monto, descripción, fecha y categoría del movimiento, recálculo de
 * las partes, sin sus anulados). Devuelve `null` si no cuenta: se escribió ([escritoEn], el `created_at`
 * de su fila) antes de [LadoDelOtro.desde], o nombra a alguien que no es ninguno de los dos.
 *
 * @param categoriaMia la categoría con que cuenta mi parte en mis totales (la mía con el mismo nombre
 *   normalizado o, sin ella, la de lo que no tiene categoría). La resuelve el server.
 */
fun gastoDelOtroLado(
    g: GastoDelEspacio,
    lado: LadoDelOtro,
    escritoEn: Long,
    categoriaMia: (String) -> String = { it },
): GastoDelEspacio? {
    if (escritoEn < lado.desde) return null
    val pagadoPor = miembroDelOtroLado(g.pagadoPor, lado) ?: return null
    val partes = clavesDelOtroLado(g.partes, lado) ?: return null
    return g.copy(
        espacioId = lado.miEspacioId,
        pagadoPor = pagadoPor,
        partes = partes,
        eventoId = null,
        categoria = categoriaMia(g.categoria),
        reparto = repartoDelOtroLado(g.reparto, lado),
        anotadoPor = lado.miMiembroDelOtro,
    )
}

/** **Un saldado que anotó la otra persona, en mi lado**: como [gastoDelOtroLado], sin su movimiento. */
fun liquidacionDelOtroLado(l: LiquidacionDelEspacio, lado: LadoDelOtro, escritoEn: Long): LiquidacionDelEspacio? {
    if (escritoEn < lado.desde) return null
    val de = miembroDelOtroLado(l.de, lado) ?: return null
    val a = miembroDelOtroLado(l.a, lado) ?: return null
    return l.copy(espacioId = lado.miEspacioId, de = de, a = a, eventoId = null, movimiento = null, anotadoPor = lado.miMiembroDelOtro)
}

// ─────────────────────────────────────────────────────────────────────────────
// La invitación por código
// ─────────────────────────────────────────────────────────────────────────────

/** Las letras del código: sin 0/O ni 1/I/L, que se confunden al dictarlo o al leerlo. */
const val ALFABETO_DEL_CODIGO: String = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

/** Cuántos caracteres tiene un código de invitación. */
const val LARGO_DEL_CODIGO: Int = 6

/** Cuántos días vale un código. */
const val DIAS_QUE_VALE_LA_INVITACION: Int = 7

/** Cuántos códigos fallidos se aceptan por persona en una hora (después, 429). */
const val INTENTOS_DE_CODIGO_POR_HORA: Int = 10

/**
 * El código como se escribió, limpio: en mayúsculas y sin espacios, guiones ni puntos («k7p-3q9» →
 * «K7P3Q9»). No corrige letras parecidas: un código con una «O» simplemente no existe.
 */
fun codigoLimpio(escrito: String): String =
    escrito.uppercase().filter { it.isLetterOrDigit() }

/** Lo que un código de Movi nunca trae (se confunde al leerlo o dictarlo). */
const val CARACTERES_QUE_NO_TRAE_EL_CODIGO: String = "0O1IL"

/** ¿[codigo] (ya limpio) trae alguno de [CARACTERES_QUE_NO_TRAE_EL_CODIGO]? */
fun traeCaracteresQueNoExisten(codigo: String): Boolean = codigo.any { it in CARACTERES_QUE_NO_TRAE_EL_CODIGO }

/** ¿[codigo] (ya limpio) tiene la forma de un código de Movi? */
fun pareceUnCodigo(codigo: String): Boolean =
    codigo.length == LARGO_DEL_CODIGO && codigo.all { it in ALFABETO_DEL_CODIGO }

/** `POST /api/espacios/{id}/invitacion`: el código en claro (solo aquí) y hasta cuándo vale. */
@Serializable
data class InvitacionCreada(val codigo: String, val venceEn: Long)

/** `GET /api/espacios/{id}/invitacion`: la pendiente, sin el código. */
@Serializable
data class InvitacionPendiente(val venceEn: Long)

/** El cuerpo de `POST /api/espacios/invitaciones/ver` y `…/aceptar`. */
@Serializable
data class CodigoDeInvitacion(val codigo: String)

/** `POST /api/espacios/invitaciones/ver`: a qué grupo invita y quién. */
@Serializable
data class InvitacionVista(val nombreDelGrupo: String, val quienInvita: String)

/** ¿Se puede invitar desde [espacio]? Solo un grupo de dos (el dueño + un tercero) sin conectar. */
fun sePuedeInvitar(espacio: Espacio): Boolean =
    espacio.conexion == null && espacio.miembros.count { it.id != MIEMBRO_YO } == 1

/**
 * **Lo que deja de verse al desconectar** [detalle]: cuántos gastos y saldados anotó la otra persona, y
 * el saldo con y sin ellos (el neto del dueño: positivo = le deben). Pura, sobre lo que ya llegó.
 */
data class LoQueSeDejaDeVer(val gastos: Int, val saldados: Int, val netoAntes: Long, val netoDespues: Long)

fun loQueSeDejaDeVer(detalle: DetalleDelEspacio): LoQueSeDejaDeVer {
    val propios = detalle.gastos.filter { it.anotadoPor == null }
    val propiasLiq = detalle.liquidaciones.filter { it.anotadoPor == null }
    val despues = saldoDelEspacio(detalle.espacio, propios, propiasLiq)
    return LoQueSeDejaDeVer(
        gastos = detalle.gastos.size - propios.size,
        saldados = detalle.liquidaciones.size - propiasLiq.size,
        netoAntes = detalle.saldo.netoDe(MIEMBRO_YO),
        netoDespues = despues.netoDe(MIEMBRO_YO),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Revisión de la vuelta 131: lo saldado entre los dos lados
// ─────────────────────────────────────────────────────────────────────────────

/**
 * El motivo del 422 de «Pagó otro» con la persona conectada: cada uno anota lo que pagó él, desde su Movi
 * (si no, el mismo mercado quedaba dos veces: el que Juan anotó por Caro y el que Caro repartió).
 */
const val LO_ANOTA_DESDE_SU_MOVI: String = "LO_ANOTA_DESDE_SU_MOVI"

/** El prefijo del 409 de un saldado que la otra persona ya marcó; sigue el id de ese saldado. */
const val YA_LO_MARCO_LA_OTRA_PERSONA: String = "YA_LO_MARCO_LA_OTRA_PERSONA:"

/** Cuerpo de `POST /api/espacios/{id}/liquidaciones/{liquidacionId}/vincular`. */
@Serializable
data class VincularLiquidacion(val eventoId: String)

/**
 * **Los miembros que el dueño puede anotar como «Pagó otro» desde acá**: todos menos la persona con
 * la que el grupo está conectado (ella anota lo que paga desde su Movi).
 */
fun quienesPuedenPagarDesdeAca(espacio: Espacio): List<MiembroDelEspacio> =
    espacio.miembros.filter { it.id != MIEMBRO_YO && it.id != espacio.conexion?.miembroId }

/**
 * **El saldado que la otra persona ya marcó** para lo mismo que el dueño va a marcar: anotado por ella
 * ([LiquidacionDelEspacio.anotadoPor]), la misma dirección ([de] → [a]), el monto ± 1 % ([montoParecido]) y
 * de los últimos [DIAS_ANTES_DEL_SALDADO] días. Con uno así, marcar otro sería saldar dos veces: lo que
 * toca es vincular la transferencia a ese.
 */
fun saldadoQueYaMarcoElOtro(
    liquidaciones: List<LiquidacionDelEspacio>,
    de: String,
    a: String,
    monto: Long,
    ahora: Long,
): LiquidacionDelEspacio? = liquidaciones
    .filter { it.anotadoPor != null && it.de == de && it.a == a && montoParecido(it.monto, monto) }
    .filter { it.fecha >= ahora - DIAS_ANTES_DEL_SALDADO * 24L * 60 * 60 * 1000 }
    .maxByOrNull { it.fecha }
