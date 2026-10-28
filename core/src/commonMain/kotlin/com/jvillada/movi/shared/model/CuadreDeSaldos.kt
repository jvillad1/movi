package com.jvillada.movi.shared.model

import com.jvillada.movi.shared.time.AppTimeZone
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * # Cuadrar saldos: cuándo una cuenta lleva demasiado sin mirarse
 *
 * Las reglas nacieron en `:shared` (`ui/cuadre/CuadreDeSaldosLogic.kt`, que las sigue nombrando igual
 * y explica el porqué de cada una) y se mudaron acá sin reescribirse cuando Movi AI empezó a contestar
 * «¿Qué me recomiendas revisar primero?» sin el modelo: el chat y la fila de «Para revisar» de Hoy
 * tienen que nombrar las MISMAS cuentas, así que las eligen con la misma función.
 */

/** Un día en milisegundos. */
private const val UN_DIA_MS = 24L * 60L * 60L * 1000L

/**
 * **Cuántos días puede pasar una cuenta sin cuadrarse antes de que Movi lo mencione**: un período
 * entero más medio de gracia. Ver el KDoc homónimo en `:shared`.
 */
const val DIAS_PARA_VOLVER_A_CUADRAR = 45

/** **¿Esta cuenta se puede cuadrar contra el banco?** Solo Dinero e Inversión, y nunca un bien. */
fun sePuedeCuadrar(cuenta: Account): Boolean = cuenta.type.group != AccountGroup.DEUDA && !cuenta.esBien

/**
 * **Hace cuántos días que esta cuenta no se cuadra**: desde el último ajuste o, si nunca hubo, desde
 * su movimiento más viejo. `null` sin ningún movimiento: no hay nada que cuadrar ahí.
 */
fun diasSinCuadrar(cuenta: Account, ahora: Long): Long? {
    val desde = cuenta.lastAdjustmentAt ?: cuenta.firstEventAt ?: return null
    return ((ahora - desde) / UN_DIA_MS).coerceAtLeast(0L)
}

/** Ver [DIAS_PARA_VOLVER_A_CUADRAR]. Una cuenta que no se puede cuadrar nunca está atrasada. */
fun estaSinCuadrar(cuenta: Account, ahora: Long): Boolean =
    sePuedeCuadrar(cuenta) && (diasSinCuadrar(cuenta, ahora) ?: 0L) >= DIAS_PARA_VOLVER_A_CUADRAR

/** Las cuentas que el aviso menciona. Vacío = no se dice nada. */
fun cuentasSinCuadrar(cuentas: List<Account>, ahora: Long): List<Account> =
    cuentas.filter { estaSinCuadrar(it, ahora) }

/** Lo que se dice de una cuenta que tiene movimientos y nunca se cuadró. */
const val NUNCA_LA_HAS_CUADRADO: String = "Nunca la has cuadrado"

/** Lo que se dice de una cuenta que se cuadró alguna vez y ya pasó [DIAS_PARA_VOLVER_A_CUADRAR]. */
const val HACE_MAS_DE_UN_MES_QUE_NO_LA_CUADRAS: String = "Hace más de un mes que no la cuadras"

/** Lo que se dice de una cuenta sin un solo movimiento: no hay nada que cuadrar. */
const val SIN_MOVIMIENTOS_TODAVIA: String = "Sin movimientos todavía"

private val MESES_CORTOS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

private fun diaDe(ms: Long, zona: TimeZone): LocalDate = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zona).date

/** «3 oct»: el día del último cuadre, corto (con el año solo si no es el de [hoy]). */
private fun fechaCortaDelCuadre(dia: LocalDate, hoy: LocalDate): String =
    "${dia.dayOfMonth} ${MESES_CORTOS[dia.monthNumber - 1]}" + if (dia.year != hoy.year) " ${dia.year}" else ""

/** «última el 3 ago»: el tramo que dice cuándo se cuadró una cuenta atrasada. */
private fun ultimaEl(ajuste: Long, ahora: Long, zona: TimeZone): String =
    "última el ${fechaCortaDelCuadre(diaDe(ajuste, zona), diaDe(ahora, zona))}"

