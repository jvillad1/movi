package com.jvillada.movi.server.sms

import com.jvillada.movi.server.routes.montoYMonedaDelTexto
import com.jvillada.movi.shared.model.TransactionType
import com.jvillada.movi.shared.model.normalizarParaBuscar

/**
 * # Vuelta 99 · el SMS de Davivienda
 *
 * El papá del dueño recibe sus movimientos de Davivienda por SMS (desde un código corto 871…), y Movi
 * no los veía: el filtro del teléfono solo dejaba pasar Bancolombia, y aunque pasara, `parseSms` no
 * conocía su forma. Tres reales, transcritos de su teléfono (oct-2026):
 *
 * - `DAVIVIENDA Descuento en internet, $64,900,Cta de Ahorros *0136,Hora 16:49,Lugar PSE MERCADOPAGO COLOMBIA.`
 * - `DAVIVIENDA Abono Transferencia ACH, $419,745,Cta de Ahorros *0136,Hora 17:18,Lugar 0000000000000000000000000.`
 * - `DAVIVIENDA . Retiro . $320,000,Cta de Ahorros *0136,Hora 12:04,Lugar OVIEDO.`
 *
 * La forma es fija: «DAVIVIENDA» + qué pasó + monto + producto con sus últimos dígitos + «Hora HH:MM» +
 * «Lugar …». Ninguna de las frases que `parseSms` y `queEsEsteMensaje` conocían aparece: «Descuento»
 * hasta hacía que se apartara como **promoción** un gasto real de $64.900. Por eso la forma se
 * reconoce entera ([leerElAvisoDeDavivienda]) y gana antes que las palabras sueltas.
 *
 * El monto lleva coma de miles («$64,900») y sale de [montoYMonedaDelTexto], el mismo de todos los
 * bancos: una sola coma seguida de tres dígitos es de miles ([com.jvillada.movi.server.routes.montoDelSms]).
 */
data class AvisoDeDavivienda(
    /** Qué pasó, como lo escribe el banco: «Descuento en internet», «Abono Transferencia ACH», «Retiro». */
    val descripcion: String,
    val monto: Double,
    val moneda: String,
    /** «Cta de Ahorros», «Tarjeta de Credito»… lo que va antes del número. */
    val producto: String,
    /** Los dígitos que el banco deja ver del producto («0136»). La cuenta la elige el cliente por ellos. */
    val numero: String,
    /** «16:49»: la hora que dice el texto. */
    val hora: String,
    /** El «Lugar» limpio, o `null` si no dice nada (vacío o una corrida de ceros). */
    val lugar: String?,
    val tipo: TransactionType,
)

/** «DAVIVIENDA …», «Davivienda: …» al principio del texto. */
private val arranqueDeDavivienda = Regex("""^\s*davivienda\b[\s.:]*""", RegexOption.IGNORE_CASE)

/** Lo que va entre el arranque y el monto: «Descuento en internet, », « . Retiro . ». */
private val antesDelMonto = Regex("""^(.*?)[\s.,:]*(?:\$|\bCOP\b|\bUSD\b)""", RegexOption.IGNORE_CASE)

/** «,Cta de Ahorros *0136,»: el producto y sus dígitos visibles. */
private val productoYNumero = Regex(""",\s*([^,*]*?)\s*\*+\s*(\d{3,})\s*(?:,|$)""")

/** «,Hora 16:49» — la marca que separa un movimiento de Davivienda de cualquier otro SMS suyo. */
private val horaDelAviso = Regex(""",\s*hora\s+(\d{1,2}:\d{2})\b""", RegexOption.IGNORE_CASE)

