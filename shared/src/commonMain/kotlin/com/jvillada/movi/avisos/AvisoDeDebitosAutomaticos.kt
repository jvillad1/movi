package com.jvillada.movi.avisos

import com.jvillada.movi.shared.model.DebitoAutomaticoPorConfirmar
import com.jvillada.movi.shared.model.loQueSeCobra
import com.jvillada.movi.shared.time.epochMillisToAppDate
import com.jvillada.movi.ui.fecha.MESES_DEL_ANIO
import kotlinx.datetime.LocalDate

/*
 * # «El banco debió cobrar hoy la cuota de Libre inversión 9695. ¿Se cobró?»
 *
 * Hueco que dejó #440: cuando el banco cobra solo una cuota (o un recurrente), `GET
 * /api/debitos-automaticos` la propone armada en «Por revisar», pero nadie le avisaba al dueño —el
 * banco justamente no manda nada—. Un Worker diario del teléfono (`AvisoDeDebitosAutomaticosWorker`,
 * en `:androidApp`) lee esa misma lista y avisa lo nuevo; tocar el aviso abre «Por revisar».
 *
 * Acá vive lo que decide y lo que dice, puro y probado, como el resto de `AvisosDelTelefono.kt`.
 */

/** Cuántas claves de débitos ya avisados recuerda el teléfono: de sobra para varios meses. */
const val TOPE_DE_DEBITOS_AVISADOS: Int = 60

/**
 * **Qué débitos avisar**: los que el teléfono no avisó todavía. La llave es
 * [DebitoAutomaticoPorConfirmar.clave] —regla y período—, así que **cada débito suena una sola vez por
 * período**: el Worker corre todos los días y la propuesta sigue en la lista hasta que el dueño la
 * contesta, pero el mismo vencimiento no vuelve a sonar. El del mes siguiente es otra clave y sí suena.
 */
fun debitosParaAvisar(
    debitos: List<DebitoAutomaticoPorConfirmar>,
    yaAvisados: Collection<String>,
): List<DebitoAutomaticoPorConfirmar> =
    debitos.filter { it.clave !in yaAvisados }.distinctBy { it.clave }

/**
 * Lo que el teléfono recuerda después de avisar [avisados]: las claves de antes más las nuevas, sin
 * repetir y con tope ([TOPE_DE_DEBITOS_AVISADOS]); se olvidan primero las más viejas.
 */
fun recordarDebitosAvisados(previos: List<String>, avisados: List<DebitoAutomaticoPorConfirmar>): List<String> =
    (previos + avisados.map { it.clave }).distinct().takeLast(TOPE_DE_DEBITOS_AVISADOS)

/** «hoy», o «el 3 de octubre» si el vencimiento fue antes (el teléfono estuvo apagado ese día). */
private fun cuando(vence: String, hoy: LocalDate): String {
    val dia = runCatching { LocalDate.parse(vence) }.getOrNull() ?: return "hoy"
    return if (dia >= hoy) "hoy" else "el ${dia.dayOfMonth} de ${MESES_DEL_ANIO[dia.monthNumber - 1]}"
}

private fun montoDe(debito: DebitoAutomaticoPorConfirmar): String = montoParaElAviso(debito.monto.toDouble(), debito.moneda)

/**
 * **El texto del aviso.** Uno: «Débito automático por confirmar» y «El banco debió cobrar hoy la
 * cuota de Libre inversión 9695 ($1.204.064). ¿Se cobró?». Varios: «2 débitos automáticos por
 * confirmar», con uno por renglón. `null` sin nada que decir.
 */
fun textoDeDebitosAutomaticos(debitos: List<DebitoAutomaticoPorConfirmar>, hoy: LocalDate): TextoDeAviso? {
    if (debitos.isEmpty()) return null
    if (debitos.size == 1) {
        val debito = debitos.single()
        return TextoDeAviso(
            titulo = "Débito automático por confirmar",
            texto = "El banco debió cobrar ${cuando(debito.vence, hoy)} ${loQueSeCobra(debito)} (${montoDe(debito)}). ¿Se cobró?",
        )
    }
    return TextoDeAviso(
        titulo = "${debitos.size} débitos automáticos por confirmar",
        texto = "El banco debió cobrarlos solo. ¿Se cobraron? — $TOCA_PARA_REVISAR",
        lineas = debitos.map { "${loQueSeCobra(it).replaceFirstChar(Char::uppercase)} · ${montoDe(it)}" },
    )
}

/**
 * [textoDeDebitosAutomaticos] con «hoy» sacado de [ahora] en la zona de la app. Es lo que llama el
 * Worker: `:androidApp` no ve `kotlinx-datetime`.
 */
fun textoDelAvisoDeDebitos(debitos: List<DebitoAutomaticoPorConfirmar>, ahora: Long): TextoDeAviso? =
    textoDeDebitosAutomaticos(debitos, epochMillisToAppDate(ahora))

/** La hora del aviso, en Bogotá: después del de banco mudo, para no sonar juntos. El banco ya debitó de madrugada. */
const val HORA_DEL_AVISO_DE_DEBITOS: Int = 10