/**
 * **La frase del cuadre de UNA cuenta**, la misma en todas las pantallas (vuelta 31). Patrimonio
 * decía «Hace más de un mes que no cuadras Bancolombia Ahorros» y el detalle de esa cuenta «Nunca la
 * has cuadrado»: dos frases para un mismo hecho se leen como una contradicción. Ahora el detalle y
 * Cuadre de saldos dicen esta, y Patrimonio, Hoy y Movi AI su versión con nombre
 * ([textoDelAvisoDeCuadre]), con la misma regla. **Nunca pierde la fecha** si hubo un cuadre:
 * - sin movimientos → [SIN_MOVIMIENTOS_TODAVIA];
 * - con movimientos y sin un solo ajuste → [NUNCA_LA_HAS_CUADRADO];
 * - cuadrada hace [DIAS_PARA_VOLVER_A_CUADRAR] días o menos → «Cuadrada el 3 oct» («Cuadrada hoy»,
 *   «Cuadrada ayer»);
 * - más → «Hace más de un mes que no la cuadras · última el 3 ago».
 */
fun fraseDelCuadre(cuenta: Account, ahora: Long, zona: TimeZone = AppTimeZone.zone): String {
    val ajuste = cuenta.lastAdjustmentAt
        ?: return if (cuenta.firstEventAt == null) SIN_MOVIMIENTOS_TODAVIA else NUNCA_LA_HAS_CUADRADO
    if (estaSinCuadrar(cuenta, ahora)) return "$HACE_MAS_DE_UN_MES_QUE_NO_LA_CUADRAS · ${ultimaEl(ajuste, ahora, zona)}"
    val dia = diaDe(ajuste, zona)
    val hoy = diaDe(ahora, zona)
    return when (dia) {
        hoy -> "Cuadrada hoy"
        hoy.minus(DatePeriod(days = 1)) -> "Cuadrada ayer"
        else -> "Cuadrada el ${fechaCortaDelCuadre(dia, hoy)}"
    }
}

/**
 * **El aviso, en una línea**, o `null` cuando no hay nada que decir. Sin cifras: no se saben.
 *
 * Con una sola cuenta, su nombre y la regla de [fraseDelCuadre], más corta: «Bancolombia Ahorros ·
 * Nunca la has cuadrado», «Bancolombia Ahorros · última el 3 ago». Con varias, cuántas («2 cuentas
 * nunca se han cuadrado» si ninguna se cuadró nunca). [ahora] y [zona] solo nombran el año de la fecha.
 */
fun textoDelAvisoDeCuadre(
    atrasadas: List<Account>,
    ahora: Long = Clock.System.now().toEpochMilliseconds(),
    zona: TimeZone = AppTimeZone.zone,
): String? = when {
    atrasadas.isEmpty() -> null
    atrasadas.size == 1 -> {
        val cuenta = atrasadas.first()
        val ajuste = cuenta.lastAdjustmentAt
        "${cuenta.name} · ${if (ajuste == null) NUNCA_LA_HAS_CUADRADO else ultimaEl(ajuste, ahora, zona)}"
    }
    atrasadas.all { it.lastAdjustmentAt == null } -> "${atrasadas.size} cuentas nunca se han cuadrado"
    else -> "${atrasadas.size} cuentas llevan más de un mes sin cuadrar"
}

/**
 * La fila de «Para revisar» cuando hay propuestas de cuadre por aviso, o `null` sin ninguna. Con una
 * sola se nombra la cuenta, como el aviso de cuadre de siempre ([textoDelAvisoDeCuadre]).
 */
fun textoDeLasPropuestasPorAviso(propuestas: List<PropuestaDeCuadrePorAviso>): String? = when {
    propuestas.isEmpty() -> null
    propuestas.size == 1 -> "El saldo de ${propuestas.first().cuentaNombre} no coincide con el del banco"
    else -> "${propuestas.size} cuentas no coinciden con el saldo del banco"
}
