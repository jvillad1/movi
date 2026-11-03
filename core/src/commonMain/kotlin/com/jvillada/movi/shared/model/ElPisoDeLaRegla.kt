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
 * el piso y revive como vencidos los meses intermedios sin pago (solo pasa sellando a mano un mes viejo);
 * `sellarOcurrencia` acepta períodos anteriores al piso; y un PUT con una copia vieja de la regla puede dejar
 * la fecha de creación guardada como arranque explícito (si el piso derivado cambió desde que se leyó).
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
 * **¿La hoja de un pago fijo nuevo pregunta «¿Ya pagaste el de este período?»?** Solo en un **gasto**
 * escrito a mano (no desde un movimiento: ese movimiento ya es la ocurrencia de su período y la sella el
 * alta) cuyo vencimiento del día [dia] en el período de [hoy] **ya pasó o es hoy** — la regla de la
 * vuelta 77 para la cuota de un crédito, [laCuotaDeEstePeriodoYaVencio]. Si todavía no vence, no hay nada
 * vencido que esconder. Un ingreso no se pregunta (ver el KDoc de [RecurringRule.yaPagoElDeEstePeriodo]).
 * El server vuelve a comprobarlo antes de aceptar el «Sí».
 *
 * **Conocido y pendiente para otra vuelta:** la guía de primer uso («Lo que pagas cada mes») y la
 * propuesta `proponer_recurrente` de Movi AI crean gastos sin esta pregunta; corren desde el período en
 * que se crean, como con «No». Pura.
 */
fun sePreguntaSiYaPagasteElDeEstePeriodo(
    tipo: TransactionType,
    desdeUnMovimiento: Boolean,
    dia: Int,
    hoy: LocalDate,
    ajustes: PeriodSettings,
): Boolean = tipo == TransactionType.EXPENSE && !desdeUnMovimiento && dia in 1..31 &&
    laCuotaDeEstePeriodoYaVencio(dia, hoy, ajustes)

/** [sePreguntaSiYaPagasteElDeEstePeriodo] con «hoy» en ISO, para el server. Una fecha ilegible da `false`. */
fun sePreguntaSiYaPagasteElDeEstePeriodoEl(
    tipo: TransactionType,
    desdeUnMovimiento: Boolean,
    dia: Int,
    hoyIso: String,
    ajustes: PeriodSettings,
): Boolean = runCatching { LocalDate.parse(hoyIso.trim()) }.getOrNull()
    ?.let { sePreguntaSiYaPagasteElDeEstePeriodo(tipo, desdeUnMovimiento, dia, it, ajustes) } ?: false

private fun fechaIsoLegible(texto: String?): String? {
    val t = texto?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { LocalDate.parse(t).toString() }.getOrNull()
}
