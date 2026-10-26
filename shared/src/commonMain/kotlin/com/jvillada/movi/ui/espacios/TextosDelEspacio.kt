package com.jvillada.movi.ui.espacios

import com.jvillada.movi.shared.model.Espacio
import com.jvillada.movi.shared.model.FinancialEvent
import com.jvillada.movi.shared.model.GastoDelEspacio
import com.jvillada.movi.shared.model.MIEMBRO_YO
import com.jvillada.movi.shared.model.RepartoDelEspacio
import com.jvillada.movi.shared.model.Reembolso
import com.jvillada.movi.shared.model.SaldoDelEspacio
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.idsDeMiembros
import com.jvillada.movi.shared.model.netoDe

/**
 * # Lo que dice la pantalla de un espacio (R3)
 *
 * Funciones puras, para que las frases se prueben sin montar la pantalla. Español neutro, tuteo,
 * frases cortas.
 */

/**
 * El saldo dicho para el dueño: «Caro te debe» $390.000 «para quedar 50/50».
 *
 * @property titulo quién le debe a quién.
 * @property monto cuánto (0 si están a mano).
 * @property apoyo «para quedar 50/50» o «para quedar a mano»; `null` si no hay deuda.
 * @property reembolso el pago que lo salda, si el dueño paga o cobra; `null` si están a mano.
 */
data class FraseDelSaldo(
    val titulo: String,
    val monto: Long,
    val apoyo: String?,
    val reembolso: Reembolso?,
)

/** El nombre de [miembroId] en [espacio], o «Alguien». El dueño es «Tú». */
fun nombreEnElEspacio(espacio: Espacio, miembroId: String): String =
    if (miembroId == MIEMBRO_YO) "Tú" else espacio.miembros.firstOrNull { it.id == miembroId }?.nombre?.ifBlank { null } ?: "Alguien"

/** «50/50» con dos miembros en partes iguales; si no, «a mano». */
fun aQuedar(espacio: Espacio): String =
    if (espacio.reparto == RepartoDelEspacio.PARTES_IGUALES && idsDeMiembros(espacio).size == 2) "para quedar 50/50"
    else "para quedar a mano"

/** Ver [FraseDelSaldo]. */
fun fraseDelSaldo(espacio: Espacio, saldo: SaldoDelEspacio): FraseDelSaldo {
    val neto = saldo.netoDe(MIEMBRO_YO)
    val cobras = saldo.reembolsos.filter { it.a == MIEMBRO_YO }
    val pagas = saldo.reembolsos.filter { it.de == MIEMBRO_YO }
    return when {
        neto > 0L && cobras.size == 1 ->
            FraseDelSaldo("${nombreEnElEspacio(espacio, cobras.single().de)} te debe", neto, aQuedar(espacio), cobras.single())
        neto > 0L -> FraseDelSaldo("Te deben", neto, aQuedar(espacio), cobras.firstOrNull())
        neto < 0L && pagas.size == 1 ->
            FraseDelSaldo("Le debes a ${nombreEnElEspacio(espacio, pagas.single().a)}", -neto, aQuedar(espacio), pagas.single())
        neto < 0L -> FraseDelSaldo("Debes", -neto, aQuedar(espacio), pagas.firstOrNull())
        else -> FraseDelSaldo("Están a mano", 0L, null, null)
    }
}

/** «Compartido · Juan y Caro». */
fun quienesComparten(espacio: Espacio): String {
    val nombres = espacio.miembros.map { it.nombre.ifBlank { nombreEnElEspacio(espacio, it.id) } }
    val lista = when (nombres.size) {
        0 -> ""
        1 -> nombres.single()
        else -> nombres.dropLast(1).joinToString(", ") + " y " + nombres.last()
    }
    return "Compartido · $lista"
}

/** «Mitad» en un 50/50, «Te toca» en cualquier otro reparto. */
fun rotuloDeTuParte(espacio: Espacio): String =
    if (espacio.reparto == RepartoDelEspacio.PARTES_IGUALES && idsDeMiembros(espacio).size == 2) "Mitad" else "Te toca"

/** El subtítulo de un gasto del espacio: «Pagaste tú · Mercado» o «Pagó Caro · Mercado». */
fun quienPagoElGasto(espacio: Espacio, gasto: GastoDelEspacio): String {
    val quien = if (gasto.pagadoPor == MIEMBRO_YO) "Pagaste tú" else "Pagó ${nombreEnElEspacio(espacio, gasto.pagadoPor)}"
    return if (gasto.categoria.isBlank()) quien else "$quien · ${gasto.categoria}"
}

/** La propuesta de vincular una transferencia: «¿Esta transferencia de Caro es para saldar la casa?». */
fun preguntaDeSaldar(espacio: Espacio, deOtro: String, entra: Boolean): String {
    val quien = nombreEnElEspacio(espacio, deOtro)
    return if (entra) "¿Esta transferencia de $quien salda ${espacio.nombre}?"
    else "¿Esta transferencia a $quien salda ${espacio.nombre}?"
}

/** ¿Se le ofrece «Repartir» a [evento]? Un gasto en pesos, que no es un traspaso ni salda nada. */
fun sePuedeRepartir(evento: FinancialEvent): Boolean =
    evento.type == TransactionType.EXPENSE &&
        evento.currency == "COP" &&
        evento.transferId == null &&
        evento.saldaEspacioId == null &&
        evento.countsAsCashFlow

/** El texto del error de una escritura en un espacio: lo que dijo el server, o que falta conexión. */
const val ESPACIOS_SIN_CONEXION: String = "Para esto necesitas conexión"

/** Lo más largo que guarda la nota de «Pagó otro» (la descripción del gasto en el server). */
const val MAX_NOTA_PAGO_OTRO: Int = 200

/** La descripción de un gasto que pagó otro: la nota escrita, o la categoría si no hay nota. */
fun descripcionDelPagoOtro(nota: String, categoria: String): String =
    nota.trim().ifBlank { categoria.trim() }.take(MAX_NOTA_PAGO_OTRO)
