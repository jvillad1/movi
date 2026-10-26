package com.jvillada.movi.server.parsing

import com.jvillada.movi.server.routes.SIN_CATEGORIA
import com.jvillada.movi.shared.model.ParsedTransaction
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.categoriaProbablePorElNombre
import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

/**
 * # Los movimientos de una cuenta o una tarjeta de Bancolombia, leídos sin IA
 *
 * Es la impresión de «Movimientos: Cuentas» (o «Movimientos: Tarjetas de Crédito») de la Sucursal
 * Virtual Personas: la lista que el dueño descarga en PDF para conciliar. Medido el 25-oct-2026 en producción, de los 66 movimientos de
 * octubre que todavía entraron a mano, 26 eran cargos y abonos que ningún banco avisa (4x1000,
 * intereses, comisiones) y solo salen en esta lista. El lector con Claude los leía, pero la cuenta
 * de Anthropic está sin crédito a propósito; esto los lee sin gastar nada, y lo de después
 * (conciliar contra lo anotado, agrupar los cargos del banco) es el camino de siempre.
 *
 * La forma sale del PDF real, con el texto que saca PDFBox:
 *
 * ```
 * Sucursal Virtual Personas
 * Movimientos: Cuentas
 * Cuenta de Ahorros
 * Ahorros 012 - 345678 - 90
 * Fecha Descripción Referencia Valor
 * 07 sept 2026 TRASLADO A FONDO DE
 * INVERSION
 * 1234567890Fiducuenta -$ 1.000.000,00
 * 06 sept 2026 PAGO QR CAFETERIA 0091234567 -$ 12.500,00
 * 06 sept 2026 ABONO INTERESES AHORROS $ 15,08
 * Dirección IP 1.2.3.4
 * ```
 *
 * (Cifras, números y comercios inventados.)
 *
 * - **Se reconoce** por «Sucursal Virtual Personas» y «Movimientos: Cuentas».
 * - **Un movimiento** empieza en un renglón con fecha («07 sept 2026») y termina en el primer
 *   renglón que acaba en un valor («-$ 1.000.000,00»): la descripción puede ocupar varios renglones.
 * - **La referencia** (un número largo, a veces pegado a un nombre: «1234567890Fiducuenta», o
 *   «null») se saca de la descripción: no ayuda a reconocer el comercio y estorba al emparejar.
 * - **El signo** dice el tipo: con «-» es un gasto, sin él una entrada. Los centavos se redondean al
 *   peso, como lleva Movi la plata; un abono de $0,15 queda en $0 y no se propone.
 * - **El 4x1000, los intereses y las comisiones** no se categorizan acá: los reconoce
 *   `cargosDelExtracto` al conciliar, con la categoría del dueño, como a los del lector con Claude.
 * - **La tarjeta de crédito** («Movimientos: Tarjetas de Crédito») tiene la misma forma con más
 *   columnas: «17 ago 2026 CUOTA DE MANEJO 17 ago 2026 COP $ 35.672,00 0» — descripción, fecha de
 *   corte (a veces), moneda, valor y cuotas. Ahí el signo va al revés: sin «-» es un cobro (un gasto
 *   de la tarjeta) y con «-» un abono. Un cobro en dólares va en dólares, redondeado a la unidad. Es
 *   donde salen la cuota de manejo, los intereses y las suscripciones que no mandan SMS.
 * - **El fondo de inversión** («Movimientos: Inversiones», la Fiducuenta) pone la fecha sola en su
 *   renglón y cierra con unidades, valor de la unidad y valor en pesos: «19 Oct 2026 / Gravamen a
 *   los / movimientos / financieros / 0,158864 -$ 7.728,00 -$ 7.728,00». El signo es el de una
 *   cuenta. Trae los traslados, el 4x1000 y la retención en la fuente; **los rendimientos no son
 *   filas** (se ven en el valor de la unidad), así que se cuadran con el saldo, como siempre. No
 *   escribe el número del fondo.
 * - **Si algo no cuadra** (un movimiento sin valor, una fecha que no existe) se devuelve `null` y el
 *   extracto sigue el camino de siempre. Un movimiento con la fecha o el monto equivocados es peor
 *   que ninguno.
 */
