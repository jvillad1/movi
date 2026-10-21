package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * # El extracto que solo dice cuánto hay: Skandia y la AFC dejan de ser cuentas ciegas
 *
 * El fondo voluntario de pensión de Skandia y la cuenta AFC de Davibank no avisan nada: no mandan
 * SMS ni notificación por cada movimiento, y su saldo lo escribía alguien a mano en la base. Por
 * ahí pasan millones al mes. Lo único que mandan es el extracto en PDF, que dice **cuánto hay al
 * corte**.
 *
 * El server lo lee **sin IA** (`ExtractoDeSkandia.kt`, `ExtractoAfcDavibank.kt` en el server: la
 * forma es fija y leerla con reglas no gasta un peso ni depende del crédito de Anthropic) y arma
 * una **propuesta**: «El extracto dice $X al 31 de agosto; Movi tiene $Y». **Nada se ajusta
 * solo.** «Actualizar el saldo» (`POST /api/accounts/{id}/saldo-del-extracto`) escribe el ajuste
 * de siempre (categoría «Ajuste de saldo», que no cuenta como gasto ni como ingreso), fechado al
 * final del día del corte.
 *
 * Es aparte de [PropuestaDelExtractoDeCredito] a propósito: aquel compara cuatro cifras de una
 * deuda y escribe las condiciones del crédito; este compara una sola cifra de una cuenta de
 * dinero o de inversión y solo escribe el ajuste. Generalizar el de crédito habría cambiado un
 * contrato que el APK instalado ya lee.
 */
@Serializable
enum class ExtractoDeSaldoDe {
    /** «Productos Skandia» · «Fondo Voluntario de Pensión Skandia». */
    SKANDIA_FONDO_VOLUNTARIO,

    /** El extracto de una cuenta de ahorros de Davibank (la AFC del dueño es una). */
    DAVIBANK_AHORROS,
}

/** Un contrato del fondo, con su saldo. Solo los últimos cuatro del número: es lo que se muestra. */
@Serializable
data class ContratoDelExtracto(
    val terminaEn: String,
    val saldo: Long,
)

/**
 * **Lo que dice el extracto.** [saldo] en pesos enteros (el banco escribe centavos; Movi trabaja en
 * pesos), [fechaDeCorte] ISO (`2026-08-31`): el saldo se compara —y el ajuste se fecha— ahí.
 */
@Serializable
data class ResumenDelExtractoDeSaldo(
    val de: ExtractoDeSaldoDe,
    val saldo: Long,
    val fechaDeCorte: String,
    val moneda: String = "COP",
    /** Los últimos cuatro de la cuenta que nombra el extracto (la AFC). `null` en Skandia. */
    val numero: String? = null,
    /** El detalle por contrato de Skandia, solo si suma exactamente el total. */
    val contratos: List<ContratoDelExtracto> = emptyList(),
)

/** Una cuenta del dueño a la que se le podría poner el saldo del extracto. */
@Serializable
data class CuentaParaElSaldo(
    val id: String,
    val nombre: String,
    /** Lo que Movi tiene en esa cuenta **al corte del extracto**, sin anulados. */
    val saldoEnMovi: Long,
    /**
     * La cuenta tiene un ajuste **posterior** al corte: el dueño ya la cuadró contra algo más nuevo
     * que este extracto, y ajustarla al corte movería ese cuadre. No se ofrece «Actualizar».
     */
    val cuadradaDespues: Boolean = false,
)

/**
 * **La propuesta.** [cuentaId] es la cuenta que el extracto nombra sin duda (la AFC por su número,
 * Skandia por llamarse así); `null` si no hay ninguna o hay más de una, y entonces el dueño elige
 * entre [opciones] (las cuentas de dinero e inversión en la moneda del extracto, la nombrada
 * primero si la hay). Cada opción ya trae lo que Movi tiene al corte: elegir no pide la red.
 */
@Serializable
data class PropuestaDelExtractoDeSaldo(
    val resumen: ResumenDelExtractoDeSaldo,
    val cuentaId: String? = null,
    val opciones: List<CuentaParaElSaldo> = emptyList(),
)

/** «Fondo voluntario de pensión Skandia» / «Cuenta de ahorros Davibank ·9497». */
fun nombreDelExtractoDeSaldo(r: ResumenDelExtractoDeSaldo): String = when (r.de) {
    ExtractoDeSaldoDe.SKANDIA_FONDO_VOLUNTARIO -> "Fondo voluntario de pensión Skandia"
    ExtractoDeSaldoDe.DAVIBANK_AHORROS -> "Cuenta de ahorros Davibank" + (r.numero?.let { " ·$it" } ?: "")
}

/** «31 de agosto de 2026», o la fecha tal cual si no se entiende. */
fun corteLegible(r: ResumenDelExtractoDeSaldo): String =
    diaLegible(r.fechaDeCorte)?.let { "$it de ${r.fechaDeCorte.take(4)}" } ?: r.fechaDeCorte

/**
 * «El extracto dice $106.787.543 al 31 de agosto de 2026; Movi tiene $100.000.000.» — la frase de
 * la propuesta. Sin cuenta elegida, solo la primera mitad.
 */
fun fraseDelExtractoDeSaldo(r: ResumenDelExtractoDeSaldo, cuenta: CuentaParaElSaldo?): String {
    val dice = "El extracto dice ${plataDelExtracto(r.saldo, r.moneda)} al ${corteLegible(r)}"
    return if (cuenta == null) "$dice." else "$dice; Movi tiene ${plataDelExtracto(cuenta.saldoEnMovi, r.moneda)}."
}

/** «Contrato ·3541: $94.951.151», uno por contrato del fondo. */
fun renglonesDeLosContratos(r: ResumenDelExtractoDeSaldo): List<String> =
    r.contratos.map { "Contrato ·${it.terminaEn}: ${plataDelExtracto(it.saldo, r.moneda)}" }

/** La cuenta que la propuesta trae elegida, o `null`. */
fun cuentaNombrada(p: PropuestaDelExtractoDeSaldo): CuentaParaElSaldo? =
    p.cuentaId?.let { id -> p.opciones.firstOrNull { it.id == id } }

/** ¿Hay algo que actualizar en [cuenta]? Ni sin cuenta, ni igual, ni cuadrada después del corte. */
fun hayQueActualizarElSaldo(r: ResumenDelExtractoDeSaldo, cuenta: CuentaParaElSaldo?): Boolean =
    cuenta != null && !cuenta.cuadradaDespues && cuenta.saldoEnMovi != r.saldo

private fun plataDelExtracto(valor: Long, moneda: String): String =
    if (moneda == "COP") "$" + conPuntosDeMiles(valor) else conPuntosDeMiles(valor) + " " + moneda
