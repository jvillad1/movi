package com.jvillada.movi.server.routes

import com.jvillada.movi.shared.model.CARD_PAYMENT_CATEGORY
import com.jvillada.movi.shared.model.CUOTA_CATEGORY
import com.jvillada.movi.shared.model.ParsedSms
import com.jvillada.movi.shared.model.TRANSFER_CATEGORY
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.categoriaProbablePorElNombre
import com.jvillada.movi.shared.model.normalizarParaBuscar

/**
 * # El correo de PSE
 *
 * PSE (`serviciopse@achcolombia.com.co`) manda un correo por **cada pago hecho por PSE, desde
 * cualquier banco**: la cuota del carro con el Banco de Occidente, Av Villas, Coomeva, el pago de la
 * tarjeta Nu, Movistar. Ninguno de esos llega por SMS como una compra legible, y hasta acá el dueño
 * los anotaba a mano. El cuerpo, en texto plano:
 *
 * ```
 * Valor: $ 138.600,00
 * Empresa: Coomeva Medicina Prepagada S.A.
 * Descripción: Coomeva Pago de saldo plan familiar
 * Fecha de la transacción: 03/10/2026
 * CUS: 709591889
 * ```
 *
 * y el asunto, `PSE - Transacción Aprobada CUS 709591889`. Entra por el MISMO camino que cualquier
 * correo (`guardarCorreoEntrante`) y se lee con el MISMO `parseSms`, que acá solo reconoce la forma y
 * delega en [leerElCorreoDePse].
 *
 * **Lo que el correo NO dice es desde qué cuenta salió la plata.** Eso lo resuelven, en este orden,
 * el aviso del banco del mismo pago (SMS o notificación, ver `AvisosDelMismoPago.kt`), la regla o el
 * crédito emparejado y la memoria del dueño — ver `LoQueMoviSabeDelPago.kt`.
 */

/** Las etiquetas del correo, para cortar un campo donde empieza el siguiente. */
private const val SIGUIENTE_CAMPO =
    """(?=\s*(?:Valor:|Empresa:|Descripci[oó]n:|Fecha de la transacci[oó]n:|CUS:|Estado:|\n|$))"""

private val valorDePse = Regex("""\bValor:\s*\$?\s*([0-9][0-9.,]*)""", RegexOption.IGNORE_CASE)
private val empresaDePse = Regex("""\bEmpresa:\s*(.+?)$SIGUIENTE_CAMPO""", RegexOption.IGNORE_CASE)
private val descripcionDePse = Regex("""\bDescripci[oó]n:\s*(.+?)$SIGUIENTE_CAMPO""", RegexOption.IGNORE_CASE)
private val fechaDePse = Regex("""\bFecha de la transacci[oó]n:\s*(\d{1,2})/(\d{1,2})/(\d{4})""", RegexOption.IGNORE_CASE)
private val cusDePse = Regex("""\bCUS:?\s*(\d{4,})""", RegexOption.IGNORE_CASE)

/**
 * ¿Es un correo de PSE? Pide las dos etiquetas que nadie más escribe juntas —«Empresa:» y el CUS—
 * además del valor. Un SMS o una notificación no las traen nunca, así que esto no cambia la lectura
 * de ningún aviso que ya se leía.
 */
internal fun esUnCorreoDePse(texto: String): Boolean =
    empresaDePse.containsMatchIn(texto) && cusDePse.containsMatchIn(texto) && valorDePse.containsMatchIn(texto)

/** El CUS del pago: el id único de la transacción en PSE. `null` si el texto no es de PSE. */
internal fun cusDelCorreoDePse(texto: String): String? =
    if (esUnCorreoDePse(texto)) cusDePse.find(texto)?.groupValues?.get(1) else null

/** Lo que va al final del nombre de una empresa y no la nombra: «S.A.», «SAS», «(ATH)». */
private val colaDeLaEmpresa = Regex(
    """[\s,.]*(?:\(ATH\)|\bATH|\bS\.?\s?A\.?\s?S\.?|\bS\.?\s?A\.?|\bE\.?\s?S\.?\s?P\.?|\bLTDA\.?)\s*$""",
    RegexOption.IGNORE_CASE,
)

/** «Coomeva Medicina Prepagada S.A.» → «Coomeva Medicina Prepagada»; «Banco de Occidente (ATH)» → «Banco de Occidente». */
internal fun empresaLimpia(empresa: String): String {
    var limpia = empresa.trim()
    while (true) {
        val sin = limpia.replace(colaDeLaEmpresa, "").trim()
        if (sin == limpia || sin.isEmpty()) return limpia
        limpia = sin
    }
}