/** «,Lugar PSE MERCADOPAGO COLOMBIA.» hasta el final. */
private val lugarDelAviso = Regex(""",\s*lugar\s*(.*?)\s*\.?\s*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

/**
 * Lo que es plata que **entra** a la cuenta. «Abono Transferencia ACH» es una transferencia que llegó.
 * Todo lo demás (descuento, compra, retiro, pago, transferencia que sale) es un gasto: ante la duda,
 * un gasto, como en el resto de `parseSms`.
 */
private val entraALaCuenta = Regex("""^(?:abono|consignacion|deposito|recibiste|recepcion|devolucion|reverso|reintegro|nomina)\b""")

/**
 * **El movimiento de un SMS de Davivienda**, o `null` si el texto no tiene esa forma (empieza con
 * «DAVIVIENDA», trae un monto, el producto con sus dígitos y «Hora HH:MM»). Una clave dinámica, una
 * promoción o un aviso sin monto no la tienen.
 */
fun leerElAvisoDeDavivienda(texto: String): AvisoDeDavivienda? {
    val arranque = arranqueDeDavivienda.find(texto) ?: return null
    val resto = texto.substring(arranque.range.last + 1)
    val hora = horaDelAviso.find(resto)?.groupValues?.get(1) ?: return null
    val (monto, moneda) = montoYMonedaDelTexto(resto) ?: return null
    val producto = productoYNumero.find(resto) ?: return null
    val descripcion = antesDelMonto.find(resto)?.groupValues?.get(1)
        ?.trim(' ', '.', ',', ':')?.replace(Regex("""\s+"""), " ")
        ?.takeIf { it.isNotEmpty() } ?: return null
    val lugar = lugarDelAviso.find(resto)?.groupValues?.get(1)?.trim()?.trimEnd('.')?.trim()
        ?.takeIf { l -> l.isNotEmpty() && !l.all { it.isDigit() } }
    val tipo = if (entraALaCuenta.containsMatchIn(normalizarParaBuscar(descripcion))) TransactionType.INCOME else TransactionType.EXPENSE
    return AvisoDeDavivienda(
        descripcion = descripcion,
        monto = monto,
        moneda = moneda,
        producto = producto.groupValues[1].trim(),
        numero = producto.groupValues[2],
        hora = hora,
        lugar = lugar,
        tipo = tipo,
    )
}

/** ¿Es un movimiento de Davivienda? Lo usa `queEsEsteMensaje`: con esta forma, nunca se aparta. */
fun tieneLaFormaDeDavivienda(texto: String): Boolean = leerElAvisoDeDavivienda(texto) != null

/**
 * Las pasarelas que el «Lugar» nombra pegadas y en mayúsculas: «PSE MERCADOPAGO COLOMBIA» es un pago
 * por PSE a Mercado Pago. Se nombran como se leen. Lo que no está acá queda como lo escribió el banco.
 */
private val PASARELAS: Map<String, String> = mapOf(
    "mercadopago" to "Mercado Pago",
    "mercado pago" to "Mercado Pago",
    "payu" to "PayU",
    "wompi" to "Wompi",
    "epayco" to "ePayco",
)

/** «PSE », al principio del lugar: por dónde se pagó, no a quién. */
private val prefijoPse = Regex("""^pse\s+""", RegexOption.IGNORE_CASE)

/** « COLOMBIA», « S.A.S.», « LTDA» al final: la razón social, no el nombre. */
private val colaDeRazonSocial = Regex("""(?:\s+(?:colombia|s\.?\s?a\.?\s?s\.?|s\.?\s?a\.?|ltda\.?))+\s*$""", RegexOption.IGNORE_CASE)

/**
 * **El nombre que se propone para el movimiento.**
 *
 * - Un **retiro** es en un cajero, y el «Lugar» es el cajero: «Retiro en cajero OVIEDO» (la huella
 *   `cajero:oviedo` de `MemoriaDeCategorias`, igual que un retiro de Bancolombia nombra su cajero).
 * - Un **pago por PSE** nombra la pasarela o la empresa: «PSE MERCADOPAGO COLOMBIA» → «Mercado Pago».
 * - Un lugar que no dice nada (vacío o una corrida de ceros, como en las transferencias ACH): una
 *   entrada es «Transferencia recibida»; una salida, lo que pasó («Descuento en internet»).
 */
fun comercioDelAvisoDeDavivienda(aviso: AvisoDeDavivienda): String {
    val desc = normalizarParaBuscar(aviso.descripcion)
    if (desc.startsWith("retiro")) return aviso.lugar?.let { "Retiro en cajero $it" } ?: "Retiro en cajero"
    val lugar = aviso.lugar?.let { l ->
        val sinPse = prefijoPse.replace(l, "")
        val nombre = colaDeRazonSocial.replace(sinPse, "").trim().ifEmpty { sinPse.trim() }
        PASARELAS[normalizarParaBuscar(nombre)] ?: nombre
    }
    return lugar ?: if (aviso.tipo == TransactionType.INCOME) "Transferencia recibida" else aviso.descripcion
}
