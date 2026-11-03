package com.jvillada.movi.shared.model

import kotlinx.datetime.LocalDate

/*
 * # Vuelta 121 · un pago fijo nuevo no nace vencido del período anterior
 *
 * Claudia (probadora, 2-nov-2026) creó un pago fijo del día 28 con el período = el mes de calendario,
 * y Movi le dijo al instante «Del período de octubre · Venció hace 5 días». Las reglas escritas a mano
 * nacían con `activeFrom = null` («desde siempre»), así que la ocurrencia del 28 de octubre existía, en
 * su gracia, y nadie la había pagado en Movi.
 *
 * Lo decidido por el dueño:
 *  1. **Un pago fijo nunca aparece antes del período en que se creó.** El piso sale de un dato que ya
 *     existe —`recurring_rules.created_at`, desde el 25-sep-2026— y no de una escritura nueva: así las
 *     reglas que los probadores crearon el 2-nov quedan bien al desplegar, sin migración.
 *  2. **Si al crearlo su fecha de este período ya pasó (hoy incluido), la hoja pregunta «¿Ya pagaste el
 *     de este período?»**, con «Sí» por defecto: con «Sí» la regla arranca en el período siguiente.
 *     La misma pregunta y la misma cuenta que el alta de un crédito (vuelta 77,
 *     [laCuotaDeEstePeriodoYaVencio] y [arranqueDeLasCuotasSiYaPagaste]).
 */

/**
 * **Desde cuándo corre una regla guardada** (lo que viaja en [RecurringRule.activeFrom]), ISO o `null`.
 *
 * - Con [activeFrom] guardado (el movimiento que la originó, o el arranque del período siguiente de
 *   «ya pagué» o del salario de la guía): ese, **sin tocarlo**. No se toma el mayor con la creación: una
 *   regla creada hoy desde un movimiento del período pasado tiene que conservar la ocurrencia de ese
 *   período, que la salda ese movimiento (ver [RecurringRule.eventoDeOrigen]).
 * - Sin él, la fecha en que se creó ([creadaEl], ISO): el piso queda en el arranque del período de la
 *   creación (`arranqueDeLaRegla` en el server, con `arranqueEsDesembolso = false`), así que la regla no
 *   tiene ocurrencias en los períodos anteriores.
 * - Sin ninguna de las dos (las reglas de antes del 25-sep-2026, cuando nació `created_at`): `null`, desde
 *   siempre, como hasta hoy. Es lo conservador: no se sabe cuándo nacieron, y una regla vieja no puede
 *   perder ocurrencias que el dueño ya pagó ni las que debe de verdad.
 *
 * **Lo pagado nunca desaparece** (revisión de la vuelta 121): con la fecha de creación, el piso es el
 * **menor** entre ella y [primerSello], el vencimiento de la primera ocurrencia sellada de la regla («Sí,
 * fue este», «Ya lo pagué»). Así un septiembre que el dueño selló antes de escribir la regla —o el
 * octubre falso que los probadores tildaron el 2-nov— sigue viéndose «Pagado», y no vencido. Un sello no
 * baja un [activeFrom] guardado (ese ya dice desde cuándo corre) ni le pone piso a una regla sin fecha.
 *
 * **Límites conocidos** (re-revisión de la 121, a propósito sin tocar): un sello anterior a la creación baja
 * el piso y revive como vencidos los meses intermedios sin pago (solo pasa sellando a mano un mes viejo); y
 * `sellarOcurrencia` acepta períodos anteriores al piso. (El tercero, un PUT con una copia vieja de la regla
 * que dejaba guardado un piso derivado, lo cierra [arranqueQueGuardaUnPut] en la vuelta 125.)
 *
 * Una fecha ilegible cuenta como ausente. Pura.
 */
fun arranqueDeLaReglaGuardada(activeFrom: String?, creadaEl: String?, primerSello: String? = null): String? {
    fechaIsoLegible(activeFrom)?.let { return it }
    val creada = fechaIsoLegible(creadaEl) ?: return null
    val sello = fechaIsoLegible(primerSello) ?: return creada
    return minOf(creada, sello)
}

