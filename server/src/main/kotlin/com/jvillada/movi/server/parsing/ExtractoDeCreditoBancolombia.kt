package com.jvillada.movi.server.parsing

import com.jvillada.movi.server.routes.montoDelSms
import com.jvillada.movi.shared.model.ResumenDelExtractoDeCredito
import kotlin.math.roundToLong

/**
 * # El extracto de un crédito de Bancolombia, leído sin IA
 *
 * La forma sale de los **extractos reales del dueño** (Libre inversión ·9695 y Libranza ·4818,
 * `~/Developer/investments/documents/bancolombia_credito-*.pdf.txt`, el texto que saca PDFBox):
 *
 * ```
 * OBLIGACIÓN Nº:    290109695
 * FECHA DE PAGO:    9/15/2026
 * CONCEPTO ABONO ANTERIOR CUOTA A PAGAR
 * ABONO A CAPITAL 1,032,007.00 452,697.00
 * INTERÉS CORRIENTE 136,857.00 594,567.00
 * SEGURO VIDA 35,200.00 156,800.00
 * TOTAL 1,204,064.00 1,204,064.00
 * FECHA CORTE EXTRACTO 8/20/2026
 * SALDO DE CAPITAL 40,710,555.00
 * TASA DE INTERÉS E.A. 11.27
 * ```
 *
 * Cada renglón del desglose trae dos cifras: lo que se abonó en el pago anterior y lo que se cobra
 * en la cuota que viene. **Vale la segunda**: es la cuota de la «FECHA DE PAGO». Las fechas vienen
 * mes/día/año (8/20/2026); si el primer número no puede ser un mes, se leen día/mes.
 *
 * El saldo que se compara es el **de capital**, no el «SALDO DE CRÉDITO» (que suma lo causado):
 * la deuda que Movi lleva es capital, porque una cuota solo le baja el capital.
 *
 * Es una lectura **sin IA a propósito**: el extracto de un crédito tiene una forma fija y diez
 * renglones, y leerlo con reglas no gasta un peso ni depende del crédito de Anthropic. Lo que no
 * tenga esta forma devuelve `null` (y el server contesta como siempre: no es un extracto de
 * movimientos). Otros bancos (Davibank, Occidente) no están: no hay extractos suyos con texto.
 */
fun leerElExtractoDeCreditoDeBancolombia(texto: String): ResumenDelExtractoDeCredito? {
    val lineas = texto.lines().map { it.trim() }
    fun linea(regex: Regex): MatchResult? = lineas.firstNotNullOfOrNull { regex.find(it) }
    fun monto(raw: String?): Long? = raw?.let(::montoDelSms)?.roundToLong()

    val numero = linea(Regex("""\bOBLIGACI[ÓO]N\s*N[º°o]?\.?\s*:?\s*(\d{4,})""", RegexOption.IGNORE_CASE))?.groupValues?.get(1)
    val fechaDePago = linea(Regex("""\bFECHA DE PAGO\s*:?\s*(\d{1,2}/\d{1,2}/\d{4})""", RegexOption.IGNORE_CASE))
        ?.groupValues?.get(1)?.let(::fechaIso)
    val fechaDeCorte = linea(Regex("""\bFECHA CORTE EXTRACTO\s*:?\s*(\d{1,2}/\d{1,2}/\d{4})""", RegexOption.IGNORE_CASE))
        ?.groupValues?.get(1)?.let(::fechaIso)
    val cifra = """([0-9][0-9.,]*[0-9]|[0-9]|\.\d{2})"""
    /** La SEGUNDA cifra del renglón del desglose: la de la cuota que viene. */
    fun deLaCuota(rotulo: String): Long? =
        linea(Regex("""^$rotulo\s+$cifra\s+$cifra(?=\s|$)""", RegexOption.IGNORE_CASE))?.groupValues?.get(2)?.let(::monto)
    val interes = deLaCuota("INTER[ÉE]S CORRIENTE")
    val seguro = deLaCuota("SEGURO (?:DE )?VIDA")
    val cuota = deLaCuota("TOTAL")
    val saldo = linea(Regex("""\bSALDO DE CAPITAL\s*:?\s*$cifra(?=\s|$)""", RegexOption.IGNORE_CASE))?.groupValues?.get(1)?.let(::monto)
    val tasa = linea(Regex("""\bTASA DE INTER[ÉE]S E\.?\s*A\.?\s*:?\s*([0-9]+(?:[.,][0-9]+)?)\s*%?(?=\s|$)""", RegexOption.IGNORE_CASE))
        ?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        ?.takeIf { it > 0.0 && it < 100.0 }

    val resumen = ResumenDelExtractoDeCredito(
        numero = numero,
        saldoCapital = saldo,
        interesDelPeriodo = interes,
        seguro = seguro?.takeIf { it > 0L },
        tasaEa = tasa,
        cuota = cuota?.takeIf { it > 0L },
        fechaDePago = fechaDePago,
        fechaDeCorte = fechaDeCorte,
    )
    return resumen.takeIf { it.traeAlgo }
}

/** «8/20/2026» → «2026-08-20». Mes/día, salvo que el primero no pueda ser un mes. */
private fun fechaIso(raw: String): String? {
    val (a, b, anio) = raw.split('/').map { it.toIntOrNull() ?: return null }
    val (mes, dia) = if (a in 1..12) a to b else b to a
    if (mes !in 1..12 || dia !in 1..31) return null
    return "%04d-%02d-%02d".format(anio, mes, dia)
}
