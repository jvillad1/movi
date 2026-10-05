package com.jvillada.movi.shared.model

/*
 * # El traspaso entre cuentas propias, avisado del lado que entra
 *
 * Hueco que dejó #439: las dos patas de un traspaso se armaban solo cuando el aviso era del lado que
 * SALE («Retiraste $X de tu cuenta *9586 Fiducuenta hacia la cuenta *8133»). Si el aviso es del lado
 * que ENTRA y nombra de dónde vino la plata —«Recibiste $X de tu cuenta *9586 en tu cuenta *8133»—
 * y ese origen es una cuenta del dueño, no es un ingreso: es un traspaso, y confirmarlo como ingreso
 * suelto inflaba «Entró» con plata que ya era suya.
 *
 * Esto es lo que comparten el server (que lo pone en [ParsedSms.traspasoDesdeId]) y la app (que no
 * cuenta ese número como la cuenta del aviso): qué número nombra el origen de un ingreso y a qué
 * cuenta propia corresponde.
 */

/**
 * «… **de tu cuenta \*9586** …», «… desde la cuenta \*02955068133 …», «… de tu producto 9586 …», «… de
 * tu cuenta de ahorros \*9586 …». En un ingreso, lo que va detrás de «de/desde» es de donde vino la
 * plata; la cuenta que recibe la dice «en tu cuenta» (o el remitente del aviso).
 */
private val ORIGEN_DEL_INGRESO = Regex(
    """\b(?:de|desde)\s+(?:tu|la|su)\s+(?:cuenta|producto)(?:\s+de\s+ahorros|\s+corriente)?\s*\*?\s?(\d{4,})""",
    RegexOption.IGNORE_CASE,
)

/** Las corridas de dígitos de un nombre de cuenta: «Fiducuenta 9586» → 9586. */
private val DIGITOS_DEL_NOMBRE = Regex("""\d+""")

/**
 * **El número que un aviso de ingreso nombra como origen**, o `null` si no nombra ninguno. Solo mira
 * el texto: si el movimiento es un ingreso lo decide quien llama.
 */
fun numeroDelOrigenDelIngreso(texto: String): String? = ORIGEN_DEL_INGRESO.find(texto)?.groupValues?.get(1)

/**
 * [texto] sin el tramo que nombra el origen de un ingreso («de tu cuenta \*9586»). Para leer **a qué
 * cuenta entró** la plata: ese número es la otra punta, y leído como la cuenta del aviso el ingreso
 * caería en la Fiducuenta en vez de en Ahorros.
 */
fun sinElOrigenDelIngreso(texto: String): String = ORIGEN_DEL_INGRESO.replace(texto, " ")

/**
 * **La cuenta del dueño de la que vino un ingreso**, o `null`.
 *
 * Solo para un [TransactionType.INCOME] que nombra su origen por número ([numeroDelOrigenDelIngreso]),
 * y solo si **una sola** de [cuentas] lleva esos últimos cuatro dígitos en el nombre —la misma regla
 * que lee el número de la cuenta en el resto de la app—. Una deuda o un bien no cuentan: un traspaso
 * sale de una cuenta de plata o de inversión (el avance de una tarjeta tiene su propio camino). Un
 * número que no es de ninguna cuenta suya deja el aviso como lo que era: un ingreso de alguien más.
 */
fun cuentaPropiaDeLaQueVinoElIngreso(tipo: TransactionType, texto: String, cuentas: List<Account>): Account? {
    if (tipo != TransactionType.INCOME) return null
    val cola = numeroDelOrigenDelIngreso(texto)?.takeLast(4) ?: return null
    return cuentas
        .filter { it.type.group != AccountGroup.DEUDA && !it.esBien }
        .filter { cuenta -> DIGITOS_DEL_NOMBRE.findAll(cuenta.name).any { it.value == cola } }
        .singleOrNull()
}
