package com.jvillada.movi.server.sms

import com.jvillada.movi.shared.model.Account
import com.jvillada.movi.shared.model.AccountType
import com.jvillada.movi.shared.model.APPLE_PAY
import com.jvillada.movi.shared.model.esBien
import com.jvillada.movi.shared.model.normalizarParaBuscar

/*
 * # Vuelta 111 · el pago con Apple Pay que manda el atajo del iPhone
 *
 * La automatización «Transacción» de Atajos (iOS 17+) se dispara al pagar con Apple Pay y entrega el
 * comercio, el monto y la tarjeta. El atajo los manda por separado; el server arma con ellos **un texto
 * legible**, «Apple Pay · Rappi · $27.500 · Nu», y lo guarda como cualquier otro aviso. Así la bandeja,
 * la memoria de categorías, el dedupe (texto + tiempo) y «el mismo pago» (`AvisosDelMismoPago.kt`)
 * funcionan sin ninguna rama nueva: el texto se vuelve a leer con [leerElPagoConApplePay].
 *
 * El monto llega como lo escribe el iPhone, que depende de su idioma y su región: «$27.500,00»,
 * «27,500.00», «COP 27.500», «US$12.50». [montoDelAtajo] lo lee **solo si no hay duda**; si la hay, el
 * aviso queda sin monto («sin monto» en el texto) y el dueño lo escribe en Revisar. Nunca se adivina.
 */

/** Cómo empieza el texto de un pago con Apple Pay. */
internal const val PREFIJO_DE_APPLE_PAY: String = "$APPLE_PAY · "

/** Lo que va en el lugar del monto cuando no se pudo leer con seguridad. */
internal const val SIN_MONTO_DEL_ATAJO: String = "sin monto"

/** El comercio cuando el atajo no lo mandó. */
internal const val COMPRA_SIN_COMERCIO: String = "Compra"

/** Un monto leído: el valor y la moneda (`COP` o `USD`). */
data class MontoDelAtajo(val valor: Double, val moneda: String)

private val MONEDAS_QUE_NO_SE_LEEN = Regex("""(?i)\b(?:EUR|GBP|MXN|ARS|CLP|PEN|BRL|CAD|AUD|JPY|CHF)\b|[€£¥]""")
private val ES_DOLAR = Regex("""(?i)\bUSD\b|US\s?\$|U[$]S""")
private val MARCAS_DE_MONEDA = Regex("""(?i)\bUSD\b|\bCOP\b|\bCOL\b|US\s?\$|U[$]S|\$""")
private val ESPACIO_DE_MILES = Regex("""(?<=\d)[ \u00A0\u202F](?=\d{3}(?:\D|$))""")
private val SOLO_CIFRAS_Y_SEPARADORES = Regex("""^\d[\d.,]*$""")

/**
 * **El monto que escribió el iPhone, o `null` si no se puede leer sin adivinar.** Las reglas:
 *
 * - La moneda: «USD», «US$» → dólares; «COP», «$» o nada → pesos. Otra moneda (€, EUR…) → `null`: Movi
 *   no la lleva y convertirla sería inventar.
 * - Con punto **y** coma, el que va de último separa los decimales (1 o 2 cifras) y el otro los miles:
 *   «27.500,00» y «27,500.00» son lo mismo.
 * - Con un solo separador que se repite, son miles: «1.500.000».
 * - Con un solo separador una vez: seguido de 3 cifras son miles («27.500», «27,500»: ninguna de las
 *   dos monedas tiene tres decimales); de 1 o 2, decimales («12.50», «27,5»).
 * - Los miles tienen que venir de a tres («1.50.000» no se lee), y el monto tiene que ser mayor que cero:
 *   el disparador de Wallet a veces entrega 0 cuando no supo el monto.
 */