/** Quién recibe la plata cuando el pago es a un banco o una financiera. */
private val EMPRESAS_FINANCIERAS = listOf(
    "banco", "bancolombia", "davivienda", "davibank", "colpatria", "scotiabank", "bbva", "av villas",
    "occidente", "itau", "falabella", "financiamiento", "financiera", "cooperativa", "cotrafa", "nu ",
    "rappipay", "lulo", "credito",
)

/** Qué es el pago, según lo que el dueño escribió en la descripción o según a quién se le pagó. */
internal enum class QueEsElPagoDePse { DEPOSITO_A_TU_CUENTA, PAGO_DE_TARJETA, CUOTA_DE_CREDITO, GASTO }

/**
 * **La clasificación, con reglas simples y en este orden** (la primera que pega gana):
 *
 * | La descripción dice… | Qué es |
 * |---|---|
 * | «depósito a tu cuenta» | un traspaso a una cuenta suya (Nu), no un gasto |
 * | «tarjeta» con «pago», o «pago mínimo»/«pago total» a un banco | el pago de una tarjeta |
 * | «préstamo», «hipotecario», «libranza»; o «crédito», «cuota» a un banco | la cuota de un crédito suyo |
 * | cualquier otra cosa | un gasto, con la categoría que diga el nombre |
 *
 * «Pago mínimo», «crédito» y «cuota» solo cuentan si la empresa es un banco o una financiera: un
 * «pago mínimo» al colegio sigue siendo el colegio, y una «cuota de administración», el edificio.
 */
internal fun queEsElPagoDePse(empresa: String, descripcion: String?): QueEsElPagoDePse {
    val d = normalizarParaBuscar(descripcion.orEmpty())
    val e = normalizarParaBuscar(empresa) + " "
    val esFinanciera = EMPRESAS_FINANCIERAS.any { it in e }
    return when {
        "deposito a tu cuenta" in d || "deposito a su cuenta" in d -> QueEsElPagoDePse.DEPOSITO_A_TU_CUENTA
        ("tarjeta" in d && "pago" in d) -> QueEsElPagoDePse.PAGO_DE_TARJETA
        esFinanciera && ("pago minimo" in d || "pago total" in d) -> QueEsElPagoDePse.PAGO_DE_TARJETA
        listOf("prestamo", "hipotecario", "libranza").any { it in d } -> QueEsElPagoDePse.CUOTA_DE_CREDITO
        esFinanciera && ("credito" in d || "cuota" in d) -> QueEsElPagoDePse.CUOTA_DE_CREDITO
        else -> QueEsElPagoDePse.GASTO
    }
}

/**
 * **El correo de PSE, leído**: el monto del «Valor», el comercio es la Empresa (limpia), la nota es
 * la Descripción y la fecha la de la transacción. Siempre un gasto (EXPENSE): PSE solo avisa pagos
 * que salen. `null` si no es un correo de PSE o si no trae un monto legible.
 */
internal fun leerElCorreoDePse(texto: String): ParsedSms? {
    if (!esUnCorreoDePse(texto)) return null
    // «Transacción Pendiente»: todavía no salió nada (la rechazada ya la saca `NO_PASARON`).
    if ("pendiente" in normalizarParaBuscar(texto)) return null
    val monto = valorDePse.find(texto)?.groupValues?.get(1)?.trimEnd('.', ',')?.let(::montoDelSms) ?: return null
    val empresaCruda = empresaDePse.find(texto)?.groupValues?.get(1)?.trim().orEmpty()
    val empresa = empresaLimpia(empresaCruda).ifBlank { "Pago PSE" }
    val descripcion = descripcionDePse.find(texto)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    val fecha = fechaDePse.find(texto)?.let { m ->
        val (dia, mes, anio) = m.destructured
        "$anio-${mes.padStart(2, '0')}-${dia.padStart(2, '0')}"
    }
    val categoria = when (queEsElPagoDePse(empresa, descripcion)) {
        QueEsElPagoDePse.DEPOSITO_A_TU_CUENTA -> TRANSFER_CATEGORY
        QueEsElPagoDePse.PAGO_DE_TARJETA -> CARD_PAYMENT_CATEGORY
        QueEsElPagoDePse.CUOTA_DE_CREDITO -> CUOTA_CATEGORY
        QueEsElPagoDePse.GASTO -> categoriaProbablePorElNombre(empresa)
            ?: descripcion?.let(::categoriaProbablePorElNombre)
            ?: SIN_CATEGORIA
    }
    return ParsedSms(
        amount = monto,
        merchant = empresa,
        type = TransactionType.EXPENSE,
        category = categoria,
        currency = "COP",
        nota = descripcion,
        fecha = fecha,
    )
}
