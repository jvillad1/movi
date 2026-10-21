package com.jvillada.movi.server.parsing

import com.jvillada.movi.shared.model.ContratoDelExtracto
import com.jvillada.movi.shared.model.ExtractoDeSaldoDe
import com.jvillada.movi.shared.model.ResumenDelExtractoDeSaldo
import java.time.YearMonth

/**
 * # El extracto del fondo voluntario de pensión de Skandia, leído sin IA
 *
 * La forma sale del extracto real del dueño (11 páginas, el texto que saca PDFBox). La primera
 * página es el resumen de los productos, y es lo único que se lee:
 *
 * ```
 * Resumen
 * Productos Skandia
 * Agosto 2026
 * Producto Contrato No. Objetivo Inversión Perfil Inversión Saldo
 * Productos Voluntarios
 * Fondo Voluntario de Pensión Skandia
 * Multifund
 * $12,345,678.90
 * Contratos Bonificaciones OMNI 100000001111 $10,000,000.00
 * Contratos Bonificaciones OMNI 100000002222 $2,345,678.90
 * ```
 *
 * (Cifras y contratos inventados.) Con otra extracción el total puede venir en el mismo renglón
 * del fondo («Fondo Voluntario de Pensión Skandia | $12,345,678.90»): se toma la primera cifra con
 * `$` del renglón del fondo o de los tres que le siguen, antes de los contratos.
 *
 * - **Se reconoce** por «Productos Skandia» y «Fondo Voluntario de Pensión», sin depender de tildes.
 * - **El corte** es el último día del mes del resumen («Agosto 2026» → 31 de agosto); si no está,
 *   el de «Saldos Actualizados a Agosto 31 de 2026» de las páginas de cada contrato.
 * - **Los contratos** van solo si suman **exactamente** el total: un detalle que no cuadra es un
 *   detalle mal leído, y se calla en vez de mostrarse.
 *
 * Lo que este saldo ajusta en Movi es la **valuación del fondo**: lo que creció por rendimientos (y
 * lo que bajó por comisiones) sin que nadie lo anotara. Es lo que la revisión «Lo que le falta a
 * Movi» llamaba «sin valuación de inversiones»: con esto el fondo se valúa cada mes con el papel
 * de Skandia, sin escribir en la base a mano.
 */
fun leerElExtractoDeSkandia(texto: String): ResumenDelExtractoDeSaldo? {
    val plano = sinTildes(texto)
    if (!plano.contains("productos skandia") || !plano.contains("fondo voluntario de pension")) return null
    val lineas = texto.lines().map { it.trim() }.filter { it.isNotEmpty() }

    val delFondo = lineas.indexOfFirst { sinTildes(it).contains("fondo voluntario de pension") }
    if (delFondo < 0) return null
    val total = (delFondo until minOf(delFondo + 4, lineas.size))
        .map { lineas[it] }
        .takeWhile { !it.startsWith("Contrato", ignoreCase = true) }
        .firstNotNullOfOrNull { PESOS.find(it)?.groupValues?.get(1)?.let(::centavosDelExtracto) }
        ?.takeIf { it > 0L }
        ?: return null

    val corte = corteDelResumen(lineas) ?: corteDeLosContratos(texto) ?: return null

    val contratos = lineas.mapNotNull { CONTRATO.find(it) }
        .map { it.groupValues[1] to centavosDelExtracto(it.groupValues[2]) }
        .distinctBy { it.first }
    val limpios = contratos.isNotEmpty() && contratos.all { it.second != null } &&
        contratos.sumOf { it.second!! } == total

    return ResumenDelExtractoDeSaldo(
        de = ExtractoDeSaldoDe.SKANDIA_FONDO_VOLUNTARIO,
        saldo = pesosDe(total),
        fechaDeCorte = corte.toString(),
        contratos = if (limpios) contratos.map { ContratoDelExtracto(it.first.takeLast(4), pesosDe(it.second!!)) } else emptyList(),
    )
}

/** «$106,787,542.87», «$ 1.234.567,89». */
private val PESOS = Regex("""\$\s?(\d[\d.,]*\d)""")

/** «Contratos Bonificaciones OMNI 100000001111 $10,000,000.00» (con `|` entre columnas o sin). */
private val CONTRATO = Regex("""^Contratos?\b.*?(\d{6,})[\s|]+\$\s?(\d[\d.,]*\d)[\s|]*$""", RegexOption.IGNORE_CASE)

/** «Agosto 2026», solo, después de «Productos Skandia». */
private fun corteDelResumen(lineas: List<String>): java.time.LocalDate? {
    val desde = lineas.indexOfFirst { sinTildes(it).contains("productos skandia") }.coerceAtLeast(0)
    return lineas.drop(desde).take(12).firstNotNullOfOrNull { linea ->
        val m = MES_Y_ANIO.matchEntire(linea) ?: return@firstNotNullOfOrNull null
        val mes = MESES_DEL_EXTRACTO[sinTildes(m.groupValues[1])] ?: return@firstNotNullOfOrNull null
        YearMonth.of(m.groupValues[2].toInt(), mes).atEndOfMonth()
    }
}

private val MES_Y_ANIO = Regex("""([A-Za-zÁÉÍÓÚáéíóú]+)\s+(\d{4})""")

/** «Saldos Actualizados a Agosto 31 de 2026», aunque venga partido en dos renglones. */
private fun corteDeLosContratos(texto: String): java.time.LocalDate? {
    val m = ACTUALIZADOS.find(texto) ?: return null
    val mes = MESES_DEL_EXTRACTO[sinTildes(m.groupValues[1])] ?: return null
    return runCatching { java.time.LocalDate.of(m.groupValues[3].toInt(), mes, m.groupValues[2].toInt()) }.getOrNull()
}

private val ACTUALIZADOS = Regex(
    """Saldos\s+Actualizados\s+a\s+([A-Za-zÁÉÍÓÚáéíóú]+)\s+(\d{1,2})\s+de\s+(\d{4})""",
    RegexOption.IGNORE_CASE,
)