fun montoDelAtajo(crudo: String?): MontoDelAtajo? {
    val texto = crudo?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (MONEDAS_QUE_NO_SE_LEEN.containsMatchIn(texto)) return null
    val moneda = if (ES_DOLAR.containsMatchIn(texto)) "USD" else "COP"
    val numero = MARCAS_DE_MONEDA.replace(texto, " ")
        .replace(ESPACIO_DE_MILES, "")
        .trim()
        .removePrefix("-").removePrefix("+").trim()
    if (!SOLO_CIFRAS_Y_SEPARADORES.matches(numero)) return null
    val valor = valorSinAdivinar(numero) ?: return null
    if (valor <= 0.0 || valor >= 1e12) return null
    return MontoDelAtajo(valor, moneda)
}

private fun valorSinAdivinar(numero: String): Double? {
    val puntos = numero.count { it == '.' }
    val comas = numero.count { it == ',' }
    if (puntos == 0 && comas == 0) return numero.toDoubleOrNull()
    if (numero.last() == '.' || numero.last() == ',') return null
    return if (puntos > 0 && comas > 0) {
        val decimal = if (numero.lastIndexOf('.') > numero.lastIndexOf(',')) '.' else ','
        val miles = if (decimal == '.') ',' else '.'
        if (numero.count { it == decimal } != 1) return null
        val entero = numero.substringBefore(decimal)
        val fraccion = numero.substringAfter(decimal)
        if (fraccion.length !in 1..2 || miles in fraccion) return null
        val sinMiles = enteroConMiles(entero, miles) ?: return null
        "$sinMiles.$fraccion".toDoubleOrNull()
    } else {
        val separador = if (puntos > 0) '.' else ','
        val veces = maxOf(puntos, comas)
        if (veces > 1) {
            enteroConMiles(numero, separador)?.toDoubleOrNull()
        } else {
            val fraccion = numero.substringAfter(separador)
            when (fraccion.length) {
                3 -> enteroConMiles(numero, separador)?.toDoubleOrNull()
                1, 2 -> "${numero.substringBefore(separador)}.$fraccion".toDoubleOrNull()
                else -> null
            }
        }
    }
}

/** «1.500.000» con [separador] '.' → «1500000»; `null` si los grupos no son de a tres. */
private fun enteroConMiles(entero: String, separador: Char): String? {
    val grupos = entero.split(separador)
    if (grupos.size == 1) return grupos.single().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    val primero = grupos.first()
    if (primero.length !in 1..3 || primero.startsWith('0')) return null
    if (grupos.drop(1).any { it.length != 3 }) return null
    if (grupos.any { g -> !g.all(Char::isDigit) }) return null
    return grupos.joinToString("")
}

/** «$27.500», «$27.500,50», «USD 12,50»: el monto en el texto del aviso, con las reglas de Colombia. */
internal fun montoParaElTexto(monto: MontoDelAtajo): String {
    val centavos = Math.round(monto.valor * 100)
    val entero = centavos / 100
    val fraccion = centavos % 100
    val conMiles = entero.toString().reversed().chunked(3).joinToString(".").reversed()
    val numero = if (fraccion == 0L) conMiles else "$conMiles,${fraccion.toString().padStart(2, '0')}"
    return if (monto.moneda == "USD") "USD $numero" else "$$numero"
}

private val MONTO_DEL_TEXTO = Regex("""^(\$|USD )(\d{1,3}(?:\.\d{3})*)(?:,(\d{2}))?$""")

/** El monto como lo escribió [montoParaElTexto], o `null`. */
private fun montoDelTexto(parte: String): MontoDelAtajo? {
    val m = MONTO_DEL_TEXTO.matchEntire(parte.trim()) ?: return null
    val entero = m.groupValues[2].replace(".", "")
    val fraccion = m.groupValues[3].ifEmpty { "0" }
    val valor = "$entero.$fraccion".toDoubleOrNull() ?: return null
    return MontoDelAtajo(valor, if (m.groupValues[1] == "$") "COP" else "USD")
}

/** Un dato que viene del iPhone, listo para ir en el texto: una línea, sin «·», con tope. */
private fun limpioParaElTexto(dato: String?, maximo: Int): String? =
    dato?.replace('·', '-')?.replace(Regex("""\s+"""), " ")?.trim()?.take(maximo)?.trim()?.takeIf { it.isNotEmpty() }