fun leerLosMovimientosDeBancolombia(texto: String): MovimientosDeBancolombia? {
    val plano = sinTildes(texto)
    if (!plano.contains("sucursal virtual personas")) return null
    val deTarjeta = plano.contains("movimientos: tarjetas de credito")
    val deUnFondo = plano.contains("movimientos: inversiones")
    if (!deTarjeta && !deUnFondo && !plano.contains("movimientos: cuentas")) return null

    // El PDF real separa el «$» de la cifra con un espacio duro (U+00A0), que `\s` no reconoce.
    val lineas = texto.replace('\u00A0', ' ').lines().map { it.trim() }.filter { it.isNotEmpty() }
        .filterNot { sinTildes(it).startsWith("direccion ip") }
    val numero = if (deUnFondo) {
        // La lista del fondo no escribe el número: la cuenta la encuentra la revisión por el nombre
        // (ver [MovimientosDeBancolombia.deUnFondo]).
        null
    } else if (deTarjeta) {
        lineas.firstNotNullOfOrNull { TARJETA.find(it)?.groupValues?.get(1) }
    } else {
        lineas.firstNotNullOfOrNull { CUENTA.find(it)?.groupValues?.get(2) }?.filter { it.isDigit() }?.takeLast(4)
    }

    val filas = mutableListOf<ParsedTransaction>()
    var fecha: LocalDate? = null
    val renglones = mutableListOf<String>()
    for (linea in lineas) {
        val empieza = FECHA.find(linea)
        if (empieza != null) {
            // Un movimiento nuevo con el anterior todavía sin valor: la lista no tiene la forma que
            // se espera, y no se adivina.
            if (fecha != null) return null
            fecha = fechaDelRenglon(empieza) ?: return null
            renglones.clear()
            renglones += empieza.groupValues[4].trim()
        } else if (fecha != null) {
            renglones += linea
        } else {
            continue
        }
        val junto = renglones.joinToString(" ").trim()
        if (deUnFondo) {
            // El último valor es el de pesos, con su signo; antes van las unidades y el valor de la
            // unidad, que no son parte de la descripción.
            val valor = VALOR_DE_FONDO.find(junto) ?: continue
            val centavos = centavosDelExtracto(valor.groupValues[1] + valor.groupValues[2]) ?: return null
            fila(fecha!!, junto.substring(0, valor.range.first), centavos, junto)?.let { filas += it }
        } else if (deTarjeta) {
            val valor = VALOR_DE_TARJETA.find(junto) ?: continue
            val centavos = centavosDelExtracto(valor.groupValues[2] + valor.groupValues[3]) ?: return null
            // En la tarjeta un valor positivo es un cobro y uno con «-» un abono; la fecha de corte
            // que sigue a la descripción no es parte de ella.
            val crudo = CORTE_AL_FINAL.replace(junto.substring(0, valor.range.first).trim(), "")
            fila(fecha!!, crudo, -centavos, junto, moneda = valor.groupValues[1])?.let { filas += it }
        } else {
            val valor = VALOR.find(junto) ?: continue
            val centavos = centavosDelExtracto(valor.groupValues[1] + valor.groupValues[2]) ?: return null
            fila(fecha!!, junto.substring(0, valor.range.first), centavos, junto)?.let { filas += it }
        }
        fecha = null
    }
    if (fecha != null || filas.isEmpty()) return null
    return MovimientosDeBancolombia(numero = numero, filas = filas, deUnFondo = deUnFondo)
}

/**
 * Lo que se leyó: los últimos cuatro de la cuenta (si estaban) y sus movimientos. [deUnFondo] es la
 * lista de un fondo de inversión, que no escribe su número.
 */
data class MovimientosDeBancolombia(
    val numero: String?,
    val filas: List<ParsedTransaction>,
    val deUnFondo: Boolean = false,
)

/**
 * Una fila de la lista. [centavos] con el signo de la plata de la cuenta: negativo es lo que salió
 * (un gasto), positivo lo que entró. [moneda] es la del movimiento: en la tarjeta, un cobro en dólares
 * va en dólares, como lo lleva Movi en la cuenta «USD» de la tarjeta.
 */