/**
 * **Vuelta 125 · qué arranque escribe en la fila un PUT de `/api/recurring-rules/{id}`** (la columna
 * `active_from`, ISO o `null`).
 *
 * El GET devuelve el arranque **derivado** ([arranqueDeLaReglaGuardada]: la creación, o el primer sello si
 * es anterior) y los clientes reenvían la regla tal cual la leyeron (`regla.copy(...)`). Ningún cliente de
 * hoy edita esta fecha a propósito: la ponen el alta desde un movimiento, «ya pagué» y la guía, todas en el
 * POST. La vuelta 121 solo reconocía el eco del derivado **de hoy**; una copia leída antes de que el piso
 * cambiara —el dueño selló un mes anterior a la creación, o le quitó la marca— traía otro derivado, y el PUT
 * lo guardaba como arranque explícito: la regla dejaba de seguir sus sellos (lo pagado podía volver a verse
 * vencido, o desaparecer).
 *
 * La regla: todo derivado, de hoy o viejo, es **la creación o anterior** (es el menor entre ella y un sello),
 * así que en una fila sin arranque guardado y con fecha de creación un [pedido] que no pasa de [creadaEl] es
 * un eco y la fila sigue sin arranque. Uno posterior a la creación no puede ser un derivado: es una decisión
 * y se guarda (lo que un PUT ya podía hacer).
 *
 * - [pedido] ausente o ilegible: lo que había ([guardado]), como siempre (un APK viejo no lo manda).
 * - Con [guardado]: el [pedido] (el eco de una fila con arranque es ese mismo valor).
 * - Sin fecha de creación (las reglas de antes del 25-sep-2026): el GET no deriva nada, así que el
 *   [pedido] no es un eco y se guarda.
 *
 * El campo tiene dos estados en el wire (ausente = no lo toques; una fecha), no tres: no hay «quítalo». Pura.
 */
fun arranqueQueGuardaUnPut(pedido: String?, guardado: String?, creadaEl: String?): String? {
    val fecha = fechaIsoLegible(pedido) ?: return guardado
    if (!guardado.isNullOrBlank()) return fecha
    val creada = fechaIsoLegible(creadaEl) ?: return fecha
    return if (fecha <= creada) null else fecha
}

/**
 * **¿La hoja de un pago fijo nuevo pregunta «¿Ya pagaste el de este período?»?** Solo en un **gasto**
 * escrito a mano (no desde un movimiento: ese movimiento ya es la ocurrencia de su período y la sella el
 * alta) cuyo vencimiento del día [dia] en el período de [hoy] **ya pasó o es hoy** — la regla de la
 * vuelta 77 para la cuota de un crédito, [laCuotaDeEstePeriodoYaVencio]. Si todavía no vence, no hay nada
 * vencido que esconder. Un ingreso no se pregunta (ver el KDoc de [RecurringRule.yaPagoElDeEstePeriodo]).
 * El server vuelve a comprobarlo antes de aceptar el «Sí».
 *
 * **Vuelta 124:** también la usan la guía de primer uso («Lo que pagas cada mes», `BorradorDeRecurrente`)
 * y la tarjeta de `proponer_recurrente` de Movi AI (`laPropuestaPreguntaSiYaPago`). Pura.
 */
fun sePreguntaSiYaPagasteElDeEstePeriodo(
    tipo: TransactionType,
    desdeUnMovimiento: Boolean,
    dia: Int,
    hoy: LocalDate,
    ajustes: PeriodSettings,
    /**
     * Vuelta 126 · la frecuencia y el ancla del pago: una no mensual se pregunta solo si **vence en este
     * período** y ese vencimiento ya pasó o es hoy ([vencimientoEnElPeriodo] por regla). Sin ellas (o
     * mensual), lo de siempre.
     */
    frecuencia: FrecuenciaDelPago? = null,
    primerVencimiento: String? = null,
): Boolean {
    if (tipo != TransactionType.EXPENSE || desdeUnMovimiento || dia !in 1..31) return false
    if (frecuencia == null || frecuencia == FrecuenciaDelPago.MENSUAL) return laCuotaDeEstePeriodoYaVencio(dia, hoy, ajustes)
    val regla = RecurringRule(
        id = "", name = "", category = "", amount = 0L, dayOfMonth = dia, type = tipo,
        frecuencia = frecuencia, primerVencimiento = primerVencimiento,
    )
    // Una obligación se cuenta por el mes de referencia (identidad en el mensual), como la cuota.
    val referencia = ajustes.mesDeReferencia()
    val periodo = periodoDeLaFecha(hoy.toString(), referencia) ?: return false
    val vence = vencimientoEnElPeriodo(regla, periodo, referencia) ?: return false
    return vence <= hoy
}

/** [sePreguntaSiYaPagasteElDeEstePeriodo] con «hoy» en ISO, para el server. Una fecha ilegible da `false`. */
fun sePreguntaSiYaPagasteElDeEstePeriodoEl(
    tipo: TransactionType,
    desdeUnMovimiento: Boolean,
    dia: Int,
    hoyIso: String,
    ajustes: PeriodSettings,
    frecuencia: FrecuenciaDelPago? = null,
    primerVencimiento: String? = null,
): Boolean = runCatching { LocalDate.parse(hoyIso.trim()) }.getOrNull()
    ?.let { sePreguntaSiYaPagasteElDeEstePeriodo(tipo, desdeUnMovimiento, dia, it, ajustes, frecuencia, primerVencimiento) } ?: false

private fun fechaIsoLegible(texto: String?): String? {
    val t = texto?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { LocalDate.parse(t).toString() }.getOrNull()
}