/**
 * **El texto del aviso**, «Apple Pay · Rappi · $27.500 · Nu»: el comercio (o [texto] si el atajo no
 * mandó comercio, o «Compra»), el monto leído o «sin monto», y la tarjeta si vino. `null` si no llegó
 * nada que guardar.
 */
internal fun textoDelPagoConApplePay(comercio: String?, monto: String?, tarjeta: String?, texto: String?): String? {
    val elComercio = limpioParaElTexto(comercio, 60)
    val elTexto = limpioParaElTexto(texto, 60)
    val laTarjeta = limpioParaElTexto(tarjeta, 40)
    val elMonto = montoDelAtajo(monto)
    if (elComercio == null && elTexto == null && laTarjeta == null && elMonto == null) return null
    return listOfNotNull(
        APPLE_PAY,
        elComercio ?: elTexto ?: COMPRA_SIN_COMERCIO,
        elMonto?.let(::montoParaElTexto) ?: SIN_MONTO_DEL_ATAJO,
        laTarjeta,
    ).joinToString(" · ")
}

/** ¿Es el texto de un pago con Apple Pay que armó el server? */
internal fun esUnPagoConApplePay(texto: String): Boolean = texto.startsWith(PREFIJO_DE_APPLE_PAY)

/** Lo que dice un pago con Apple Pay: el comercio, el monto (si se leyó) y la tarjeta (si vino). */
data class PagoConApplePay(val comercio: String, val monto: MontoDelAtajo?, val tarjeta: String?)

/** Vuelve a leer el texto de [textoDelPagoConApplePay]; `null` si no es uno. */
internal fun leerElPagoConApplePay(texto: String): PagoConApplePay? {
    if (!esUnPagoConApplePay(texto)) return null
    val partes = texto.removePrefix(PREFIJO_DE_APPLE_PAY).split(" · ")
    if (partes.size < 2) return null
    val comercio = partes[0].trim().ifEmpty { COMPRA_SIN_COMERCIO }
    val monto = montoDelTexto(partes[1])
    if (monto == null && partes[1].trim() != SIN_MONTO_DEL_ATAJO) return null
    return PagoConApplePay(comercio, monto, partes.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() })
}

/** Palabras del nombre de una tarjeta que no dicen cuál es. */
private val PALABRAS_QUE_NO_DISTINGUEN = setOf(
    "tarjeta", "de", "del", "la", "el", "credito", "debito", "card", "visa", "mastercard", "master", "apple", "pay", "cash",
)

private val CUATRO_CIFRAS = Regex("""\d{4}""")

/**
 * **La cuenta de la tarjeta que nombró Apple Pay**, solo si **una sola** cuenta del dueño calza; si no,
 * `null` y se pide en Revisar. Se buscan las tarjetas de crédito y las cuentas de ahorros o corriente
 * (Apple Pay paga con las dos), sin bienes.
 *
 * - Si [tarjeta] trae cuatro cifras («Visa ••3684»), las que las llevan en el nombre.
 * - Si no, las que tienen en el nombre **todas** las palabras que distinguen («Mastercard Black» → «black»
 *   → «Master Black 3684»; «Nu» → «nu», que calza con la cuenta y con la tarjeta Nu: dos, y no se elige).
 */
internal fun cuentaDeLaTarjeta(tarjeta: String?, cuentas: List<Account>): Account? {
    val nombre = tarjeta?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val candidatas = cuentas.filter {
        !it.esBien && it.type in setOf(AccountType.CREDIT_CARD, AccountType.SAVINGS, AccountType.CHECKING)
    }
    val cifras = CUATRO_CIFRAS.findAll(nombre).lastOrNull()?.value
    if (cifras != null) {
        return candidatas.filter { cifras in it.name }.singleOrNull()
    }
    val palabras = palabrasDe(nombre).filter { it !in PALABRAS_QUE_NO_DISTINGUEN }
    if (palabras.isEmpty()) return null
    return candidatas.filter { cuenta -> palabrasDe(cuenta.name).containsAll(palabras) }.singleOrNull()
}

private fun palabrasDe(texto: String): Set<String> =
    normalizarParaBuscar(texto).split(Regex("""[^a-z0-9]+""")).filter { it.isNotEmpty() }.toSet()