private fun fila(fecha: LocalDate, crudo: String, centavos: Long, texto: String, moneda: String = "COP"): ParsedTransaction? {
    val pesos = pesosDe(abs(centavos))
    if (pesos == 0L) return null
    val descripcion = crudo.split(Regex("""\s+""")).filter { it.isNotEmpty() && !esReferencia(it) }
        .joinToString(" ")
    if (descripcion.isEmpty()) return null
    val comercio = PREFIJOS.fold(descripcion) { d, prefijo ->
        if (d.startsWith(prefijo, ignoreCase = true)) d.substring(prefijo.length).trim() else d
    }.ifEmpty { descripcion }
    val tipo = if (centavos < 0) TransactionType.EXPENSE else TransactionType.INCOME
    return ParsedTransaction(
        id = UUID.randomUUID().toString(),
        date = fecha.toString(),
        merchant = comercio,
        amount = pesos,
        currency = moneda,
        type = tipo,
        category = categoriaProbablePorElNombre(descripcion) ?: SIN_CATEGORIA,
        description = descripcion,
        rawText = texto,
    )
}

private fun fechaDelRenglon(m: MatchResult): LocalDate? {
    val mes = mesDelExtracto(m.groupValues[2]) ?: return null
    return runCatching { LocalDate.of(m.groupValues[3].toInt(), mes, m.groupValues[1].toInt()) }.getOrNull()
}

/** Un número de referencia, solo o pegado a un nombre («1234567890Fiducuenta»), o «null». */
private fun esReferencia(token: String): Boolean =
    token.equals("null", ignoreCase = true) || Regex("""\d{6,}[A-Za-zÁÉÍÓÚáéíóúÑñ-]*""").matches(token)

/** Lo que el banco antepone al nombre del comercio y no es parte de él. */
private val PREFIJOS = listOf("COMPRA EN ", "PAGO QR ", "PAGO PSE ")

/** «07 sept 2026 RESTO…»: día, mes abreviado, año y lo que sigue en el renglón. */
private val FECHA = Regex("""^(\d{1,2})\s+([A-Za-zÁÉÍÓÚáéíóú]{3,10})\.?\s+(\d{4})\b(.*)$""")

/** El valor al final del movimiento: «-$ 1.932.000,00» o «$ 15,08». */
private val VALOR = Regex("""(-?)\$\s?(\d{1,3}(?:\.\d{3})*,\d{2})$""")

/**
 * El valor al final de un movimiento de la tarjeta: la moneda, el signo, la cifra y las cuotas.
 * «COP $ 35.672,00 0», «USD $ 20,00 36», o partido en renglones: «COP - $ 9.000.000,00 0».
 */
private val VALOR_DE_TARJETA = Regex("""\b(COP|USD)\s+(-?)\s*\$\s?(\d{1,3}(?:\.\d{3})*,\d{2})\s+\d{1,3}$""")

/** La fecha de corte que la tarjeta pone después de la descripción («… 17 ago 2026»). */
private val CORTE_AL_FINAL = Regex("""\s*\d{1,2}\s+[A-Za-zÁÉÍÓÚáéíóú]{3,10}\.?\s+\d{4}$""")

/**
 * El final de un movimiento del fondo: las unidades (o «--»), el valor de la unidad y el valor en
 * pesos con su signo. «0,158864 -$ 7.728,00 -$ 7.728,00» o «-- $ 1.932.000,00 $ 1.932.000,00».
 */
private val VALOR_DE_FONDO = Regex("""(?:--|\d+(?:,\d+)?)\s+-?\$\s?\d{1,3}(?:\.\d{3})*,\d{2}\s+(-?)\$\s?(\d{1,3}(?:\.\d{3})*,\d{2})$""")

/** «MASTER BLACK *3684»: los últimos cuatro de la tarjeta. */
private val TARJETA = Regex("""^[A-Za-zÁÉÍÓÚáéíóú ]+\*(\d{4})$""")

/** «Ahorros 029 - 550681 - 33» o «Corriente …»: el número de la cuenta. */
private val CUENTA = Regex("""^(Ahorros|Corriente)\s+(\d[\d\s-]{5,}\d)$""", RegexOption.IGNORE_CASE)
