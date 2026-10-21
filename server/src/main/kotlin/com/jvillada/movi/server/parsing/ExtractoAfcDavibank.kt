package com.jvillada.movi.server.parsing

import com.jvillada.movi.shared.model.ExtractoDeSaldoDe
import com.jvillada.movi.shared.model.ResumenDelExtractoDeSaldo
import kotlin.math.abs

/**
 * # El extracto de la AFC de Davibank, leído sin IA
 *
 * La AFC del dueño es una cuenta de ahorros de Davibank, y su extracto es el de cualquier cuenta de
 * ahorros de ese banco. La forma sale del extracto real (el texto que saca PDFBox):
 *
 * ```
 * PERIODO
 * 1 AL 31 AGO 2026
 * CUENTA DE AHORROS
 * No 1234567890
 * RESUMEN CUENTA AHORROS
 * SALDO ANTERIOR DEPOSITOS Y OTROS CREDITOS RETIROS Y OTROS DEBITOS NUEVO SALDO
 * 1.000.000,00 500.000,00 200.000,50 1.299.999,50
 * ```
 *
 * (Cifras y número inventados.) Más abajo, «www.davibank.com».
 *
 * - **Se reconoce** por «Davibank», «RESUMEN CUENTA AHORROS» y «NUEVO SALDO».
 * - **El saldo** es el cuarto número del renglón del resumen, y solo vale si **cuadra**: anterior
 *   más créditos menos débitos tiene que dar el nuevo (al centavo). Si no cuadra, el renglón se leyó
 *   mal y se devuelve `null`.
 * - **El corte** es el último día del «PERIODO» («1 AL 31 AGO 2026» → 31 de agosto).
 * - **El número** de la cuenta (sus últimos cuatro) es lo que la nombra en Movi («AFC 9497»).
 *
 * **Los movimientos no se leen, a propósito.** PDFBox saca la tabla por columnas —primero todas
 * las fechas, después todas las oficinas, las descripciones, los montos, los saldos— y en el
 * extracto real hay una fecha menos que movimientos: dos del mismo día comparten la celda. No se
 * puede saber con certeza qué fecha va con qué movimiento, y un movimiento con la fecha equivocada
 * es peor que ninguno.
 */
fun leerElExtractoAfcDavibank(texto: String): ResumenDelExtractoDeSaldo? {
    val plano = sinTildes(texto)
    if (!plano.contains("davibank") || !plano.contains("resumen cuenta ahorros") || !plano.contains("nuevo saldo")) return null
    val lineas = texto.lines().map { it.trim() }.filter { it.isNotEmpty() }

    val corte = PERIODO.find(texto)?.let { m ->
        val mes = mesDelExtracto(m.groupValues[3]) ?: return@let null
        runCatching { java.time.LocalDate.of(m.groupValues[4].toInt(), mes, m.groupValues[2].toInt()) }.getOrNull()
    } ?: return null

    val desde = lineas.indexOfFirst { sinTildes(it).contains("resumen cuenta ahorros") }
    val cifras = lineas.drop(desde + 1).take(6).firstNotNullOfOrNull { linea ->
        val partes = linea.split(Regex("""[\s|]+""")).filter { it.isNotEmpty() }
        if (partes.size != 4 || !partes.all { CIFRA.matches(it) }) return@firstNotNullOfOrNull null
        partes.map { centavosDelExtracto(it) ?: return@firstNotNullOfOrNull null }
    } ?: return null
    val (anterior, creditos, debitos, nuevo) = cifras
    // Los débitos pueden venir con signo o sin él: se resta su valor.
    if (abs(anterior + creditos - abs(debitos) - nuevo) > 1L) return null
    if (nuevo < 0L) return null

    return ResumenDelExtractoDeSaldo(
        de = ExtractoDeSaldoDe.DAVIBANK_AHORROS,
        saldo = pesosDe(nuevo),
        fechaDeCorte = corte.toString(),
        numero = NUMERO.find(texto)?.groupValues?.get(1)?.takeLast(4),
    )
}

/** «PERIODO 1 AL 31 AGO 2026», partido en renglones o no. */
private val PERIODO = Regex("""PERIODO\s+(\d{1,2})\s+AL\s+(\d{1,2})\s+([A-Za-z]{3,})\.?\s+(\d{4})""", RegexOption.IGNORE_CASE)

/** «CUENTA DE AHORROS No 1234567890». */
private val NUMERO = Regex("""CUENTA\s+DE\s+AHORROS\s+No\.?\s*(\d{4,})""", RegexOption.IGNORE_CASE)

/** «4.737.682,62», «-18.958,04», «1,000.00». */
private val CIFRA = Regex("""-?\$?\d[\d.,]*\d""")
