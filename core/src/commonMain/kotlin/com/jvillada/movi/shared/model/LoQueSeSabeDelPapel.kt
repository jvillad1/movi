package com.jvillada.movi.shared.model

import kotlinx.serialization.Serializable

/**
 * Vuelta 129 · **qué producto del banco es un papel**: una tarjeta de crédito, una cuenta (ahorros,
 * corriente, un fondo) o un crédito. Lo dice el nombre del archivo ([productoQueNombraElArchivo]) o
 * lo que Movi ya leyó del contenido ([PistasDelPapel.producto]). `null` en cualquiera de los dos es
 * «no se sabe», y entonces el nombre del banco solo no alcanza para proponer una cuenta: «Nu» puede
 * ser la tarjeta o la cuenta de ahorros.
 */
@Serializable
enum class ProductoDelPapel { TARJETA, CUENTA, CREDITO }

/**
 * Vuelta 129 · **lo que Movi ya sabe del contenido de un archivo**, porque ya lo leyó (una fila de
 * `lecturas_de_papeles` con la misma huella): el banco, los últimos cuatro de las cuentas que nombra
 * y, si se sabe, qué producto es. Lo devuelve `POST /api/documents/lo-que-se-sabe`; la hoja de subida
 * lo usa antes que el nombre del archivo para proponer la cuenta.
 */
@Serializable
data class PistasDelPapel(
    val banco: String = "",
    val numerosDeCuenta: List<String> = emptyList(),
    val producto: ProductoDelPapel? = null,
)

/** El cuerpo de `POST /api/documents/lo-que-se-sabe`: la huella va en el cuerpo, no en la URL. */
@Serializable
data class PedidoDeLoQueSeSabe(val huella: String)

/**
 * **Los últimos cuatro dígitos de tarjeta o cuenta que nombra un extracto**: los del nombre del
 * archivo (cuatro dígitos sueltos que no son un año: «Extracto_2334_08_2026.pdf» → 2334) y los
 * números enmascarados del texto («**** 3684», «terminada en 9208»), del más confiable al menos.
 *
 * Vivía en el server (`StatementParser.numerosDeCuenta`); se mudó acá en la vuelta 129 para que la
 * hoja de subida de Documentos lea el nombre del archivo con la MISMA regla, sin una copia.
 */
fun numerosDeCuentaDelExtracto(nombreDeArchivo: String, texto: String = ""): List<String> {
    val delArchivo = Regex("""(?<!\d)\d{4}(?!\d)""").findAll(nombreDeArchivo.substringBeforeLast('.'))
        .map { it.value }
        .filterNot { it.toInt() in 1990..2100 }
    val enmascarados = Regex("""(?:[*xX•]{2,}[\s-]*|terminad[ao]\s+en\s+)(\d{4})(?!\d)""", RegexOption.IGNORE_CASE)
        .findAll(texto).map { it.groupValues[1] }
    return (delArchivo + enmascarados).distinct().toList()
}

/**
 * **Las palabras del nombre de un archivo**, partidas también donde cambia de minúscula a mayúscula:
 * «CuentaNu_CRS119_2026-09.pdf» → «Cuenta Nu CRS119 2026 09». Sin la extensión. Es lo que se le pasa a
 * [marcaDelBanco] (que ya parte en lo que no es letra ni dígito) y a [productoQueNombraElArchivo].
 */
fun palabrasDelNombreDelArchivo(nombreDeArchivo: String): String {
    val base = nombreDeArchivo.substringBeforeLast('.')
    return buildString(base.length + 8) {
        base.forEachIndexed { i, c ->
            val antes = base.getOrNull(i - 1)
            if (antes != null && antes.isLowerCase() && c.isUpperCase()) append(' ')
            append(if (c.isLetterOrDigit()) c else ' ')
        }
    }.trim()
}

private val PALABRAS_DE_TARJETA = setOf("tarjeta", "tarjetas", "tc", "tdc", "visa", "mastercard", "master", "amex")
private val PALABRAS_DE_CREDITO = setOf("credito", "creditos", "prestamo", "hipotecario", "hipoteca", "libranza", "leasing")
private val PALABRAS_DE_CUENTA = setOf("cuenta", "cuentas", "ahorros", "ahorro", "corriente", "cta", "afc")

/**
 * **El producto que nombra el archivo**, o `null` si no nombra ninguno o nombra dos que se
 * contradicen («Tarjeta_Cuenta.pdf»). «Tarjeta de crédito» es una tarjeta, no un crédito.
 */
fun productoQueNombraElArchivo(nombreDeArchivo: String): ProductoDelPapel? {
    val palabras = palabrasDeMarca(palabrasDelNombreDelArchivo(nombreDeArchivo)).toSet()
    val tarjeta = palabras.any { it in PALABRAS_DE_TARJETA }
    val cuenta = palabras.any { it in PALABRAS_DE_CUENTA }
    val credito = !tarjeta && palabras.any { it in PALABRAS_DE_CREDITO }
    val nombrados = listOfNotNull(
        ProductoDelPapel.TARJETA.takeIf { tarjeta },
        ProductoDelPapel.CUENTA.takeIf { cuenta },
        ProductoDelPapel.CREDITO.takeIf { credito },
    )
    return nombrados.singleOrNull()
}

/** ¿[cuenta] es del [producto]? Una cuenta es de ahorros, corriente o un fondo; nunca efectivo. */
fun esDelProducto(cuenta: Account, producto: ProductoDelPapel): Boolean = when (producto) {
    ProductoDelPapel.TARJETA -> cuenta.type == AccountType.CREDIT_CARD
    ProductoDelPapel.CREDITO -> cuenta.type == AccountType.LOAN
    ProductoDelPapel.CUENTA -> cuenta.type == AccountType.SAVINGS || cuenta.type == AccountType.CHECKING ||
        cuenta.type == AccountType.INVESTMENT
}
