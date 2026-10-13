package com.jvillada.movi.server.sms

import com.jvillada.movi.server.routes.montoDelSms

/** El saldo que nombra un aviso, como lo escribió el banco: con centavos y en su moneda. */
data class SaldoDelAviso(val monto: Double, val moneda: String)

/**
 * **Las tres frases con que un aviso dice el saldo**, seguidas del monto:
 *
 * - «Saldo disponible $138.407» (también «saldo disponible es de $…», «saldo disponible: …»);
 * - «Tu saldo es $138.407» (también «tu saldo disponible es de …»);
 * - «Saldo: $138.407».
 *
 * **Son genéricas, no copiadas de un aviso real.** Al escribir esto (13-oct-2026) no había un solo
 * aviso con saldo entre los ~530 SMS y correos del dueño respaldados en `server/movi-data/backups`,
 * ni en las pruebas: los SMS de Bancolombia que le llegan no lo traen, y el correo de PSE tampoco
 * (Valor, Empresa, Descripción, Fecha, CUS). Falta validarlo con el primer aviso real que lo traiga.
 *
 * El monto tiene que venir **pegado** a la frase (a lo sumo un «:», «es», «de» o el símbolo de la
 * moneda en el medio). Eso es lo que deja afuera lo que nombra el saldo sin decirlo:
 * «¡Tienes nuevo saldo!: Mercado Libre recargó 640.000,00 COP» (Glim: lo que llegó, no el saldo),
 * «rechazada por saldo insuficiente», «Pago de saldo plan familiar» (la descripción de un PSE),
 * «Revisa tus saldos actualizados».
 */
private val saldoRegex = Regex(
    """(?:\bsaldo\s+disponible(?:\s+es)?(?:\s+de)?|\btu\s+saldo(?:\s+disponible)?\s+es(?:\s+de)?|\bsaldo(?=\s*:))""" +
        """\s*:?\s*(\$|\bCOP(?![a-z])|\bUSD(?![a-z]))?\s*""" +
        """([0-9]{1,3}(?:[.,][0-9]{3})+(?:[.,][0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)""" +
        """(?:\s*(COP|USD)\b)?""",
    RegexOption.IGNORE_CASE,
)

/**
 * **El saldo que dice el aviso**, o `null` si no dice ninguno. Si lo dice dos veces, el último: es
 * el de después del movimiento. El monto se lee con [montoDelSms], la misma lectura de los montos
 * del aviso (punto o coma, a la colombiana o a la gringa).
 */
fun saldoDelAviso(texto: String): SaldoDelAviso? {
    val m = saldoRegex.findAll(texto).lastOrNull() ?: return null
    val monto = montoDelSms(m.groupValues[2]) ?: return null
    if (monto < 0.0 || !monto.isFinite()) return null
    val moneda = (m.groupValues[1].ifBlank { m.groupValues[3] }).uppercase().let {
        if (it == "USD") "USD" else "COP"
    }
    return SaldoDelAviso(monto, moneda)
}
